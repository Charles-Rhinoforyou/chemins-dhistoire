package com.cheminsdhistoire.app.map

import com.cheminsdhistoire.app.model.HistoryPlace

/** Grandes périodes historiques pour filtrer selon l'envie du moment. */
enum class Era(val label: String) {
    TOUTES("Toutes"),
    ANTIQUITE("Antiquité"),
    MOYEN_AGE("Moyen Âge"),
    RENAISSANCE("Renaissance"),
    MODERNE("Moderne"),
    CONTEMPORAIN("Contemporain")
}

/**
 * Devine l'époque d'un lieu à partir de son titre + extrait (heuristique).
 * Priorité aux mots-clés forts, puis repli sur le siècle détecté.
 * Renvoie [Era.TOUTES] si indéterminé (donc masqué quand un filtre précis est actif).
 */
object EraClassifier {

    fun eraOf(place: HistoryPlace): Era {
        val t = (place.title + " " + place.extract).lowercase()

        // Mots-clés forts (priorité).
        when {
            containsAny(t, "renaissance", "château de la loire", "françois ier", "francois ier") ->
                return Era.RENAISSANCE
            containsAny(
                t, "gallo-romain", "gallo romain", "romaine", "romain", "antiqu", "gaulois",
                "préhist", "prehist", "néolith", "neolith", "mégalith", "megalith", "dolmen",
                "menhir", "celte", "aqueduc", "amphithéâtre", "amphitheatre", "thermes"
            ) -> return Era.ANTIQUITE
            containsAny(
                t, "médiéval", "medieval", "moyen âge", "moyen age", "gothique", "féodal",
                "feodal", "château fort", "chateau fort", "forteresse", "donjon", "templier",
                "croisade", "roman"
            ) -> return Era.MOYEN_AGE
            containsAny(
                t, "baroque", "classique", "louis xiv", "louis xv", "grand siècle", "versailles"
            ) -> return Era.MODERNE
            containsAny(
                t, "industriel", "napoléon", "napoleon", "seconde guerre", "première guerre",
                "premiere guerre", "art déco", "art deco", "haussmann"
            ) -> return Era.CONTEMPORAIN
        }

        // Repli : siècle détecté (chiffres romains + « siècle »).
        val century = detectCentury(t)
        if (century != null) {
            return when {
                century <= 5 -> Era.ANTIQUITE
                century in 6..14 -> Era.MOYEN_AGE
                century in 15..16 -> Era.RENAISSANCE
                century in 17..18 -> Era.MODERNE
                else -> Era.CONTEMPORAIN
            }
        }
        return Era.TOUTES
    }

    fun matches(place: HistoryPlace, filter: Era): Boolean =
        filter == Era.TOUTES || eraOf(place) == filter

    private fun containsAny(text: String, vararg keys: String): Boolean =
        keys.any { text.contains(it) }

    private val centuryRegex =
        Regex("\\b([ivxlcdm]+)(?:er|ème|eme|e)?\\s*siècle", RegexOption.IGNORE_CASE)

    private fun detectCentury(text: String): Int? {
        val m = centuryRegex.find(text) ?: return null
        return romanToInt(m.groupValues[1].uppercase()).takeIf { it in 1..21 }
    }

    private fun romanToInt(s: String): Int {
        val values = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
        var total = 0
        var prev = 0
        for (c in s.reversed()) {
            val v = values[c] ?: return 0
            if (v < prev) total -= v else total += v
            prev = v
        }
        return total
    }
}
