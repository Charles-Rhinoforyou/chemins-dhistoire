package com.cheminsdhistoire.app.playback

import android.content.Context
import android.util.Log
import com.cheminsdhistoire.app.audio.GeminiVoice
import com.cheminsdhistoire.app.audio.SpeechManager
import com.cheminsdhistoire.app.audio.VoiceEngine
import com.cheminsdhistoire.app.data.JourneyStore
import com.cheminsdhistoire.app.data.PlacesRepository
import com.cheminsdhistoire.app.data.SettingsStore
import com.cheminsdhistoire.app.data.WikipediaService
import com.cheminsdhistoire.app.narration.GeminiNarrator
import com.cheminsdhistoire.app.location.LocationProvider
import com.cheminsdhistoire.app.map.Era
import com.cheminsdhistoire.app.map.EraClassifier
import com.cheminsdhistoire.app.map.Theme
import com.cheminsdhistoire.app.map.ThemeClassifier
import com.cheminsdhistoire.app.model.GeoPoint
import com.cheminsdhistoire.app.model.HistoryPlace
import com.cheminsdhistoire.app.model.JourneyEntry
import com.cheminsdhistoire.app.model.NarratedStory
import com.cheminsdhistoire.app.model.PlaybackState
import com.cheminsdhistoire.app.model.PlayerUiState
import com.cheminsdhistoire.app.narration.LlmNarrator
import com.cheminsdhistoire.app.narration.NarrationEngine
import com.cheminsdhistoire.app.narration.NarrationUtils
import com.cheminsdhistoire.app.narration.TemplateNarrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

/**
 * Le « cerveau » de l'application. Processus unique, partagé entre le service de
 * premier plan et l'interface.
 *
 * Règles clés :
 *  - la position GPS réelle pilote tout et recalcule la file en continu ;
 *  - un récit en cours n'est JAMAIS coupé : on ne réordonne que la file à venir ;
 *  - le contenu reste dans le contexte du lieu (source Wikipedia géolocalisée).
 */
object PlaybackController {

    private const val TAG = "PlaybackController"
    private const val REFRESH_DISTANCE_M = 2_500.0
    private const val MIN_UNSEEN_QUEUE = 3
    private const val SEARCH_RADIUS_M = 10_000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var wiki: WikipediaService
    private lateinit var places: PlacesRepository
    private lateinit var store: JourneyStore
    private lateinit var location: LocationProvider
    private lateinit var speech: SpeechManager
    private lateinit var geminiVoice: GeminiVoice
    private lateinit var voice: VoiceEngine
    private lateinit var templateNarrator: TemplateNarrator
    private lateinit var llmNarrator: LlmNarrator
    private lateinit var geminiNarrator: GeminiNarrator
    private lateinit var settings: SettingsStore
    private var engine: NarrationEngine? = null

    private val seen = LinkedHashSet<Long>()
    private var lastFetchPoint: GeoPoint? = null
    @Volatile private var fetching = false
    @Volatile private var busy = false
    private var initialized = false

    // Préchargement du prochain récit (texte + voix) pour enchaîner sans silence.
    @Volatile private var preloadedStory: NarratedStory? = null
    @Volatile private var preloadedPageId: Long = 0L

