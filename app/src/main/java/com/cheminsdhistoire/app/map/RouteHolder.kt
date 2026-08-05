package com.cheminsdhistoire.app.map

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * État de route partagé entre la carte 2D (recherche/tracé) et la carte 3D
 * (navigation en relief). Ainsi le trajet calculé dans l'onglet Carte s'affiche
 * aussi dans l'onglet 3D.
 */
object RouteHolder {
    private val _itinerary = MutableStateFlow<Itinerary?>(null)
    val itinerary: StateFlow<Itinerary?> = _itinerary.asStateFlow()

    private val _route = MutableStateFlow<RoadRoute?>(null)
    val route: StateFlow<RoadRoute?> = _route.asStateFlow()

    fun set(itin: Itinerary?, road: RoadRoute?) {
        _itinerary.value = itin
        _route.value = road
    }

    fun clear() {
        _itinerary.value = null
        _route.value = null
    }
}
