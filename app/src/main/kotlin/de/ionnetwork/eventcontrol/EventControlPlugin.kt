/*
 * EventControl - Advanced Event Management Plugin
 * Copyright (c) 2025 IONNetwork
 *
 * This plugin allows server administrators to control and cancel
 * Bukkit events with support for global, world, and region scopes.
 */
package de.ionnetwork.eventcontrol

import de.ionnetwork.eventcontrol.commands.EventControlCommand
import org.bukkit.plugin.java.JavaPlugin

/**
 * Main plugin class for EventControl
 *
 * EventControl allows server administrators to cancel specific events globally,
 * per-world, or in specific regions. It uses reflection to discover all available
 * Bukkit events and supports versions 1.8 through 1.21+
 *
 * @author IONNetwork
 * @version 1.0.0
 * @since 2025
 */
class EventControlPlugin : JavaPlugin() {

    private lateinit var eventDiscovery: EventDiscovery
    private lateinit var configManager: ConfigManager
    private lateinit var eventListener: DynamicEventListener

    override fun onEnable() {
        try {
            // Initialize components (must be first)
            initializeComponents()

            // Load configuration (must be before discovery to get debug setting)
            loadConfiguration()

            // Discover events
            discoverEvents()

            // Register event listeners
            registerEventListeners()

            // Register commands
            registerCommands()

            // Show startup message
            logger.info("EventControl v${description.version} by IONNetwork | ${eventListener.getRegisteredCount()} listeners | ${configManager.getAllEventRules().size} rules | ${configManager.getAllRegions().size} regions")
            logger.info("Website: https://www.ion-network.de | GitHub: https://github.com/IONNetworkTeam")

        } catch (e: Exception) {
            logger.severe("Failed to enable EventControl!")
            e.printStackTrace()
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        // Unregister all event listeners
        if (::eventListener.isInitialized) {
            eventListener.unregisterAll()
        }

        // Save configuration
        if (::configManager.isInitialized) {
            configManager.save()
        }

        if (::configManager.isInitialized && configManager.debugEnabled) {
            logger.info("EventControl disabled")
        }
    }

    /**
     * Initialize core components
     */
    private fun initializeComponents() {
        configManager = ConfigManager(dataFolder, logger)
        eventDiscovery = EventDiscovery(logger) { configManager.debugEnabled }
        eventListener = DynamicEventListener(this, configManager, eventDiscovery, logger)
    }

    /**
     * Discover all Bukkit events
     */
    private fun discoverEvents() {
        if (configManager.debugEnabled) {
            logger.info("Discovering Bukkit events...")
        }

        val discoveredEvents = eventDiscovery.discoverEvents()

        // Save discovered events to JSON
        configManager.saveDiscoveredEvents(discoveredEvents)

        if (configManager.debugEnabled) {
            logger.info("Event discovery complete")
        }
    }

    /**
     * Load configuration from disk
     */
    private fun loadConfiguration() {
        if (configManager.debugEnabled) {
            logger.info("Loading configuration...")
        }

        configManager.load()

        if (configManager.debugEnabled) {
            logger.info("Configuration loaded")
        }
    }

    /**
     * Register dynamic event listeners
     */
    private fun registerEventListeners() {
        if (configManager.debugEnabled) {
            logger.info("Registering event listeners...")
        }

        eventListener.registerAllEvents()

        if (configManager.debugEnabled) {
            logger.info("Event listeners registered")
        }
    }

    /**
     * Register commands
     */
    private fun registerCommands() {
        if (configManager.debugEnabled) {
            logger.info("Registering commands...")
        }

        // Register /eventcontrol command (includes region management)
        val eventControlCmd = getCommand("eventcontrol")
        val eventControlHandler = EventControlCommand(configManager, eventDiscovery, eventListener)
        eventControlCmd?.setExecutor(eventControlHandler)
        eventControlCmd?.tabCompleter = eventControlHandler

        if (configManager.debugEnabled) {
            logger.info("Commands registered")
        }
    }

    /**
     * Reload the plugin configuration
     */
    fun reload() {
        configManager.load()
        eventListener.reload()
    }

    /**
     * Get the event discovery instance
     */
    fun getEventDiscovery(): EventDiscovery = eventDiscovery

    /**
     * Get the config manager instance
     */
    fun getConfigManager(): ConfigManager = configManager

    /**
     * Get the event listener instance
     */
    fun getEventListener(): DynamicEventListener = eventListener
}
