package com.cheminsdhistoire.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.cheminsdhistoire.app.data.SettingsStore
import com.cheminsdhistoire.app.map.Era
import com.cheminsdhistoire.app.model.JourneyEntry
import com.cheminsdhistoire.app.model.PlaybackState
import com.cheminsdhistoire.app.model.PlayerUiState
import com.cheminsdhistoire.app.narration.NarrationUtils
import com.cheminsdhistoire.app.playback.PlaybackController

private val AppColors = darkColorScheme(
    primary = Color(0xFFD9A441),
    onPrimary = Color(0xFF2A1D08),
    secondary = Color(0xFFC8A15A),
    background = Color(0xFF1A1712),
    onBackground = Color(0xFFF2E9D8),
    surface = Color(0xFF262019),
    onSurface = Color(0xFFF2E9D8),
    surfaceVariant = Color(0xFF332A20),
    onSurfaceVariant = Color(0xFFCFC1A8)
)

@Composable
fun CheminsApp(onRequestStart: () -> Unit, onEnterFloating: () -> Unit = {}) {
    MaterialTheme(colorScheme = AppColors) {
        val ui by PlaybackController.state.collectAsStateWithLifecycle()
        val saved by PlaybackController.saved.collectAsStateWithLifecycle()
        var tab by remember { mutableIntStateOf(0) }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Filled.Headphones, contentDescription = null) },
                        label = { Text("Écoute") }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Filled.Map, contentDescription = null) },
                        label = { Text("Carte") }
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Filled.ViewInAr, contentDescription = null) },
                        label = { Text("3D") }
                    )
                    NavigationBarItem(
                        selected = tab == 3,
                        onClick = { tab = 3 },
                        icon = { Icon(Icons.Filled.Bookmark, contentDescription = null) },
                        label = { Text("Mes récits") }
                    )
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> PlayerScreen(ui, onRequestStart, onEnterFloating)
                    1 -> MapScreen(ui)
                    2 -> Map3DScreen(ui)
                    else -> SavedScreen(saved)
                }
            }
        }
    }
}

