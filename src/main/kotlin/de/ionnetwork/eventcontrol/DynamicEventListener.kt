/*
 * EventControl - Advanced Event Management Plugin
 * Copyright (c) 2025 IONNetwork
 *
 * This plugin allows server administrators to control and cancel
 * Bukkit events with support for global, world, and region scopes.
 */
package de.ionnetwork.eventcontrol

import org.bukkit.Location
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.Plugin
import java.util.logging.Logger

/**
 * Dynamically registers and handles event cancellation
 *
 * @author IONNetwork
 */
class DynamicEventListener(
    private val plugin: Plugin,
    private val configManager: ConfigManager,
    private val eventDiscovery: EventDiscovery,
    private val logger: Logger
) : Listener {

    private val registeredEvents = mutableSetOf<String>()

    /**
     * Register all discovered cancellable events
     */
    fun registerAllEvents() {
        if (configManager.debugEnabled) {
            logger.info("Registering dynamic event listeners...")
        }

        val eventNames = eventDiscovery.getEventNames()

        eventNames.forEach { eventName ->
            registerEvent(eventName)
        }

        if (configManager.debugEnabled) {
            logger.info("Registered ${registeredEvents.size} event listeners")
        }
    }

    /**
     * Register a specific event by name
     */
    fun registerEvent(eventName: String): Boolean {
        if (registeredEvents.contains(eventName)) {
            return false
        }

        val eventClass = eventDiscovery.getEventClass(eventName) ?: run {
            logger.warning("Event class not found: $eventName")
            return false
        }

        if (!eventDiscovery.isCancellable(eventName)) {
            logger.fine("Skipping non-cancellable event: $eventName")
            return false
        }

        try {
            // Create event executor that handles the event
            val executor = EventExecutor { _, event ->
                handleEvent(event, eventName)
            }

            // Register the event with Bukkit
            plugin.server.pluginManager.registerEvent(
                eventClass,
                this,
                EventPriority.HIGHEST,
                executor,
                plugin,
                false
            )

            registeredEvents.add(eventName)
            if (configManager.debugEnabled) {
                logger.info("Registered event listener: $eventName")
            }
            return true

        } catch (e: Exception) {
            logger.warning("Failed to register event $eventName: ${e.message}")
            return false
        }
    }

    /**
     * Handle an event and potentially cancel it
     */
    private fun handleEvent(event: Event, eventName: String) {
        if (event !is Cancellable) {
            return
        }

        try {
            // Get event context
            val location = extractLocation(event)
            val worldName = location?.world?.name

            // Check if event should be cancelled
            val shouldCancel = configManager.shouldCancelEvent(eventName, worldName, location)

            if (shouldCancel) {
                event.isCancelled = true
                if (configManager.debugEnabled) {
                    logger.info("Cancelled event: $eventName in world $worldName at location ${location?.let { "(${it.blockX}, ${it.blockY}, ${it.blockZ})" } ?: "unknown"}")
                }
            }

        } catch (e: Exception) {
            logger.warning("Error handling event $eventName: ${e.message}")
        }
    }

    /**
     * Extract location from an event using reflection
     */
    private fun extractLocation(event: Event): Location? {
        try {
            val eventClass = event.javaClass

            // Try common location getter methods
            val locationMethods = listOf(
                "getLocation",
                "getBlock",
                "getPlayer",
                "getEntity",
                "getVehicle"
            )

            for (methodName in locationMethods) {
                try {
                    val method = eventClass.getMethod(methodName)
                    val result = method.invoke(event)

                    when (result) {
                        is Location -> return result
                        is org.bukkit.block.Block -> return result.location
                        is org.bukkit.entity.Entity -> return result.location
                        null -> continue
                    }
                } catch (e: NoSuchMethodException) {
                    // Method doesn't exist, try next one
                    continue
                } catch (e: Exception) {
                    // Other error, continue
                    continue
                }
            }

            // Try to get location from nested objects
            try {
                val blockMethod = eventClass.getMethod("getBlock")
                val block = blockMethod.invoke(event) as? org.bukkit.block.Block
                if (block != null) return block.location
            } catch (e: Exception) {
                // Ignore
            }

            try {
                val entityMethod = eventClass.getMethod("getEntity")
                val entity = entityMethod.invoke(event) as? org.bukkit.entity.Entity
                if (entity != null) return entity.location
            } catch (e: Exception) {
                // Ignore
            }

            try {
                val playerMethod = eventClass.getMethod("getPlayer")
                val player = playerMethod.invoke(event) as? org.bukkit.entity.Player
                if (player != null) return player.location
            } catch (e: Exception) {
                // Ignore
            }

        } catch (e: Exception) {
            logger.finest("Could not extract location from ${event.javaClass.simpleName}")
        }

        return null
    }

    /**
     * Unregister all events
     */
    fun unregisterAll() {
        org.bukkit.event.HandlerList.unregisterAll(this)
        registeredEvents.clear()
        if (configManager.debugEnabled) {
            logger.info("Unregistered all event listeners")
        }
    }

    /**
     * Reload event listeners
     */
    fun reload() {
        unregisterAll()
        registerAllEvents()
    }

    /**
     * Get count of registered events
     */
    fun getRegisteredCount(): Int {
        return registeredEvents.size
    }
}
