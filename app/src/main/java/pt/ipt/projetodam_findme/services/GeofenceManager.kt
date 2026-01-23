package pt.ipt.projetodam_findme.services

import pt.ipt.projetodam_findme.LatLng // Importante: Usa a sua classe LatLng

object GeofenceManager {

    // Estrutura simples para o ponto atual (GPS)
    data class Point(val lat: Double, val lng: Double)

    // CORREÇÃO: Agora aceita 'List<LatLng>' em vez de String
    fun isPointInPolygon(point: Point, polygon: List<LatLng>): Boolean {
        if (polygon.size < 3) return false

        var isInside = false
        var j = polygon.size - 1

        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]

            // Algoritmo Ray Casting adaptado para a classe LatLng
            if (((pi.longitude > point.lng) != (pj.longitude > point.lng)) &&
                (point.lat < (pj.latitude - pi.latitude) * (point.lng - pi.longitude) / (pj.longitude - pi.longitude) + pi.latitude)
            ) {
                isInside = !isInside
            }
            j = i
        }
        return isInside
    }
}