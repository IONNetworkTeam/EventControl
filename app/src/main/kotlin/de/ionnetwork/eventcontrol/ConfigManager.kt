package de.ionnetwork.eventcontrol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.logging.Logger

/**
 * Manages loading and saving of event control configuration
 */
class ConfigManager(
    private val dataFolder: File,
    private val logger: Logger
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val configFile = File(dataFolder, "config.json")
    private val eventsFile = File(dataFolder, "discovered_events.json")

    private var config = EventControlConfig()
    private val regions = mutableMapOf<String, Region>()
    private val eventRules = mutableMapOf<String, MutableList<EventRule>>()

    init {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
        }
    }

    /**
     * Load configuration from disk
     */
    fun load() {
        try {
            if (configFile.exists()) {
                val content = configFile.readText()
                config = json.decodeFromString<EventControlConfig>(content)

                // Build lookup maps
                config.regions.forEach { region ->
                    regions[region.name] = region
                }

                config.events.forEach { rule ->
                    eventRules.getOrPut(rule.eventName) { mutableListOf() }.add(rule)
                }

                logger.info("Loaded configuration: ${config.events.size} rules, ${config.regions.size} regions")
            } else {
                logger.info("No existing configuration found, using defaults")
                save()
            }
        } catch (e: Exception) {
            logger.severe("Error loading configuration: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Save configuration to disk
     */
    fun save() {
        try {
            val updatedConfig = EventControlConfig(
                events = eventRules.values.flatten(),
                regions = regions.values.toList()
            )

            val content = json.encodeToString(updatedConfig)
            configFile.writeText(content)

            logger.info("Saved configuration")
        } catch (e: Exception) {
            logger.severe("Error saving configuration: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Save discovered events to JSON file
     */
    fun saveDiscoveredEvents(events: Map<String, EventInfo>) {
        try {
            val content = json.encodeToString(events)
            eventsFile.writeText(content)
            logger.info("Saved ${events.size} discovered events to ${eventsFile.name}")
        } catch (e: Exception) {
            logger.severe("Error saving discovered events: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Add or update an event rule
     */
    fun addEventRule(rule: EventRule) {
        // Remove existing rule with same parameters
        val existingRules = eventRules.getOrPut(rule.eventName) { mutableListOf() }

        // Remove rule with same scope/world/region
        existingRules.removeIf { existing ->
            existing.scope == rule.scope &&
            existing.worldName == rule.worldName &&
            existing.regionName == rule.regionName
        }

        // Add new rule
        existingRules.add(rule)
        save()
    }

    /**
     * Remove an event rule
     */
    fun removeEventRule(eventName: String, scope: EventScope, worldName: String? = null, regionName: String? = null) {
        val rules = eventRules[eventName] ?: return

        rules.removeIf { rule ->
            rule.scope == scope &&
            rule.worldName == worldName &&
            rule.regionName == regionName
        }

        if (rules.isEmpty()) {
            eventRules.remove(eventName)
        }

        save()
    }

    /**
     * Get all rules for a specific event
     */
    fun getEventRules(eventName: String): List<EventRule> {
        return eventRules[eventName]?.toList() ?: emptyList()
    }

    /**
     * Get all event rules
     */
    fun getAllEventRules(): List<EventRule> {
        return eventRules.values.flatten()
    }

    /**
     * Check if an event should be cancelled for a given location
     */
    fun shouldCancelEvent(eventName: String, worldName: String?, location: org.bukkit.Location?): Boolean {
        val rules = eventRules[eventName] ?: return false

        // Check rules in order of specificity: Region > World > Global

        // Check region rules first
        if (location != null) {
            val regionRule = rules.firstOrNull { rule ->
                rule.enabled &&
                rule.scope == EventScope.REGION &&
                rule.regionName != null &&
                regions[rule.regionName]?.contains(location) == true
            }
            if (regionRule != null) return true
        }

        // Check world rules
        if (worldName != null) {
            val worldRule = rules.firstOrNull { rule ->
                rule.enabled &&
                rule.scope == EventScope.WORLD &&
                rule.worldName == worldName
            }
            if (worldRule != null) return true
        }

        // Check global rules
        val globalRule = rules.firstOrNull { rule ->
            rule.enabled &&
            rule.scope == EventScope.GLOBAL
        }
        return globalRule != null
    }

    /**
     * Add a region
     */
    fun addRegion(region: Region): Boolean {
        if (regions.containsKey(region.name)) {
            return false
        }
        regions[region.name] = region
        save()
        return true
    }

    /**
     * Remove a region
     */
    fun removeRegion(name: String): Boolean {
        if (!regions.containsKey(name)) {
            return false
        }

        // Remove all event rules associated with this region
        eventRules.values.forEach { rules ->
            rules.removeIf { it.regionName == name }
        }

        regions.remove(name)
        save()
        return true
    }

    /**
     * Get a region by name
     */
    fun getRegion(name: String): Region? {
        return regions[name]
    }

    /**
     * Get all regions
     */
    fun getAllRegions(): List<Region> {
        return regions.values.toList()
    }

    /**
     * Get regions for a specific world
     */
    fun getRegionsForWorld(worldName: String): List<Region> {
        return regions.values.filter { it.worldName == worldName }
    }

    /**
     * Clear all configuration
     */
    fun clearAll() {
        eventRules.clear()
        regions.clear()
        save()
    }
}
