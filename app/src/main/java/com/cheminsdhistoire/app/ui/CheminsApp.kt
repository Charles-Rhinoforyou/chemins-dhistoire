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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
fun CheminsApp(onRequestStart: () -> Unit) {
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
                        icon = { Icon(Icons.Filled.Bookmark, contentDescription = null) },
                        label = { Text("Mes récits") }
                    )
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> PlayerScreen(ui, onRequestStart)
                    else -> SavedScreen(saved)
                }
            }
        }
    }
}

@Composable
private fun PlayerScreen(ui: PlayerUiState, onRequestStart: () -> Unit) {
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

        StoryCard(ui)

        ControlsRow(ui, onRequestStart)

        SettingsRow(ui)

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
                val total = story.segments.size.coerceAtLeast(1)
                val idx = ui.currentSegmentIndex.coerceIn(0, total - 1)
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
                    "Segment ${idx + 1} / $total",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
