package com.cheminsdhistoire.app.map

import com.cheminsdhistoire.app.R
import com.cheminsdhistoire.app.model.HistoryPlace
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.cos

/**
 * Génère de petites « maquettes » 3D stylisées par monument : des volumes extrudés
 * (boîtes) composés selon le type (château = base + tours, église = nef + flèche…).
 * Chaque volume devient un polygone GeoJSON avec height/base/color, extrudé par MapLibre.
 */
object Monument3D {

    private const val STONE = "#C9B79C"
    private const val STONE_DARK = "#A88E68"
    private const val ROOF = "#7A4A2B"
    private const val GOLD = "#D9A441"
    private const val SILVER = "#B9C0C7"

    /** Un volume : décalage (m) depuis le centre, demi-largeur/profondeur (m), base+hauteur (m). */
    private data class Box(
        val ox: Double, val oy: Double,
        val hw: Double, val hd: Double,
        val base: Double, val height: Double,
        val color: String
    )

    fun build(places: List<HistoryPlace>): List<Feature> {
        val out = ArrayList<Feature>()
        places.forEach { p ->
            boxesFor(p.title).forEach { b -> out.add(boxFeature(p, b)) }
        }
        return out
    }

    private fun boxesFor(title: String): List<Box> = when (MonumentIcons.iconFor(title)) {
        R.drawable.mon_castle -> listOf(
            Box(0.0, 0.0, 9.0, 9.0, 0.0, 11.0, STONE),
            Box(-7.0, -7.0, 2.5, 2.5, 0.0, 19.0, STONE_DARK),
            Box(7.0, -7.0, 2.5, 2.5, 0.0, 19.0, STONE_DARK),
            Box(-7.0, 7.0, 2.5, 2.5, 0.0, 19.0, STONE_DARK),
            Box(7.0, 7.0, 2.5, 2.5, 0.0, 19.0, STONE_DARK)
        )
        R.drawable.mon_church -> listOf(
            Box(0.0, 0.0, 5.0, 9.0, 0.0, 11.0, STONE),
            Box(0.0, -7.0, 2.6, 2.6, 0.0, 26.0, STONE_DARK),
            Box(0.0, -7.0, 1.6, 1.6, 26.0, 31.0, GOLD)
        )
        R.drawable.mon_tower -> listOf(
            Box(0.0, 0.0, 4.0, 4.0, 0.0, 24.0, STONE_DARK)
        )
        R.drawable.mon_statue -> listOf(
            Box(0.0, 0.0, 3.0, 3.0, 0.0, 4.0, STONE),
            Box(0.0, 0.0, 1.2, 1.2, 4.0, 13.0, SILVER)
        )
        R.drawable.mon_ruin -> listOf(
            Box(0.0, 0.0, 7.0, 5.0, 0.0, 5.0, STONE_DARK),
            Box(4.0, 2.0, 2.0, 2.0, 0.0, 9.0, STONE)
        )
        R.drawable.mon_bridge -> listOf(
            Box(0.0, 0.0, 12.0, 3.0, 0.0, 4.0, STONE)
        )
        R.drawable.mon_lighthouse -> listOf(
            Box(0.0, 0.0, 2.5, 2.5, 0.0, 20.0, STONE),
            Box(0.0, 0.0, 1.8, 1.8, 20.0, 24.0, GOLD)
        )
        R.drawable.mon_windmill -> listOf(
            Box(0.0, 0.0, 4.0, 4.0, 0.0, 12.0, STONE),
            Box(0.0, 0.0, 3.0, 3.0, 12.0, 15.0, ROOF)
        )
        R.drawable.mon_arena -> listOf(
            Box(0.0, 0.0, 12.0, 9.0, 0.0, 8.0, STONE)
        )
        R.drawable.mon_megalith -> listOf(
            Box(-4.0, 0.0, 1.8, 1.8, 0.0, 10.0, STONE_DARK),
            Box(4.0, 0.0, 1.8, 1.8, 0.0, 10.0, STONE_DARK),
            Box(0.0, 0.0, 6.0, 2.5, 10.0, 12.0, STONE)
        )
        else -> listOf(
            Box(0.0, 0.0, 3.0, 3.0, 0.0, 8.0, GOLD)
        )
    }

    private fun boxFeature(p: HistoryPlace, b: Box): Feature {
        val cosLat = cos(Math.toRadians(p.lat)).coerceAtLeast(0.01)
        fun toPoint(mx: Double, my: Double): Point {
            val lat = p.lat + my / 111_320.0
            val lon = p.lon + mx / (111_320.0 * cosLat)
            return Point.fromLngLat(lon, lat)
        }
        val ring = listOf(
            toPoint(b.ox - b.hw, b.oy - b.hd),
            toPoint(b.ox + b.hw, b.oy - b.hd),
            toPoint(b.ox + b.hw, b.oy + b.hd),
            toPoint(b.ox - b.hw, b.oy + b.hd),
            toPoint(b.ox - b.hw, b.oy - b.hd)
        )
        val poly = Polygon.fromLngLats(listOf(ring))
        return Feature.fromGeometry(poly).apply {
            addNumberProperty("height", b.height)
            addNumberProperty("base", b.base)
            addStringProperty("color", b.color)
            addStringProperty("title", p.title)
        }
    }
}
