package de.ionnetwork.eventcontrol

import kotlinx.serialization.Serializable
import org.bukkit.Location

/**
 * Represents a rule for event cancellation
 */
@Serializable
data class EventRule(
    val eventName: String,
    val scope: EventScope,
    val enabled: Boolean = true,
    val worldName: String? = null,
    val regionName: String? = null
)

/**
 * Defines the scope of an event rule
 */
@Serializable
enum class EventScope {
    GLOBAL,    // Apply to all worlds and locations
    WORLD,     // Apply to a specific world
    REGION     // Apply to a specific region
}

/**
 * Represents a defined region for event control
 */
@Serializable
data class Region(
    val name: String,
    val worldName: String,
    val pos1: SerializableLocation,
    val pos2: SerializableLocation,
    val description: String? = null
) {
    /**
     * Check if a location is within this region
     */
    fun contains(location: Location): Boolean {
        if (location.world?.name != worldName) return false

        val minX = minOf(pos1.x, pos2.x)
        val maxX = maxOf(pos1.x, pos2.x)
        val minY = minOf(pos1.y, pos2.y)
        val maxY = maxOf(pos1.y, pos2.y)
        val minZ = minOf(pos1.z, pos2.z)
        val maxZ = maxOf(pos1.z, pos2.z)

        return location.x >= minX && location.x <= maxX &&
               location.y >= minY && location.y <= maxY &&
               location.z >= minZ && location.z <= maxZ
    }

    fun toDisplayString(): String {
        return "$name in $worldName: ${pos1.toDisplayString()} to ${pos2.toDisplayString()}"
    }
}

/**
 * Serializable version of Bukkit Location
 */
@Serializable
data class SerializableLocation(
    val x: Double,
    val y: Double,
    val z: Double
) {
    constructor(location: Location) : this(
        location.x,
        location.y,
        location.z
    )

    fun toDisplayString(): String {
        return "(${x.toInt()}, ${y.toInt()}, ${z.toInt()})"
    }
}

/**
 * Container for all event control configuration
 */
@Serializable
data class EventControlConfig(
    val events: List<EventRule> = emptyList(),
    val regions: List<Region> = emptyList()
)
