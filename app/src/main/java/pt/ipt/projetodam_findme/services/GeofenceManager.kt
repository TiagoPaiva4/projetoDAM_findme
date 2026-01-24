/**
 * GeofenceManager.kt
 *
 * Gestor de geofences que implementa o algoritmo Ray Casting.
 * Determina se um ponto está dentro de um polígono (zona).
 */
package pt.ipt.projetodam_findme.services

import pt.ipt.projetodam_findme.LatLng

object GeofenceManager {

    // Estrutura simples para o ponto atual (GPS)
    data class Point(val lat: Double, val lng: Double)

    /**
     * Verifica se um ponto está dentro de um polígono usando o algoritmo Ray Casting.
     *
     * Funcionamento do algoritmo:
     * 1. Traça uma linha horizontal imaginária do ponto até ao infinito
     * 2. Conta quantas vezes essa linha interseta as arestas do polígono
     * 3. Se o número de interseções for ímpar, o ponto está dentro
     * 4. Se for par, o ponto está fora
     *
     * @param point Ponto GPS a verificar (latitude, longitude)
     * @param polygon Lista de vértices que definem o polígono da zona
     * @return true se o ponto está dentro do polígono, false caso contrário
     */
    fun isPointInPolygon(point: Point, polygon: List<LatLng>): Boolean {
        // Polígono precisa de pelo menos 3 pontos
        if (polygon.size < 3) return false

        var isInside = false
        var j = polygon.size - 1

        // Percorre cada aresta do polígono
        for (i in polygon.indices) {
            val pi = polygon[i]  // Ponto atual
            val pj = polygon[j]  // Ponto anterior

            // Verifica se a linha horizontal do ponto interseta esta aresta
            // A condição verifica se o ponto está entre as latitudes dos vértices
            // e se está à esquerda da aresta
            if (((pi.longitude > point.lng) != (pj.longitude > point.lng)) &&
                (point.lat < (pj.latitude - pi.latitude) * (point.lng - pi.longitude) / (pj.longitude - pi.longitude) + pi.latitude)
            ) {
                // Cada interseção inverte o estado (dentro/fora)
                isInside = !isInside
            }
            j = i
        }
        return isInside
    }
}