    private var locationJob: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _saved = MutableStateFlow<List<JourneyEntry>>(emptyList())
    val saved: StateFlow<List<JourneyEntry>> = _saved.asStateFlow()

    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        wiki = WikipediaService()
        places = PlacesRepository(wiki)
        store = JourneyStore(app)
        location = LocationProvider(app)
        settings = SettingsStore(app)
        templateNarrator = TemplateNarrator()
        llmNarrator = LlmNarrator(app)
        geminiNarrator = GeminiNarrator(settings, onProgress = ::onLoadProgress)
        engine = templateNarrator
        speech = SpeechManager(
            context = app,
            onReady = { ok -> if (!ok) setMessage("Synthèse vocale indisponible sur cet appareil.") },
            onSegment = { idx ->
                _state.update { s ->
                    s.copy(
                        currentSegmentIndex = idx,
                        playbackState = if (s.playbackState == PlaybackState.GENERATING)
                            PlaybackState.SPEAKING else s.playbackState,
                        loadingProgress = null,
                        message = null
                    )
                }
            },
            onFinished = { onStoryFinished() }
        )
        geminiVoice = GeminiVoice(
            settings = settings,
            fallback = speech,
            onSegment = { idx ->
                _state.update { s ->
                    s.copy(
                        currentSegmentIndex = idx,
                        playbackState = if (s.playbackState == PlaybackState.GENERATING)
                            PlaybackState.SPEAKING else s.playbackState,
                        loadingProgress = null,
                        message = null
                    )
                }
            },
            onFinished = { onStoryFinished() },
            onProgress = ::onLoadProgress
        )
        voice = speech
        initialized = true
        applyEngineChoice()
        applyVoiceChoice()
        scope.launch { _saved.value = store.load() }
    }

    /** Choisit le moteur de narration selon les réglages (Gemini > IA locale > local). */
    private fun applyEngineChoice() {
        engine = when {
            settings.useGemini && settings.geminiKey.isNotBlank() -> geminiNarrator
            settings.useLlm && llmNarrator.isModelPresent() -> llmNarrator
            else -> templateNarrator
        }
        _state.update { it.copy(narratorMode = engine?.label ?: "Narrateur local") }
    }

    fun setUseLlm(useLlm: Boolean) {
        settings.useLlm = useLlm
        applyEngineChoice()
    }

    fun setUseGemini(useGemini: Boolean) {
        settings.useGemini = useGemini
        applyEngineChoice()
    }

    fun setGeminiKey(key: String) {
        settings.geminiKey = key
        // Dès qu'une clé est enregistrée : Gemini par défaut pour le récit ET la voix.
        if (key.isNotBlank()) {
            settings.useGemini = true
            settings.useGeminiVoice = true
        }
        applyEngineChoice()
        applyVoiceChoice()
    }

    fun hasGeminiKey(): Boolean = settings.geminiKey.isNotBlank()

    private fun applyVoiceChoice() {
        voice = if (settings.useGeminiVoice && settings.geminiKey.isNotBlank()) geminiVoice else speech
    }

    /** Bascule voix neurale Gemini / voix du téléphone (reprend le récit en cours). */
    fun setUseGeminiVoice(useVoice: Boolean) {
        settings.useGeminiVoice = useVoice
        val wasSpeaking = _state.value.playbackState == PlaybackState.SPEAKING
        val story = _state.value.currentStory
        val idx = _state.value.currentSegmentIndex
        voice.stop()
        applyVoiceChoice()
        if (wasSpeaking && story != null) voice.speakStory(story, idx)
    }

    fun start() {
        if (!initialized) return
        if (locationJob?.isActive == true) return
        _state.update { it.copy(playbackState = PlaybackState.SEARCHING, message = "Recherche de votre position…") }
        locationJob = scope.launch {
            location.locationUpdates().collect { loc ->
                if (loc.hasBearing() && loc.speed > 0.5f) {
                    _state.update { it.copy(heading = loc.bearing) }
                }
                onLocation(GeoPoint(loc.latitude, loc.longitude))
            }
        }
    }

    fun stopEngine() {
        locationJob?.cancel()
        locationJob = null
        voice.stop()
        _state.update { it.copy(playbackState = PlaybackState.IDLE) }
    }

    private fun onLocation(point: GeoPoint) {
        // 1. Réordonne la file selon la nouvelle position (sans toucher au récit en cours).
        val requeued = _state.value.queue
            .map { it.copy(distanceMeters = LocationProvider.distanceMeters(point.lat, point.lon, it.lat, it.lon)) }
            .sortedBy { it.distanceMeters }
        _state.update { it.copy(location = point, queue = requeued) }

        // 2. Faut-il rafraîchir les lieux proches ?
        val moved = lastFetchPoint?.let {
            LocationProvider.distanceMeters(it.lat, it.lon, point.lat, point.lon)
        } ?: Double.MAX_VALUE
        val unseenCount = requeued.count { it.pageId !in seen }
        if (!fetching && (moved > REFRESH_DISTANCE_M || unseenCount < MIN_UNSEEN_QUEUE)) {
            fetchNearby(point)
        }

        // 3. Si rien n'est en cours et lecture auto activée, on lance le prochain récit.
        maybePlayNext()
    }

    private fun fetchNearby(point: GeoPoint) {
        fetching = true
        scope.launch {
            try {
                val found = places.nearby(point.lat, point.lon, SEARCH_RADIUS_M, 20)
                lastFetchPoint = point
                val existing = _state.value.queue.associateBy { it.pageId }.toMutableMap()
                found.forEach { p ->
                    if (p.pageId !in seen) existing[p.pageId] = p
                }
                val merged = existing.values.sortedBy { it.distanceMeters }
                _state.update { it.copy(queue = merged) }
                if (merged.isEmpty() && _state.value.currentStory == null) {
                    setMessage("Aucun lieu historique référencé à proximité pour l'instant.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Échec de la recherche Wikipedia : ${e.message}")
            } finally {
                fetching = false
                maybePlayNext()
            }
        }
    }

    private fun maybePlayNext() {
        if (busy) return
        val s = _state.value
        if (!s.autoContinue) return
        if (s.playbackState == PlaybackState.SPEAKING || s.playbackState == PlaybackState.PAUSED ||
            s.playbackState == PlaybackState.GENERATING
        ) return
        val next = s.queue.firstOrNull {
            it.pageId !in seen && EraClassifier.matches(it, s.eraFilter) &&
                ThemeClassifier.matches(it, s.themeFilter)
        } ?: return
        playPlace(next)
    }

    private fun playPlace(place: HistoryPlace) {
        if (busy) return
        busy = true
        seen.add(place.pageId)
        _state.update {
            it.copy(
                playbackState = PlaybackState.GENERATING,
                message = "Préparation : ${place.title}…",
                loadingProgress = null,
                queue = it.queue.filterNot { q -> q.pageId == place.pageId }
            )
        }
        scope.launch {
            try {
                // Récit déjà préchargé ? On l'utilise directement (pas de délai).
                val story = if (preloadedStory != null && preloadedPageId == place.pageId) {
                    preloadedStory!!
                } else {
                    (engine ?: templateNarrator).narrate(place)
                }
                preloadedStory = null
                preloadedPageId = 0L
                // On reste en GENERATING : le passage en SPEAKING se fait quand l'audio démarre.
                _state.update { it.copy(currentStory = story, currentSegmentIndex = 0) }
                voice.speakStory(story)
                preloadNext()
            } catch (e: Exception) {
                Log.w(TAG, "Échec de narration : ${e.message}")
                _state.update { it.copy(playbackState = PlaybackState.IDLE, loadingProgress = null) }
            } finally {
                busy = false
            }
        }
    }

    /** Prépare à l'avance le prochain récit (texte + voix) pendant la lecture en cours. */
    private fun preloadNext() {
        val s = _state.value
        val next = s.queue.firstOrNull {
            it.pageId !in seen && EraClassifier.matches(it, s.eraFilter) &&
                ThemeClassifier.matches(it, s.themeFilter)
        } ?: return
        if (preloadedPageId == next.pageId && preloadedStory != null) return
        scope.launch {
            runCatching {
                val story = (engine ?: templateNarrator).narrate(next)
                voice.prepare(story)
                preloadedStory = story
                preloadedPageId = next.pageId
            }
        }
    }

    /** Progression de chargement (récit/voix). Ignorée pendant la lecture (préchargement en fond). */
    private fun onLoadProgress(label: String, fraction: Float) {
        if (_state.value.playbackState == PlaybackState.SPEAKING) return
        _state.update { it.copy(message = "$label…", loadingProgress = fraction.coerceIn(0f, 1f)) }
    }

    private fun onStoryFinished() {
        // Appelé depuis un thread de la synthèse vocale → on revient sur le thread principal.
        scope.launch {
            _state.update { it.copy(playbackState = PlaybackState.IDLE) }
            maybePlayNext()
        }
    }

    // --- Commandes de l'interface ---

    fun pause() {
        if (_state.value.playbackState == PlaybackState.SPEAKING) {
            voice.stop()
            _state.update { it.copy(playbackState = PlaybackState.PAUSED) }
        }
    }

    fun resume() {
        val s = _state.value
        if (s.playbackState == PlaybackState.PAUSED && s.currentStory != null) {
            _state.update { it.copy(playbackState = PlaybackState.SPEAKING) }
            voice.speakStory(s.currentStory, s.currentSegmentIndex)
        } else {
            start()
            maybePlayNext()
        }
    }

    fun skip() {
        voice.stop()
        _state.update { it.copy(playbackState = PlaybackState.IDLE) }
        maybePlayNext()
    }

    /** Lance immédiatement le récit d'un lieu choisi (ex. depuis la carte). */
    fun playNow(place: HistoryPlace) {
        voice.stop()
        busy = false
        playPlace(place)
    }

    fun toggleAuto() {
        _state.update { it.copy(autoContinue = !it.autoContinue) }
        if (_state.value.autoContinue) maybePlayNext()
    }

    /** Filtre par époque : n'affecte que les prochains récits (le récit en cours continue). */
    fun setEraFilter(era: Era) {
        _state.update { it.copy(eraFilter = era) }
        maybePlayNext()
    }

    /** Filtre par thématiques (ensemble ; vide = tous). N'affecte que les prochains récits. */
    fun setThemeFilter(themes: Set<Theme>) {
        _state.update { it.copy(themeFilter = themes) }
        maybePlayNext()
    }

    fun saveCurrent() {
        val story = _state.value.currentStory ?: return
        scope.launch {
            _saved.value = store.add(
                JourneyEntry(
                    title = story.title,
                    script = story.script,
                    imageUrl = story.imageUrl,
                    sourceUrl = story.sourceUrl,
                    lat = story.place.lat,
                    lon = story.place.lon
                )
            )
            setMessage("Récit sauvegardé : ${story.title}")
        }
    }

    fun deleteSaved(entry: JourneyEntry) {
        scope.launch { _saved.value = store.remove(entry) }
    }

    /** Rejoue un récit sauvegardé (met en pause la lecture automatique). */
    fun replaySaved(entry: JourneyEntry) {
        _state.update { it.copy(autoContinue = false, playbackState = PlaybackState.SPEAKING) }
        val place = HistoryPlace(
            pageId = -1, title = entry.title, lat = entry.lat, lon = entry.lon,
            extract = entry.script, thumbnailUrl = entry.imageUrl,
            pageUrl = entry.sourceUrl, distanceMeters = 0.0
        )
        val story = NarratedStory(
            place = place, title = entry.title, script = entry.script,
            segments = NarrationUtils.toSegments(entry.script),
            imageUrl = entry.imageUrl, sourceUrl = entry.sourceUrl
        )
        _state.update { it.copy(currentStory = story, currentSegmentIndex = 0) }
        voice.speakStory(story)
    }

    private fun setMessage(msg: String) {
        _state.update { it.copy(message = msg) }
    }

    fun shutdown() {
        locationJob?.cancel()
        speech.shutdown()
        geminiVoice.shutdown()
        initialized = false
    }
}
