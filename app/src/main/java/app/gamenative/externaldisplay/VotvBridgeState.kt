package app.gamenative.externaldisplay

import kotlinx.serialization.Serializable

/**
 * Mirrors the JSON snapshot written by the GameNativeBridge UE4SS Lua mod
 * (state.json), one field per property the mod currently reports. All
 * fields are nullable since the mod only populates what resolved on the
 * game side that tick (e.g. everything is null while at the main menu).
 */
@Serializable
data class VotvBridgeState(
    val ts: Long? = null,
    val totalPower: Double? = null,
    val usedPower: Double? = null,
    val powerRatio: Double? = null,
    val isHalloween: Boolean? = null,
    val playerDead: Boolean? = null,
    val playerUnderwater: Boolean? = null,
    val playerInWater: Boolean? = null,
    val playerCrouching: Boolean? = null,
    val playerAir: Double? = null,
    val playerFoodDrain: Double? = null,
    val playerSleepDrain: Double? = null,
    val playerHolding: String? = null,
    val playerDrivingAtv: Boolean? = null,
    val playerLocX: Double? = null,
    val playerLocY: Double? = null,
    val playerLocZ: Double? = null,
    val day: Double? = null,
    val dayCount: Double? = null,
    val isRaining: Boolean? = null,
    val thickFog: Double? = null,
    val sunHeight: Double? = null,
    val subArea: String? = null,
    val invCurrVol: Double? = null,
    val invMaxVol: Double? = null,
)
