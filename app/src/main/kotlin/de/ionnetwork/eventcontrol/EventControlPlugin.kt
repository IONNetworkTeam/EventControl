package de.ionnetwork.eventcontrol

import de.ionnetwork.eventcontrol.commands.EventControlCommand
import org.bukkit.plugin.java.JavaPlugin

/**
 * Main plugin class for EventControl
 *
 * EventControl allows server administrators to cancel specific events globally,
 * per-world, or in specific regions. It uses reflection to discover all available
 * Bukkit events and supports versions 1.8 through 1.21+
 */
class EventControlPlugin : JavaPlugin() {

    private lateinit var eventDiscovery: EventDiscovery
    private lateinit var configManager: ConfigManager
    private lateinit var eventListener: DynamicEventListener

    override fun onEnable() {
        logger.info("=================================================")
        logger.info("EventControl v${description.version} by IONNetwork")
        logger.info("=================================================")

        try {
            // Initialize components
            initializeComponents()

            // Discover events
            discoverEvents()

            // Load configuration
            loadConfiguration()

            // Register event listeners
            registerEventListeners()

            // Register commands
            registerCommands()

            logger.info("EventControl enabled successfully!")
            logger.info("Registered ${eventListener.getRegisteredCount()} event listeners")
            logger.info("Loaded ${configManager.getAllEventRules().size} event rules")
            logger.info("Loaded ${configManager.getAllRegions().size} regions")

        } catch (e: Exception) {
            logger.severe("Failed to enable EventControl!")
            e.printStackTrace()
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        logger.info("EventControl disabled")

        // Unregister all event listeners
        if (::eventListener.isInitialized) {
            eventListener.unregisterAll()
        }

        // Save configuration
        if (::configManager.isInitialized) {
            configManager.save()
        }
    }

    /**
     * Initialize core components
     */
    private fun initializeComponents() {
        logger.info("Initializing components...")

        eventDiscovery = EventDiscovery(logger)
        configManager = ConfigManager(dataFolder, logger)
        eventListener = DynamicEventListener(this, configManager, eventDiscovery, logger)
    }

    /**
     * Discover all Bukkit events
     */
    private fun discoverEvents() {
        logger.info("Discovering Bukkit events...")

        val discoveredEvents = eventDiscovery.discoverEvents()

        // Save discovered events to JSON
        configManager.saveDiscoveredEvents(discoveredEvents)

        logger.info("Event discovery complete")
    }

    /**
     * Load configuration from disk
     */
    private fun loadConfiguration() {
        logger.info("Loading configuration...")

        configManager.load()

        logger.info("Configuration loaded")
    }

    /**
     * Register dynamic event listeners
     */
    private fun registerEventListeners() {
        logger.info("Registering event listeners...")

        eventListener.registerAllEvents()

        logger.info("Event listeners registered")
    }

    /**
     * Register commands
     */
    private fun registerCommands() {
        logger.info("Registering commands...")

        // Register /eventcontrol command (includes region management)
        val eventControlCmd = getCommand("eventcontrol")
        val eventControlHandler = EventControlCommand(configManager, eventDiscovery, eventListener)
        eventControlCmd?.setExecutor(eventControlHandler)
        eventControlCmd?.tabCompleter = eventControlHandler

        logger.info("Commands registered")
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
