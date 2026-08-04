package com.cheminsdhistoire.app.narration

import com.cheminsdhistoire.app.model.HistoryPlace
import com.cheminsdhistoire.app.model.NarratedStory
import kotlin.math.abs

/**
 * Narrateur local : transforme l'extrait Wikipedia en récit vivant, adressé
 * à un public jeune, avec une pointe d'humour DOSÉE (pas à chaque récit).
 *
 * 100% hors-ligne, instantané, fonctionne sur tous les téléphones.
 * Le style est piloté de façon déterministe par l'identifiant du lieu :
 * un même lieu produit toujours le même récit (utile pour la sauvegarde).
 */
class TemplateNarrator : NarrationEngine {

    override val label = "Narrateur local"

    private val hooks = listOf(
        "Ouvre grand les oreilles : à %DIST% d'ici se cache une histoire que peu de gens connaissent.",
        "Regarde autour de toi. À %DIST% à peine, les pierres ont des choses à te raconter.",
        "Petit voyage dans le temps : tout près, à %DIST%, se dresse %TITRE%.",
        "Ce que tu vois là, à %DIST% d'ici, a traversé les siècles. Écoute plutôt.",
        "Fais une pause mentale : à %DIST%, l'Histoire t'attend, et elle a de la conversation."
    )

    private val bridges = listOf(
        "Et ce n'est pas tout.",
        "Mais accroche-toi, parce que voilà le meilleur.",
        "Ce détail change tout :",
        "Là où ça devient vraiment intéressant :",
        "Petit zoom sur ce qui compte :"
    )

    // Traits d'humour légers, insérés au maximum une fois et pas systématiquement.
    private val quips = listOf(
        "(oui, à l'époque, pas de wifi pour patienter).",
        "(imagine le chantier sans grue ni pelleteuse).",
        "(spoiler : ça ne s'est pas toujours bien terminé).",
        "(à côté, nos embouteillages font presque doux).",
        "(nos ancêtres avaient le sens du décor)."
    )

    private val outros = listOf(
        "Garde ça en tête, et reprenons la route : l'Histoire n'a pas dit son dernier mot.",
        "Voilà, tu ne regarderas plus cet endroit pareil. On continue le chemin.",
        "Retiens ce nom, il te suivra un moment. La suite arrive bientôt.",
        "Fin de l'étape. Roule tranquille, la prochaine histoire se prépare."
    )

    override suspend fun narrate(place: HistoryPlace): NarratedStory {
        val seed = abs(place.pageId.toInt())
        val dist = NarrationUtils.formatDistance(place.distanceMeters)

        val hook = pick(hooks, seed)
            .replace("%DIST%", dist)
            .replace("%TITRE%", place.title)

        val core = humanize(place.extract, place.title)
        val sentences = splitSentences(core)

        val sb = StringBuilder()
        sb.append(hook).append(' ')

        // Petite accroche nommée si le hook ne l'a pas déjà nommée.
        if (!hook.contains(place.title)) {
            sb.append("Bienvenue près de ").append(place.title).append(". ")
        }

        // Insertion d'un pont narratif au tiers du récit.
        val bridgeAt = if (sentences.size >= 4) sentences.size / 3 else -1
        // Humour : seulement pour ~1 lieu sur 3, et une seule fois.
        val quipAt = if (seed % 3 == 0 && sentences.isNotEmpty()) (seed % sentences.size) else -1

        sentences.forEachIndexed { i, s ->
            if (i == bridgeAt) sb.append(pick(bridges, seed + i)).append(' ')
            sb.append(s.trim())
            if (i == quipAt) sb.append(' ').append(pick(quips, seed + 7))
            if (!sb.endsWith(" ")) sb.append(' ')
        }

        sb.append(pick(outros, seed + 3))

        val script = sb.toString().replace(Regex("\\s+"), " ").trim()
        return NarratedStory(
            place = place,
            title = place.title,
            script = script,
            segments = NarrationUtils.toSegments(script),
            imageUrl = place.thumbnailUrl,
            sourceUrl = place.pageUrl
        )
    }

    /** Nettoie l'extrait encyclopédique et le rend un peu plus oral. */
    private fun humanize(extract: String, title: String): String {
        var t = extract.replace(Regex("\\([^)]{0,40}\\)"), "") // parenthèses courtes parasites
        t = t.replace(Regex("\\s+"), " ").trim()
        // Évite de répéter le titre en tête si l'extrait commence par lui.
        if (t.startsWith(title)) t = t.removePrefix(title).trimStart(' ', ',', ':', '—', '-')
        if (t.isNotEmpty()) t = t.replaceFirstChar { it.uppercase() }
        return t
    }

    private fun splitSentences(text: String): List<String> =
        Regex("(?<=[.!?…])\\s+").split(text).map { it.trim() }.filter { it.isNotEmpty() }

    private fun <T> pick(list: List<T>, seed: Int): T = list[abs(seed) % list.size]
}
