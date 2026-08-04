package com.cheminsdhistoire.app.map

import com.cheminsdhistoire.app.R

/** Choisit une icône « style jeu vidéo médiéval » selon le type de lieu (d'après son titre). */
object MonumentIcons {

    fun iconFor(title: String): Int {
        val t = title.lowercase()
        return when {
            listOf("château", "chateau", "forteresse", "citadelle", "palais", "fort ")
                .any { t.contains(it) } -> R.drawable.mon_castle

            listOf("cathédrale", "cathedrale", "église", "eglise", "abbaye", "basilique",
                "chapelle", "prieuré", "prieure", "couvent", "monastère", "monastere")
                .any { t.contains(it) } -> R.drawable.mon_church

            listOf("tour", "donjon", "beffroi", "phare").any { t.contains(it) } -> R.drawable.mon_tower

            listOf("statue", "monument", "mémorial", "memorial", "fontaine", "obélisque",
                "obelisque", "colonne").any { t.contains(it) } -> R.drawable.mon_statue

            listOf("ruine", "vestige", "site archéologique", "site archeologique", "dolmen",
                "menhir", "aqueduc", "amphithéâtre", "amphitheatre", "arènes", "arenes")
                .any { t.contains(it) } -> R.drawable.mon_ruin

            else -> R.drawable.mon_default
        }
    }
}
