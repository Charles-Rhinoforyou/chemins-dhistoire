package com.cheminsdhistoire.app.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.cheminsdhistoire.app.data.WikipediaService
import com.cheminsdhistoire.app.map.GeocodingService
import com.cheminsdhistoire.app.map.Itinerary
import com.cheminsdhistoire.app.map.ItineraryPlanner
import com.cheminsdhistoire.app.map.MonumentIcons
import com.cheminsdhistoire.app.model.HistoryPlace
import com.cheminsdhistoire.app.model.PlayerUiState
import com.cheminsdhistoire.app.narration.NarrationUtils
import com.cheminsdhistoire.app.playback.PlaybackController
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.util.GeoPoint as OsmGeoPoint

@Composable
fun MapScreen(ui: PlayerUiState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val wiki = remember { WikipediaService() }
    val geocoder = remember { GeocodingService() }
    val planner = remember { ItineraryPlanner(wiki) }

    var destinationText by remember { mutableStateOf("") }
    var itinerary by remember { mutableStateOf<Itinerary?>(null) }
    var planning by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateOf<HistoryPlace?>(null) }
    var centered by remember { mutableStateOf(false) }

    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            // Cache des tuiles dans le dossier privé de l'app (aucune permission stockage).
            osmdroidBasePath = context.cacheDir
            osmdroidTileCache = java.io.File(context.cacheDir, "osmtiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(6.5)
            controller.setCenter(OsmGeoPoint(46.6, 2.4)) // centre de la France
            // Teinte sépia / vieux parchemin par-dessus la carte réelle.
            overlayManager.tilesOverlay.setColorFilter(
                ColorMatrixColorFilter(
                    ColorMatrix(
                        floatArrayOf(
                            0.90f, 0.10f, 0.10f, 0f, 18f,
                            0.10f, 0.85f, 0.10f, 0f, 8f,
                            0.08f, 0.08f, 0.68f, 0f, -6f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            )
        }
    }

    fun makeMarker(place: HistoryPlace, isDestination: Boolean = false): Marker {
        return Marker(mapView).apply {
            position = OsmGeoPoint(place.lat, place.lon)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(
                context,
                if (isDestination) com.cheminsdhistoire.app.R.drawable.mon_default
                else MonumentIcons.iconFor(place.title)
            )
            title = place.title
            setOnMarkerClickListener { _, _ ->
                if (place.pageId >= 0) selected.value = place
                true
            }
        }
    }

    fun rebuildOverlays() {
        mapView.overlays.clear()
        selected.value = null

        val itin = itinerary
        if (itin != null) {
            val line = Polyline().apply {
                val pts = mutableListOf(OsmGeoPoint(itin.origin.lat, itin.origin.lon))
                itin.stops.forEach { pts.add(OsmGeoPoint(it.lat, it.lon)) }
                pts.add(OsmGeoPoint(itin.destination.lat, itin.destination.lon))
                setPoints(pts)
                outlinePaint.color = 0xFFB5451B.toInt()
                outlinePaint.strokeWidth = 9f
            }
            mapView.overlays.add(line)
            itin.stops.forEach { mapView.overlays.add(makeMarker(it)) }
            mapView.overlays.add(
                makeMarker(
                    HistoryPlace(
                        -2, itin.destinationName, itin.destination.lat, itin.destination.lon,
                        "", null, "", 0.0
                    ),
                    isDestination = true
                )
            )
        } else {
            // Sans itinéraire : on montre les lieux détectés autour de soi.
            val nearby = LinkedHashMap<Long, HistoryPlace>()
            ui.currentStory?.place?.let { if (it.pageId >= 0) nearby[it.pageId] = it }
            ui.queue.forEach { nearby[it.pageId] = it }
            nearby.values.take(30).forEach { mapView.overlays.add(makeMarker(it)) }
        }
        mapView.invalidate()
    }

    // Reconstruit les marqueurs quand les données changent.
    androidx.compose.runtime.LaunchedEffect(ui.queue, itinerary, ui.currentStory?.title) {
        rebuildOverlays()
    }

    // Centre sur la position au premier fix GPS.
    androidx.compose.runtime.LaunchedEffect(ui.location) {
        val loc = ui.location
        if (loc != null && !centered && itinerary == null) {
            mapView.controller.setZoom(12.0)
            mapView.controller.setCenter(OsmGeoPoint(loc.lat, loc.lon))
            centered = true
        }
    }

    // Zoome sur l'itinéraire une fois calculé.
    androidx.compose.runtime.LaunchedEffect(itinerary) {
        val itin = itinerary ?: return@LaunchedEffect
        val pts = mutableListOf(
            OsmGeoPoint(itin.origin.lat, itin.origin.lon),
            OsmGeoPoint(itin.destination.lat, itin.destination.lon)
        )
        itin.stops.forEach { pts.add(OsmGeoPoint(it.lat, it.lon)) }
        val lats = pts.map { it.latitude }
        val lons = pts.map { it.longitude }
        val bb = BoundingBox(lats.max(), lons.max(), lats.min(), lons.min())
        mapView.post {
            runCatching { mapView.zoomToBoundingBox(bb, true, 90) }
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    fun launchPlan() {
        val origin = ui.location
        if (origin == null) {
            errorMsg = "Position GPS non disponible : lancez l'écoute d'abord (onglet Écoute)."
            return
        }
        if (destinationText.isBlank()) {
            errorMsg = "Entrez une destination."
            return
        }
        planning = true
        errorMsg = null
        scope.launch {
            val dest = geocoder.geocode(destinationText)
            if (dest == null) {
                errorMsg = "Destination introuvable."
                planning = false
                return@launch
            }
            itinerary = planner.plan(origin, dest.point, dest.name)
            if (itinerary?.stops.isNullOrEmpty()) {
                errorMsg = "Peu de lieux remarquables trouvés sur ce trajet."
            }
            planning = false
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Carte des trésors",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = destinationText,
                onValueChange = { destinationText = it },
                placeholder = { Text("Destination (ville, lieu…)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { launchPlan() }, enabled = !planning) {
                if (planning) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Route, contentDescription = null)
                }
            }
        }
        if (itinerary != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Itinéraire vers ${itinerary!!.destinationName} · ${itinerary!!.stops.size} étapes",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { itinerary = null; centered = false }) {
                    Icon(Icons.Filled.Close, contentDescription = "Effacer l'itinéraire")
                }
            }
        }
        errorMsg?.let {
            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
            )

            selected.value?.let { place ->
                SelectedPlaceCard(
                    place = place,
                    onListen = { PlaybackController.playNow(place); selected.value = null },
                    onClose = { selected.value = null },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                )
            }
        }

        itinerary?.let { itin ->
            if (itin.stops.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itin.stops.forEachIndexed { i, p ->
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${i + 1}. ${p.title}",
                                    Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(onClick = { PlaybackController.playNow(p) }) {
                                    Icon(
                                        Icons.Filled.Headphones,
                                        contentDescription = "Écouter",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedPlaceCard(
    place: HistoryPlace,
    onListen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (place.thumbnailUrl != null) {
                AsyncImage(
                    model = place.thumbnailUrl,
                    contentDescription = place.title,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.size(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    place.title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary
                )
                if (place.distanceMeters > 0) {
                    Text(
                        NarrationUtils.formatDistance(place.distanceMeters),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(onClick = onListen) {
                Icon(Icons.Filled.Headphones, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Écouter")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Fermer")
            }
        }
    }
}
