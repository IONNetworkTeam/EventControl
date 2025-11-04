package de.ionnetwork.eventcontrol

import kotlinx.serialization.Serializable
import org.bukkit.event.Event
import org.bukkit.event.Cancellable
import java.io.File
import java.lang.reflect.Modifier
import java.net.JarURLConnection
import java.util.jar.JarFile
import java.util.logging.Logger

/**
 * Discovers all Bukkit events using reflection
 */
class EventDiscovery(private val logger: Logger) {

    private val discoveredEvents = mutableMapOf<String, Class<out Event>>()
    private val cancellableEvents = mutableSetOf<String>()

    /**
     * Scan and discover all Bukkit events
     */
    fun discoverEvents(): Map<String, EventInfo> {
        logger.info("Starting event discovery...")

        try {
            // Scan org.bukkit.event package for all Event subclasses
            scanBukkitEvents()

            logger.info("Discovered ${discoveredEvents.size} events (${cancellableEvents.size} cancellable)")

        } catch (e: Exception) {
            logger.severe("Error during event discovery: ${e.message}")
            e.printStackTrace()
        }

        return discoveredEvents.map { (name, clazz) ->
            name to EventInfo(
                name = name,
                className = clazz.name,
                cancellable = cancellableEvents.contains(name),
                package_ = clazz.`package`?.name ?: "unknown"
            )
        }.toMap()
    }

    /**
     * Scan for Bukkit events by examining the Bukkit/Spigot JAR
     */
    private fun scanBukkitEvents() {
        try {
            val eventClass = Event::class.java
            val classLoader = eventClass.classLoader
            val packageName = "org/bukkit/event"

            // Get the resource URL for the bukkit event package
            val resource = classLoader.getResource(packageName)

            when {
                resource == null -> {
                    logger.warning("Could not find Bukkit event package")
                    loadPredefinedEvents()
                }
                resource.protocol == "jar" -> {
                    // We're running from a JAR file
                    scanJarFile(resource, packageName)
                }
                resource.protocol == "file" -> {
                    // We're running from a file system (development)
                    scanFileSystem(File(resource.toURI()), packageName)
                }
                else -> {
                    logger.warning("Unknown resource protocol: ${resource.protocol}")
                    loadPredefinedEvents()
                }
            }

        } catch (e: Exception) {
            logger.warning("Error scanning Bukkit events: ${e.message}")
            loadPredefinedEvents()
        }
    }

    /**
     * Scan a JAR file for Event classes
     */
    private fun scanJarFile(resource: java.net.URL, packagePath: String) {
        try {
            val connection = resource.openConnection() as JarURLConnection
            val jarFile = connection.jarFile

            jarFile.entries().asSequence()
                .filter { it.name.startsWith(packagePath) && it.name.endsWith(".class") }
                .forEach { entry ->
                    val className = entry.name
                        .replace('/', '.')
                        .removeSuffix(".class")

                    tryLoadEventClass(className)
                }

        } catch (e: Exception) {
            logger.warning("Error scanning JAR file: ${e.message}")
        }
    }

    /**
     * Scan the file system for Event classes
     */
    private fun scanFileSystem(directory: File, packagePath: String) {
        try {
            if (!directory.exists()) return

            directory.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .forEach { file ->
                    val relativePath = file.relativeTo(directory.parentFile.parentFile.parentFile)
                    val className = relativePath.path
                        .replace(File.separatorChar, '.')
                        .removeSuffix(".class")

                    tryLoadEventClass(className)
                }

        } catch (e: Exception) {
            logger.warning("Error scanning file system: ${e.message}")
        }
    }

    /**
     * Try to load and process a class as an Event
     */
    private fun tryLoadEventClass(className: String) {
        try {
            val clazz = Class.forName(className)

            // Check if it's an Event subclass
            if (Event::class.java.isAssignableFrom(clazz) && clazz != Event::class.java) {
                @Suppress("UNCHECKED_CAST")
                processEventClass(clazz as Class<out Event>)
            }

        } catch (e: ClassNotFoundException) {
            // Ignore - class might not be available
        } catch (e: NoClassDefFoundError) {
            // Ignore - dependency might not be available
        } catch (e: Exception) {
            logger.fine("Could not load class $className: ${e.message}")
        }
    }