@Composable
private fun PlayerScreen(
    ui: PlayerUiState,
    onRequestStart: () -> Unit,
    onEnterFloating: () -> Unit
) {
    val scroll = rememberScrollState()
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Chemins d'Histoire",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Le podcast d'Histoire qui suit vos pas, en temps réel.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        EraFilterRow(ui.eraFilter)

        StoryCard(ui)

        ControlsRow(ui, onRequestStart)

        OutlinedButton(
            onClick = onEnterFloating,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.PictureInPictureAlt, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Réduire en fenêtre flottante")
        }
        Text(
            "Le récit continue en arrière-plan (même pendant votre GPS). Choisissez une "
                + "mini-fenêtre déplaçable, ou fermez-la pour l'audio seul.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SettingsRow(ui)

        AiSettingsCard()

        StatusBlock(ui)

        if (ui.queue.isNotEmpty()) {
            Text(
                "À venir sur votre route",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            ui.queue.take(5).forEach { p ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            p.title,
                            Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            NarrationUtils.formatDistance(p.distanceMeters),
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StoryCard(ui: PlayerUiState) {
    val story = ui.currentStory
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (story?.imageUrl != null) {
                AsyncImage(
                    model = story.imageUrl,
                    contentDescription = story.title,
                    modifier = Modifier.fillMaxWidth().height(190.dp)
                        .then(Modifier).padding(bottom = 4.dp)
                )
            }
            if (story != null) {
                Text(
                    story.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (ui.playbackState == PlaybackState.GENERATING) {
                    // Chargement du récit / de la voix, avec pourcentage.
                    val p = ui.loadingProgress
                    if (p != null) {
                        LinearProgressIndicator(progress = p, modifier = Modifier.fillMaxWidth())
                        Text(
                            "${ui.message ?: "Chargement"} ${(p * 100).toInt()} %",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            ui.message ?: "Préparation…",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else {
                    val total = story.segments.size.coerceAtLeast(1)
                    val idx = ui.currentSegmentIndex.coerceIn(0, total - 1)
                    val pct = ((idx + 1) * 100) / total
                    LinearProgressIndicator(
                        progress = (idx + 1).toFloat() / total,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        story.segments.getOrNull(idx) ?: story.script.take(140),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Segment ${idx + 1} / $total · $pct %",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ui.playbackState == PlaybackState.SEARCHING ||
                        ui.playbackState == PlaybackState.GENERATING
                    ) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.size(12.dp))
                    }
                    Text(
                        ui.message ?: "Mettez-vous en route : dès qu'un lieu chargé d'Histoire "
                            + "approche, le récit démarre tout seul.",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                val p = ui.loadingProgress
                if (p != null) {
                    LinearProgressIndicator(progress = p, modifier = Modifier.fillMaxWidth())
                    Text(
                        "${(p * 100).toInt()} %",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlsRow(ui: PlayerUiState, onRequestStart: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val speaking = ui.playbackState == PlaybackState.SPEAKING
        FilledIconButton(onClick = {
            when (ui.playbackState) {
                PlaybackState.SPEAKING -> PlaybackController.pause()
                PlaybackState.PAUSED -> PlaybackController.resume()
                else -> { onRequestStart(); PlaybackController.resume() }
            }
        }) {
            Icon(
                if (speaking) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (speaking) "Pause" else "Lecture"
            )
        }
        OutlinedButton(onClick = { PlaybackController.skip() }) {
            Icon(Icons.Filled.SkipNext, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text("Suivant")
        }
        Button(
            onClick = { PlaybackController.saveCurrent() },
            enabled = ui.currentStory != null
        ) {
            Icon(Icons.Filled.Bookmark, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text("Garder")
        }
    }
}

@Composable
private fun AiSettingsCard() {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    var key by remember { mutableStateOf(store.geminiKey) }
    var useGemini by remember { mutableStateOf(store.useGemini) }
    var useVoice by remember { mutableStateOf(store.useGeminiVoice) }
    var saved by remember { mutableStateOf(store.geminiKey.isNotBlank()) }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Réglages IA — qualité des récits",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Avec ta clé Gemini gratuite, les récits sont bien plus riches. "
                    + "La clé reste sur ton téléphone.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                    )
                }
            }) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Obtenir une clé gratuite")
            }
            OutlinedTextField(
                value = key,
                onValueChange = { key = it; saved = false },
                label = { Text("Clé API Gemini (AIza…)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    PlaybackController.setGeminiKey(key)
                    saved = true
                    if (key.isNotBlank()) {
                        useGemini = true
                        useVoice = true
                    }
                }) {
                    Text("Enregistrer")
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    if (saved && key.isNotBlank()) "Clé enregistrée ✓" else "Aucune clé",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Utiliser Gemini pour les récits", color = MaterialTheme.colorScheme.onBackground)
                Switch(
                    checked = useGemini,
                    onCheckedChange = { useGemini = it; PlaybackController.setUseGemini(it) }
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Voix neurale Gemini (plus naturelle)", color = MaterialTheme.colorScheme.onBackground)
                Switch(
                    checked = useVoice,
                    onCheckedChange = { useVoice = it; PlaybackController.setUseGeminiVoice(it) }
                )
            }
        }
    }
}

@Composable
private fun EraFilterRow(selected: Era) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Era.values().forEach { era ->
            FilterChip(
                selected = era == selected,
                onClick = { PlaybackController.setEraFilter(era) },
                label = { Text(era.label) }
            )
        }
    }
}

@Composable
private fun SettingsRow(ui: PlayerUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Lecture continue automatique", color = MaterialTheme.colorScheme.onBackground)
            Switch(checked = ui.autoContinue, onCheckedChange = { PlaybackController.toggleAuto() })
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "IA locale (si un modèle est installé)",
                color = MaterialTheme.colorScheme.onBackground
            )
            var llm by remember { mutableIntStateOf(0) }
            Switch(
                checked = llm == 1,
                onCheckedChange = {
                    llm = if (it) 1 else 0
                    PlaybackController.setUseLlm(it)
                }
            )
        }
    }
}

@Composable
private fun StatusBlock(ui: PlayerUiState) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
    val loc = ui.location
    val locText = if (loc != null)
        "GPS : ${"%.4f".format(loc.lat)}, ${"%.4f".format(loc.lon)}"
    else "GPS : en attente de position…"
    Column {
        Text(locText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "État : ${stateLabel(ui.playbackState)}  •  ${ui.narratorMode}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Chemins d'Histoire · version $version",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun stateLabel(s: PlaybackState): String = when (s) {
    PlaybackState.IDLE -> "prêt"
    PlaybackState.SEARCHING -> "recherche des lieux"
    PlaybackState.GENERATING -> "écriture du récit"
    PlaybackState.SPEAKING -> "lecture en cours"
    PlaybackState.PAUSED -> "en pause"
    PlaybackState.ERROR -> "erreur"
}

@Composable
private fun SavedScreen(saved: List<JourneyEntry>) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Mes récits sauvegardés",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        if (saved.isEmpty()) {
            Text(
                "Vous n'avez encore rien gardé. Pendant un récit, appuyez sur « Garder » "
                    + "pour le retrouver ici, même hors connexion.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        saved.forEach { entry ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        entry.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        entry.script.take(160) + if (entry.script.length > 160) "…" else "",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { PlaybackController.replaySaved(entry) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Réécouter")
                        }
                        OutlinedButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(entry.sourceUrl))
                                )
                            }
                        }) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Source")
                        }
                        OutlinedButton(onClick = { PlaybackController.deleteSaved(entry) }) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
