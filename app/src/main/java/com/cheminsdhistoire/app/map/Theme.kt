package com.cheminsdhistoire.app.map

import com.cheminsdhistoire.app.model.HistoryPlace
import java.text.Normalizer

/** Thématiques de sujets pour filtrer les récits (avant et après la recherche). */
enum class Theme(val label: String, internal val keys: List<String>) {
    CHATEAUX("Châteaux", listOf(
        "chateau", "forteresse", "citadelle", "donjon", "palais", "fort ", "manoir"
    )),
    RELIGIEUX("Religieux", listOf(
        "eglise", "cathedrale", "abbaye", "basilique", "chapelle", "prieure", "monastere",
        "couvent", "sanctuaire", "cloitre", "collegiale", "catholic", "spiritual", "pelerinage",
        "saint ", "sainte ", "religieux", "reliques"
    )),
    ROMAINS("Romains & Antiquité", listOf(
        "romain", "gallo-romain", "gallo romain", "antiqu", "amphitheatre", "thermes", "aqueduc",
        "arenes", "gaulois", "antique", "empire romain", "cesar"
    )),
    TEMPLIERS("Templiers", listOf(
        "templier", "ordre du temple", "commanderie", "hospitalier", "croisade"
    )),
    MONUMENTS("Monuments", listOf(
        "monument", "statue", "memorial", "fontaine", "obelisque", "colonne", "arc de", "beffroi",
        "pont", "phare", "moulin", "tour ", "mausolee"
    )),
    PERSONNAGES("Personnages", listOf(
        "ne a ", "naissance de", "ecrivain", "poete", "peintre", "sculpteur", "roi ", "reine ",
        "empereur", "philosophe", "maison de", "demeure de", "general", "resistant"
    )),
    GASTRONOMIE("Gastronomie", listOf(
        "vin", "vignoble", "cave", "fromage", "gastronomie", "cuisine", "specialite", "terroir",
        "brasserie", "distillerie", "chocolat", "confiserie"
    )),
    MUSIQUE("Musique", listOf(
        "opera", "festival", "musique", "musicien", "compositeur", "conservatoire", "orgue",
        "concert", "philharmon"
    ));
}

object ThemeClassifier {

    fun themesOf(place: HistoryPlace): Set<Theme> {
        val t = normalize(place.title + " " + place.extract)
        val out = LinkedHashSet<Theme>()
        // Amorce par le type d'icône (structures) puis affine par mots-clés.
        when (MonumentIcons.iconFor(place.title)) {
            com.cheminsdhistoire.app.R.drawable.mon_castle -> out.add(Theme.CHATEAUX)
            com.cheminsdhistoire.app.R.drawable.mon_church -> out.add(Theme.RELIGIEUX)
            com.cheminsdhistoire.app.R.drawable.mon_arena -> out.add(Theme.ROMAINS)
            com.cheminsdhistoire.app.R.drawable.mon_statue,
            com.cheminsdhistoire.app.R.drawable.mon_tower,
            com.cheminsdhistoire.app.R.drawable.mon_bridge,
            com.cheminsdhistoire.app.R.drawable.mon_lighthouse,
            com.cheminsdhistoire.app.R.drawable.mon_windmill -> out.add(Theme.MONUMENTS)
        }
        Theme.entries.forEach { theme ->
            if (theme.keys.any { t.contains(it) }) out.add(theme)
        }
        return out
    }

    fun matches(place: HistoryPlace, selected: Set<Theme>): Boolean =
        selected.isEmpty() || themesOf(place).any { it in selected }

    private fun normalize(s: String): String {
        val noAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return noAccents.lowercase().replace(Regex("\\s+"), " ")
    }
}
