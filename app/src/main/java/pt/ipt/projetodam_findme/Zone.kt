/**
 * Zone.kt
 *
 * Modelos de dados para coordenadas e zonas (geofences).
 * Implementam Parcelable para passagem entre Activities.
 */
package pt.ipt.projetodam_findme

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Representa uma coordenada geográfica (latitude/longitude) */
@Parcelize
data class LatLng(
    val latitude: Double,
    val longitude: Double
) : Parcelable

/** Representa uma zona/geofence com polígono de coordenadas */
@Parcelize
data class Zone(
    val id: String,
    val name: String,
    val adminId: String,
    val associatedUserId: String,
    val coordinates: List<LatLng>,
    val isActive: Boolean = true
) : Parcelable
