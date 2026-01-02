package pt.ipt.projetodam_findme

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class LatLng(
    val latitude: Double,
    val longitude: Double
) : Parcelable

@Parcelize
data class Zone(
    val id: String,
    val name: String,
    val adminId: String,
    val associatedUserId: String,
    val coordinates: List<LatLng>
) : Parcelable