    /**
     * Process an event class and add it to the registry
     */
    private fun processEventClass(clazz: Class<out Event>) {
        try {
            // Skip abstract classes
            if (Modifier.isAbstract(clazz.modifiers)) {
                return
            }

            val simpleName = clazz.simpleName
            discoveredEvents[simpleName] = clazz

            // Check if cancellable
            if (Cancellable::class.java.isAssignableFrom(clazz)) {
                cancellableEvents.add(simpleName)
            }

            logger.fine("Discovered event: $simpleName (cancellable: ${cancellableEvents.contains(simpleName)})")

        } catch (e: Exception) {
            logger.warning("Error processing event class ${clazz.name}: ${e.message}")
        }
    }

    /**
     * Load predefined list of common Bukkit events as fallback
     */
    private fun loadPredefinedEvents() {
        logger.info("Loading predefined event list as fallback...")

        val commonEvents = listOf(
            // Player events
            "PlayerJoinEvent", "PlayerQuitEvent", "PlayerMoveEvent", "PlayerInteractEvent",
            "PlayerChatEvent", "AsyncPlayerChatEvent", "PlayerCommandPreprocessEvent",
            "PlayerDropItemEvent", "PlayerPickupItemEvent", "PlayerDeathEvent",
            "PlayerRespawnEvent", "PlayerTeleportEvent", "PlayerBedEnterEvent",
            "PlayerBedLeaveEvent", "PlayerToggleSneakEvent", "PlayerToggleSprintEvent",
            // Block events
            "BlockBreakEvent", "BlockPlaceEvent", "BlockBurnEvent", "BlockDamageEvent",
            "BlockExpEvent", "BlockFadeEvent", "BlockFormEvent", "BlockFromToEvent",
            "BlockGrowEvent", "BlockIgniteEvent", "BlockPhysicsEvent", "BlockPistonExtendEvent",
            "BlockPistonRetractEvent", "BlockRedstoneEvent", "BlockSpreadEvent",
            // Entity events
            "EntityDamageEvent", "EntityDamageByEntityEvent", "EntityDeathEvent",
            "EntitySpawnEvent", "EntityExplodeEvent", "EntityTargetEvent",
            "EntityCombustEvent", "EntityChangeBlockEvent", "EntityPortalEvent",
            "EntityTeleportEvent", "EntityBreedEvent", "EntityTameEvent",
            // Inventory events
            "InventoryClickEvent", "InventoryCloseEvent", "InventoryOpenEvent",
            "InventoryDragEvent", "InventoryMoveItemEvent", "InventoryPickupItemEvent",
            // World events
            "WorldLoadEvent", "WorldUnloadEvent", "WorldSaveEvent", "WorldInitEvent",
            "ChunkLoadEvent", "ChunkUnloadEvent", "StructureGrowEvent",
            // Weather events
            "WeatherChangeEvent", "ThunderChangeEvent", "LightningStrikeEvent",
            // Vehicle events
            "VehicleDestroyEvent", "VehicleCreateEvent", "VehicleEnterEvent",
            "VehicleExitEvent", "VehicleMoveEvent"
        )

        commonEvents.forEach { eventName ->
            try {
                // Try to find the event class
                val packages = listOf(
                    "org.bukkit.event.player",
                    "org.bukkit.event.block",
                    "org.bukkit.event.entity",
                    "org.bukkit.event.inventory",
                    "org.bukkit.event.world",
                    "org.bukkit.event.weather",
                    "org.bukkit.event.vehicle"
                )

                for (pkg in packages) {
                    try {
                        val className = "$pkg.$eventName"
                        val clazz = Class.forName(className)
                        @Suppress("UNCHECKED_CAST")
                        processEventClass(clazz as Class<out Event>)
                        break
                    } catch (e: ClassNotFoundException) {
                        continue
                    }
                }
            } catch (e: Exception) {
                // Ignore if event doesn't exist in this version
            }
        }
    }

    /**
     * Get a specific event class by name
     */
    fun getEventClass(eventName: String): Class<out Event>? {
        return discoveredEvents[eventName]
    }

    /**
     * Check if an event is cancellable
     */
    fun isCancellable(eventName: String): Boolean {
        return cancellableEvents.contains(eventName)
    }

    /**
     * Get all discovered event names
     */
    fun getEventNames(): Set<String> {
        return discoveredEvents.keys
    }
}

/**
 * Information about a discovered event
 */
@Serializable
data class EventInfo(
    val name: String,
    val className: String,
    val cancellable: Boolean,
    val package_: String
)
