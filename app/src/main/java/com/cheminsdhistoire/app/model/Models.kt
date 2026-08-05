package com.cheminsdhistoire.app.model

import com.cheminsdhistoire.app.map.Era

/** Un point géographique simple. */
data class GeoPoint(val lat: Double, val lon: Double)

/** Un lieu historique détecté autour de la position (issu de Wikipedia géolocalisée). */
data class HistoryPlace(
    val pageId: Long,
    val title: String,
    val lat: Double,
    val lon: Double,
    val extract: String,
    val thumbnailUrl: String?,
    val pageUrl: String,
    val distanceMeters: Double
)

/** Un récit prêt à être raconté, produit par un moteur de narration. */
data class NarratedStory(
    val place: HistoryPlace,
    val title: String,
    val script: String,
    val segments: List<String>,
    val imageUrl: String?,
    val sourceUrl: String,
    val generatedAt: Long = System.currentTimeMillis()
)

/** Une entrée sauvegardée dans « Mes récits ». */
data class JourneyEntry(
    val title: String,
    val script: String,
    val imageUrl: String?,
    val sourceUrl: String,
    val lat: Double,
    val lon: Double,
    val savedAt: Long = System.currentTimeMillis()
)

enum class PlaybackState { IDLE, SEARCHING, GENERATING, SPEAKING, PAUSED, ERROR }

/** État observable par l'interface. */
data class PlayerUiState(
    val playbackState: PlaybackState = PlaybackState.IDLE,
    val location: GeoPoint? = null,
    val currentStory: NarratedStory? = null,
    val currentSegmentIndex: Int = 0,
    val queue: List<HistoryPlace> = emptyList(),
    val message: String? = null,
    val narratorMode: String = "Narrateur local",
    val autoContinue: Boolean = true,
    val eraFilter: Era = Era.TOUTES
)
