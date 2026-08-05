package com.cheminsdhistoire.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheminsdhistoire.app.R
import com.cheminsdhistoire.app.map.EraClassifier
import com.cheminsdhistoire.app.map.MonumentIcons
import com.cheminsdhistoire.app.map.RouteHolder
import com.cheminsdhistoire.app.model.HistoryPlace
import com.cheminsdhistoire.app.model.PlayerUiState
import com.cheminsdhistoire.app.playback.PlaybackController
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val SRC_POIS = "pois"
private const val SRC_ROUTE = "route"
private const val LAYER_POIS = "poi-symbols"
private const val LAYER_ROUTE = "route-line"

@Composable
fun Map3DScreen(ui: PlayerUiState) {
    val context = LocalContext.current
    val itinerary by RouteHolder.itinerary.collectAsStateWithLifecycle()
    val road by RouteHolder.route.collectAsStateWithLifecycle()

    val selected = remember { mutableStateOf<HistoryPlace?>(null) }
    val poiByTitle = remember { HashMap<String, HistoryPlace>() }
    val styleRef = remember { mutableStateOf<Style?>(null) }
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }

    DisposableEffect(Unit) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Initialise la carte, le style, les couches (route + monuments) et les bâtiments 3D.
    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            mapRef.value = map
            map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                registerIcons(context, style)
                style.addSource(GeoJsonSource(SRC_POIS, FeatureCollection.fromFeatures(emptyList())))
                style.addSource(GeoJsonSource(SRC_ROUTE, FeatureCollection.fromFeatures(emptyList())))

                style.addLayer(
                    LineLayer(LAYER_ROUTE, SRC_ROUTE).withProperties(
                        PropertyFactory.lineColor(AndroidColor.parseColor("#B5451B")),
                        PropertyFactory.lineWidth(6f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                    )
                )
                style.addLayer(
                    SymbolLayer(LAYER_POIS, SRC_POIS).withProperties(
                        PropertyFactory.iconImage(Expression.get("icon")),
                        PropertyFactory.iconSize(0.9f),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM)
                    )
                )
                // Bâtiments en relief (schéma OpenMapTiles d'OpenFreeMap).
                runCatching {
                    style.addLayer(
                        FillExtrusionLayer("buildings-3d", "openmaptiles").apply {
                            sourceLayer = "building"
                            minZoom = 14f
                            setProperties(
                                PropertyFactory.fillExtrusionColor(AndroidColor.parseColor("#C9B79C")),
                                PropertyFactory.fillExtrusionHeight(Expression.get("render_height")),
                                PropertyFactory.fillExtrusionBase(Expression.get("render_min_height")),
                                PropertyFactory.fillExtrusionOpacity(0.9f)
                            )
                        }
                    )
                }

                styleRef.value = style

                map.addOnMapClickListener { latLng ->
                    val pt = map.projection.toScreenLocation(latLng)
                    val feats = map.queryRenderedFeatures(pt, LAYER_POIS)
                    val title = feats.firstOrNull()?.getStringProperty("title")
                    val place = title?.let { poiByTitle[it] }
                    if (place != null) {
                        selected.value = place
                        true
                    } else {
                        false
                    }
                }

                // Caméra initiale.
                val loc = ui.location
                val start = if (loc != null) LatLng(loc.lat, loc.lon) else LatLng(46.6, 2.4)
                map.cameraPosition = CameraPosition.Builder()
                    .target(start).zoom(if (loc != null) 15.0 else 5.0).tilt(45.0).build()
            }
        }
    }

    // Met à jour les monuments (filtrés par époque).
    LaunchedEffect(ui.queue, ui.eraFilter, ui.currentStory?.title, styleRef.value, itinerary) {
        val style = styleRef.value ?: return@LaunchedEffect
        poiByTitle.clear()
        val places = LinkedHashMap<Long, HistoryPlace>()
        ui.currentStory?.place?.let { if (it.pageId >= 0) places[it.pageId] = it }
        ui.queue.forEach { places[it.pageId] = it }
        itinerary?.stops?.forEach { places[it.pageId] = it }
        val feats = places.values
            .filter { EraClassifier.matches(it, ui.eraFilter) }
            .take(40)
            .map { p ->
                poiByTitle[p.title] = p
                Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply {
                    addStringProperty("icon", MonumentIcons.iconFor(p.title).toString())
                    addStringProperty("title", p.title)
                }
            }
        (style.getSource(SRC_POIS) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(feats))
    }

    // Met à jour la trajectoire.
    LaunchedEffect(road, itinerary, styleRef.value) {
        val style = styleRef.value ?: return@LaunchedEffect
        val pts: List<Point> = when {
            road != null && road!!.points.size >= 2 ->
                road!!.points.map { Point.fromLngLat(it.lon, it.lat) }
            itinerary != null -> buildList {
                val it0 = itinerary!!
                add(Point.fromLngLat(it0.origin.lon, it0.origin.lat))
                it0.stops.forEach { add(Point.fromLngLat(it.lon, it.lat)) }
                add(Point.fromLngLat(it0.destination.lon, it0.destination.lat))
            }
            else -> emptyList()
        }
        val fc = if (pts.size >= 2) {
            FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(pts)))
        } else {
            FeatureCollection.fromFeatures(emptyList())
        }
        (style.getSource(SRC_ROUTE) as? GeoJsonSource)?.setGeoJson(fc)
    }

    // Navigation : la caméra suit la position, inclinée et orientée dans le sens de la marche.
    LaunchedEffect(ui.location, ui.heading, mapRef.value) {
        val map = mapRef.value ?: return@LaunchedEffect
        val loc = ui.location ?: return@LaunchedEffect
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(loc.lat, loc.lon))
                    .zoom(16.5)
                    .tilt(55.0)
                    .bearing((ui.heading ?: 0f).toDouble())
                    .build()
            ),
            800
        )
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Vue 3D — navigation en relief",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Bâtiments en relief, caméra inclinée. Cherchez un itinéraire dans l'onglet Carte "
                + "pour le suivre ici en 3D.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

            selected.value?.let { place ->
                Card(
                    Modifier.align(Alignment.BottomCenter).padding(8.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            place.title,
                            Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(onClick = {
                            PlaybackController.playNow(place); selected.value = null
                        }) {
                            Icon(Icons.Filled.Headphones, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Écouter")
                        }
                        IconButton(onClick = { selected.value = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Fermer")
                        }
                    }
                }
            }
        }
    }
}

private fun registerIcons(context: android.content.Context, style: Style) {
    val icons = listOf(
        R.drawable.mon_castle, R.drawable.mon_church, R.drawable.mon_tower,
        R.drawable.mon_statue, R.drawable.mon_ruin, R.drawable.mon_default,
        R.drawable.mon_bridge, R.drawable.mon_lighthouse, R.drawable.mon_windmill,
        R.drawable.mon_arena, R.drawable.mon_megalith
    )
    icons.forEach { resId ->
        drawableToBitmap(context, resId)?.let { style.addImage(resId.toString(), it) }
    }
}

private fun drawableToBitmap(context: android.content.Context, resId: Int): Bitmap? {
    val d = ContextCompat.getDrawable(context, resId) ?: return null
    val w = if (d.intrinsicWidth > 0) d.intrinsicWidth else 96
    val h = if (d.intrinsicHeight > 0) d.intrinsicHeight else 96
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    d.setBounds(0, 0, w, h)
    d.draw(canvas)
    return bmp
}
