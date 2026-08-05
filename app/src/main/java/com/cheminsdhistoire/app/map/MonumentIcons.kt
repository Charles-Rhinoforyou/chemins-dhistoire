package com.cheminsdhistoire.app.map

import com.cheminsdhistoire.app.R

/** Choisit une icône « style jeu vidéo médiéval » selon le type de lieu (d'après son titre). */
object MonumentIcons {

    fun iconFor(title: String): Int {
        val t = title.lowercase()
        return when {
            // Types spécifiques d'abord.
            listOf("dolmen", "menhir", "mégalith", "megalith", "cromlech", "tumulus")
                .any { t.contains(it) } -> R.drawable.mon_megalith

            listOf("amphithéâtre", "amphitheatre", "arènes", "arenes", "colisée", "colisee",
                "théâtre antique", "theatre antique", "aqueduc", "thermes")
                .any { t.contains(it) } -> R.drawable.mon_arena

            listOf("pont", "viaduc").any { t.contains(it) } -> R.drawable.mon_bridge

            listOf("phare").any { t.contains(it) } -> R.drawable.mon_lighthouse

            listOf("moulin").any { t.contains(it) } -> R.drawable.mon_windmill

            listOf("château", "chateau", "forteresse", "citadelle", "palais", "fort ")
                .any { t.contains(it) } -> R.drawable.mon_castle

            listOf("cathédrale", "cathedrale", "église", "eglise", "abbaye", "basilique",
                "chapelle", "prieuré", "prieure", "couvent", "monastère", "monastere")
                .any { t.contains(it) } -> R.drawable.mon_church

            listOf("tour", "donjon", "beffroi").any { t.contains(it) } -> R.drawable.mon_tower

            listOf("statue", "monument", "mémorial", "memorial", "fontaine", "obélisque",
                "obelisque", "colonne").any { t.contains(it) } -> R.drawable.mon_statue

            listOf("ruine", "vestige", "site archéologique", "site archeologique", "château fort",
                "chateau fort").any { t.contains(it) } -> R.drawable.mon_ruin

            else -> R.drawable.mon_default
        }
    }
}
