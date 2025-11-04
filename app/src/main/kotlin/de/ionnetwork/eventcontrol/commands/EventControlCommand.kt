package de.ionnetwork.eventcontrol.commands

import de.ionnetwork.eventcontrol.ConfigManager
import de.ionnetwork.eventcontrol.DynamicEventListener
import de.ionnetwork.eventcontrol.EventDiscovery
import de.ionnetwork.eventcontrol.EventRule
import de.ionnetwork.eventcontrol.EventScope
import de.ionnetwork.eventcontrol.Region
import de.ionnetwork.eventcontrol.SerializableLocation
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Main command handler for /eventcontrol
 */
class EventControlCommand(
    private val configManager: ConfigManager,
    private val eventDiscovery: EventDiscovery,
    private val eventListener: DynamicEventListener
) : CommandExecutor, TabCompleter {

    // Store player selections for region creation
    private val playerSelections = mutableMapOf<UUID, PlayerSelection>()

    data class PlayerSelection(
        var pos1: SerializableLocation? = null,
        var pos2: SerializableLocation? = null,
        var world: String? = null
    )

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        // Check base permission for any command
        if (!sender.hasPermission("eventcontrol.admin") &&
            !sender.hasPermission("eventcontrol.events.global") &&
            !sender.hasPermission("eventcontrol.events.world") &&
            !sender.hasPermission("eventcontrol.region.create") &&
            !sender.hasPermission("eventcontrol.region.delete") &&
            !sender.hasPermission("eventcontrol.region.manage")) {
            sender.sendMessage("${ChatColor.RED}You don't have permission to use EventControl commands.")
            return true
        }

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "help" -> sendHelp(sender)
            "list" -> {
                if (!sender.hasPermission("eventcontrol.admin")) {
                    sender.sendMessage("${ChatColor.RED}You don't have permission to use this command.")
                    return true
                }
                handleList(sender, args)
            }
            "cancel" -> {
                if (!hasEventPermission(sender, "cancel")) {
                    sender.sendMessage("${ChatColor.RED}You don't have permission to cancel events.")
                    return true
                }
                handleCancel(sender, args)
            }
            "allow" -> {
                if (!hasEventPermission(sender, "allow")) {
                    sender.sendMessage("${ChatColor.RED}You don't have permission to allow events.")
                    return true
                }
                handleAllow(sender, args)
            }
            "info" -> {
                if (!sender.hasPermission("eventcontrol.admin")) {
                    sender.sendMessage("${ChatColor.RED}You don't have permission to use this command.")
                    return true
                }
                handleInfo(sender, args)
            }
            "reload" -> {
                if (!sender.hasPermission("eventcontrol.admin")) {
                    sender.sendMessage("${ChatColor.RED}You don't have permission to reload the configuration.")
                    return true
                }
                handleReload(sender)
            }
            "events" -> {
                if (!sender.hasPermission("eventcontrol.admin")) {
                    sender.sendMessage("${ChatColor.RED}You don't have permission to use this command.")
                    return true
                }
                handleEvents(sender, args)
            }
            "region" -> {
                handleRegionCommand(sender, args)
            }
            else -> {
                sender.sendMessage("${ChatColor.RED}Unknown subcommand. Use /eventcontrol help")
            }
        }

        return true
    }

    /**
     * Check if sender has permission for event management
     * Checks scope-specific permissions
     */
    private fun hasEventPermission(sender: CommandSender, action: String): Boolean {
        return sender.hasPermission("eventcontrol.admin") ||
               sender.hasPermission("eventcontrol.events.global") ||
               sender.hasPermission("eventcontrol.events.world")
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}EventControl Commands:")
        sender.sendMessage("${ChatColor.YELLOW}/ec list ${ChatColor.GRAY}- List all active event rules")
        sender.sendMessage("${ChatColor.YELLOW}/ec cancel <event> global ${ChatColor.GRAY}- Cancel event globally")
        sender.sendMessage("${ChatColor.YELLOW}/ec cancel <event> world [world] ${ChatColor.GRAY}- Cancel event in world")
        sender.sendMessage("${ChatColor.YELLOW}/ec cancel <event> region <region> ${ChatColor.GRAY}- Cancel event in region")
        sender.sendMessage("${ChatColor.YELLOW}/ec allow <event> <scope> ${ChatColor.GRAY}- Remove event cancellation rule")
        sender.sendMessage("${ChatColor.YELLOW}/ec info <event> ${ChatColor.GRAY}- Get info about an event")
        sender.sendMessage("${ChatColor.YELLOW}/ec events [filter] ${ChatColor.GRAY}- List all discovered events")
        sender.sendMessage("${ChatColor.YELLOW}/ec region <subcommand> ${ChatColor.GRAY}- Manage regions (use /ec region help)")
        sender.sendMessage("${ChatColor.YELLOW}/ec reload ${ChatColor.GRAY}- Reload configuration")
    }

    private fun handleList(sender: CommandSender, args: Array<out String>) {
        val rules = configManager.getAllEventRules()

        if (rules.isEmpty()) {
            sender.sendMessage("${ChatColor.YELLOW}No event rules configured.")
            return
        }

        sender.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}Active Event Rules (${rules.size}):")

        val groupedRules = rules.groupBy { it.scope }

        groupedRules[EventScope.GLOBAL]?.let { globalRules ->
            sender.sendMessage("${ChatColor.AQUA}Global:")
            globalRules.forEach { rule ->
                val status = if (rule.enabled) "${ChatColor.GREEN}✓" else "${ChatColor.RED}✗"
                sender.sendMessage("  $status ${ChatColor.WHITE}${rule.eventName}")
            }
        }

        groupedRules[EventScope.WORLD]?.let { worldRules ->
            sender.sendMessage("${ChatColor.AQUA}World-Specific:")
            worldRules.forEach { rule ->
                val status = if (rule.enabled) "${ChatColor.GREEN}✓" else "${ChatColor.RED}✗"
                sender.sendMessage("  $status ${ChatColor.WHITE}${rule.eventName} ${ChatColor.GRAY}in ${rule.worldName}")
            }
        }

        groupedRules[EventScope.REGION]?.let { regionRules ->
            sender.sendMessage("${ChatColor.AQUA}Region-Specific:")
            regionRules.forEach { rule ->
                val status = if (rule.enabled) "${ChatColor.GREEN}✓" else "${ChatColor.RED}✗"
                sender.sendMessage("  $status ${ChatColor.WHITE}${rule.eventName} ${ChatColor.GRAY}in region ${rule.regionName}")
            }
        }
    }

    private fun handleCancel(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage("${ChatColor.RED}Usage: /ec cancel <event> <global|world|region> [world/region name]")
            return
        }

        val eventName = args[1]
        val scopeStr = args[2].lowercase()

        // Validate event exists
        if (eventDiscovery.getEventClass(eventName) == null) {
            sender.sendMessage("${ChatColor.RED}Unknown event: $eventName")
            sender.sendMessage("${ChatColor.GRAY}Use /ec events to see all available events")
            return
        }

        // Check if event is cancellable
        if (!eventDiscovery.isCancellable(eventName)) {
            sender.sendMessage("${ChatColor.RED}Event $eventName is not cancellable!")
            return
        }

        when (scopeStr) {
            "global" -> {
                if (!sender.hasPermission("eventcontrol.admin") &&
                    !sender.hasPermission("eventcontrol.events.global")) {
                    sender.sendMessage("${ChatColor.RED}You don't have permission to cancel events globally.")
                    return
                }
                val rule = EventRule(eventName, EventScope.GLOBAL)
                configManager.addEventRule(rule)
                sender.sendMessage("${ChatColor.GREEN}Event $eventName will now be cancelled globally.")
            }
            "world" -> {
                if (!sender.hasPermission("eventcontrol.admin") &&
                    !sender.hasPermission("eventcontrol.events.world")) {
                    sender.sendMessage("${ChatColor.RED}You don't have permission to cancel events in worlds.")
                    return
                }
                val worldName = if (args.size > 3) {
                    args[3]
                } else if (sender is Player) {
                    sender.world.name
                } else {
                    sender.sendMessage("${ChatColor.RED}Please specify a world name.")
                    return
                }

                val rule = EventRule(eventName, EventScope.WORLD, worldName = worldName)
                configManager.addEventRule(rule)
                sender.sendMessage("${ChatColor.GREEN}Event $eventName will now be cancelled in world $worldName.")
            }
            "region" -> {
                if (!sender.hasPermission("eventcontrol.admin") &&
                    !sender.hasPermission("eventcontrol.region.manage")) {
                    sender.sendMessage("${ChatColor.RED}You don't have permission to manage region events.")
                    return
                }
                if (args.size < 4) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ec cancel <event> region <region name>")
                    return
                }

                val regionName = args[3]
                val region = configManager.getRegion(regionName)

                if (region == null) {
                    sender.sendMessage("${ChatColor.RED}Region '$regionName' does not exist.")
                    sender.sendMessage("${ChatColor.GRAY}Use /ec region list to see all regions")
                    return
                }

                val rule = EventRule(eventName, EventScope.REGION, regionName = regionName)
                configManager.addEventRule(rule)
                sender.sendMessage("${ChatColor.GREEN}Event $eventName will now be cancelled in region $regionName.")
            }
            else -> {
                sender.sendMessage("${ChatColor.RED}Invalid scope. Use: global, world, or region")
            }
        }
    }

    private fun handleAllow(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage("${ChatColor.RED}Usage: /ec allow <event> <global|world|region> [world/region name]")
            return
        }

        val eventName = args[1]
        val scopeStr = args[2].lowercase()

        when (scopeStr) {
            "global" -> {
                if (!sender.hasPermission("eventcontrol.admin") &&
                    !sender.hasPermission("eventcontrol.events.global")) {
                    sender.sendMessage("${ChatColor.RED}You don't have permission to allow events globally.")
                    return
                }
                configManager.removeEventRule(eventName, EventScope.GLOBAL)
                sender.sendMessage("${ChatColor.GREEN}Removed global cancellation for event $eventName.")
            }
            "world" -> {
                if (!sender.hasPermission("eventcontrol.admin") &&
                    !sender.hasPermission("eventcontrol.events.world")) {
                    sender.sendMessage("${ChatColor.RED}You don't have permission to allow events in worlds.")
                    return
                }
                val worldName = if (args.size > 3) {
                    args[3]
                } else if (sender is Player) {
                    sender.world.name
                } else {
                    sender.sendMessage("${ChatColor.RED}Please specify a world name.")
                    return
                }

                configManager.removeEventRule(eventName, EventScope.WORLD, worldName = worldName)
                sender.sendMessage("${ChatColor.GREEN}Removed world cancellation for event $eventName in $worldName.")
            }
            "region" -> {
                if (!sender.hasPermission("eventcontrol.admin") &&
                    !sender.hasPermission("eventcontrol.region.manage")) {
                    sender.sendMessage("${ChatColor.RED}You don't have permission to manage region events.")
                    return
                }
                if (args.size < 4) {
                    sender.sendMessage("${ChatColor.RED}Usage: /ec allow <event> region <region name>")
                    return
                }

                val regionName = args[3]
                configManager.removeEventRule(eventName, EventScope.REGION, regionName = regionName)
                sender.sendMessage("${ChatColor.GREEN}Removed region cancellation for event $eventName in region $regionName.")
            }
            else -> {
                sender.sendMessage("${ChatColor.RED}Invalid scope. Use: global, world, or region")
            }
        }
    }

    private fun handleInfo(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.RED}Usage: /ec info <event>")
            return
        }

        val eventName = args[1]
        val eventClass = eventDiscovery.getEventClass(eventName)

        if (eventClass == null) {
            sender.sendMessage("${ChatColor.RED}Unknown event: $eventName")
            return
        }

        sender.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}Event Information:")
        sender.sendMessage("${ChatColor.YELLOW}Name: ${ChatColor.WHITE}$eventName")
        sender.sendMessage("${ChatColor.YELLOW}Class: ${ChatColor.WHITE}${eventClass.name}")
        sender.sendMessage("${ChatColor.YELLOW}Cancellable: ${if (eventDiscovery.isCancellable(eventName)) "${ChatColor.GREEN}Yes" else "${ChatColor.RED}No"}")

        val rules = configManager.getEventRules(eventName)
        if (rules.isNotEmpty()) {
            sender.sendMessage("${ChatColor.YELLOW}Active Rules:")
            rules.forEach { rule ->
                val scopeInfo = when (rule.scope) {
                    EventScope.GLOBAL -> "Global"
                    EventScope.WORLD -> "World: ${rule.worldName}"
                    EventScope.REGION -> "Region: ${rule.regionName}"
                }
                sender.sendMessage("  ${ChatColor.GRAY}- $scopeInfo")
            }
        } else {
            sender.sendMessage("${ChatColor.GRAY}No active rules for this event")
        }
    }

    private fun handleReload(sender: CommandSender) {
        configManager.load()
        eventListener.reload()
        sender.sendMessage("${ChatColor.GREEN}EventControl configuration reloaded!")
    }

    private fun handleEvents(sender: CommandSender, args: Array<out String>) {
        val filter = if (args.size > 1) args[1].lowercase() else ""
        val allEvents = eventDiscovery.getEventNames().sorted()

        val filteredEvents = if (filter.isNotEmpty()) {
            allEvents.filter { it.lowercase().contains(filter) }
        } else {
            allEvents
        }

        if (filteredEvents.isEmpty()) {
            sender.sendMessage("${ChatColor.YELLOW}No events found matching filter: $filter")
            return
        }

        sender.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}Discovered Events (${filteredEvents.size}):")

        val cancellableCount = filteredEvents.count { eventDiscovery.isCancellable(it) }
        sender.sendMessage("${ChatColor.GRAY}Cancellable: $cancellableCount, Non-cancellable: ${filteredEvents.size - cancellableCount}")

        filteredEvents.chunked(10).forEachIndexed { index, chunk ->
            if (index == 0 || filter.isNotEmpty()) {
                chunk.forEach { event ->
                    val cancellable = if (eventDiscovery.isCancellable(event)) {
                        "${ChatColor.GREEN}[C]"
                    } else {
                        "${ChatColor.RED}[N]"
                    }
                    sender.sendMessage("  $cancellable ${ChatColor.WHITE}$event")
                }
            }
        }

        if (filteredEvents.size > 10 && filter.isEmpty()) {
            sender.sendMessage("${ChatColor.GRAY}... and ${filteredEvents.size - 10} more. Use /ec events <filter> to search")
        }
    }

    // ==================== REGION COMMANDS ====================

    private fun handleRegionCommand(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("${ChatColor.RED}Region commands can only be used by players.")
            return
        }

        if (args.size < 2) {
            sendRegionHelp(sender)
            return
        }

        when (args[1].lowercase()) {
            "help" -> sendRegionHelp(sender)
            "pos1" -> {
                if (!hasRegionPermission(sender, "create")) {
                    sender.sendMessage("${ChatColor.RED}You don't have permission to set region positions.")
                    return
                }
                handleRegionPos1(sender)
            }
            "pos2" -> {
                if (!hasRegionPermission(sender, "create")) {
                    sender.sendMessage("${ChatColor.RED}You don't have permission to set region positions.")
                    return
                }
                handleRegionPos2(sender)
            }
            "create" -> {
                if (!hasRegionPermission(sender, "create")) {
                    sender.sendMessage("${ChatColor.RED}You don't have permission to create regions.")
                    return
                }
                handleRegionCreate(sender, args)
            }
            "delete" -> {
                if (!hasRegionPermission(sender, "delete")) {
                    sender.sendMessage("${ChatColor.RED}You don't have permission to delete regions.")
                    return
                }
                handleRegionDelete(sender, args)
            }
            "list" -> {
                if (!hasRegionPermission(sender, "list")) {
                    sender.sendMessage("${ChatColor.RED}You don't have permission to list regions.")
                    return
                }
                handleRegionList(sender, args)
            }
            "info" -> {
                if (!hasRegionPermission(sender, "list")) {
                    sender.sendMessage("${ChatColor.RED}You don't have permission to view region info.")
                    return
                }
                handleRegionInfo(sender, args)
            }
            else -> {
                sender.sendMessage("${ChatColor.RED}Unknown region subcommand. Use /ec region help")
            }
        }
    }

    /**
     * Check if sender has permission for region management
     */
    private fun hasRegionPermission(sender: CommandSender, action: String): Boolean {
        if (sender.hasPermission("eventcontrol.admin")) return true

        return when (action) {
            "create" -> sender.hasPermission("eventcontrol.region.create")
            "delete" -> sender.hasPermission("eventcontrol.region.delete")
            "list" -> sender.hasPermission("eventcontrol.region.manage") ||
                      sender.hasPermission("eventcontrol.region.create")
            else -> false
        }
    }

    private fun sendRegionHelp(sender: CommandSender) {
        sender.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}Region Commands:")
        sender.sendMessage("${ChatColor.YELLOW}/ec region pos1 ${ChatColor.GRAY}- Set first position")
        sender.sendMessage("${ChatColor.YELLOW}/ec region pos2 ${ChatColor.GRAY}- Set second position")
        sender.sendMessage("${ChatColor.YELLOW}/ec region create <name> [description] ${ChatColor.GRAY}- Create region")
        sender.sendMessage("${ChatColor.YELLOW}/ec region delete <name> ${ChatColor.GRAY}- Delete region")
        sender.sendMessage("${ChatColor.YELLOW}/ec region list [world] ${ChatColor.GRAY}- List all regions")
        sender.sendMessage("${ChatColor.YELLOW}/ec region info <name> ${ChatColor.GRAY}- Get region info")
    }

    private fun handleRegionPos1(sender: Player) {
        val location = sender.location
        val selection = playerSelections.getOrPut(sender.uniqueId) { PlayerSelection() }

        selection.pos1 = SerializableLocation(location)
        selection.world = location.world?.name

        sender.sendMessage("${ChatColor.GREEN}Position 1 set to: ${ChatColor.WHITE}${selection.pos1!!.toDisplayString()}")

        if (selection.pos2 != null) {
            val volume = calculateVolume(selection.pos1!!, selection.pos2!!)
            sender.sendMessage("${ChatColor.GRAY}Region volume: $volume blocks")
        }
    }

    private fun handleRegionPos2(sender: Player) {
        val location = sender.location
        val selection = playerSelections.getOrPut(sender.uniqueId) { PlayerSelection() }

        // Check if world matches pos1
        if (selection.world != null && selection.world != location.world?.name) {
            sender.sendMessage("${ChatColor.RED}Position 2 must be in the same world as position 1!")
            sender.sendMessage("${ChatColor.GRAY}Position 1 is in world: ${selection.world}")
            return
        }

        selection.pos2 = SerializableLocation(location)
        selection.world = location.world?.name

        sender.sendMessage("${ChatColor.GREEN}Position 2 set to: ${ChatColor.WHITE}${selection.pos2!!.toDisplayString()}")

        if (selection.pos1 != null) {
            val volume = calculateVolume(selection.pos1!!, selection.pos2!!)
            sender.sendMessage("${ChatColor.GRAY}Region volume: $volume blocks")
        }
    }

    private fun handleRegionCreate(sender: Player, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage("${ChatColor.RED}Usage: /ec region create <name> [description]")
            return
        }

        val name = args[2]
        val description = if (args.size > 3) args.drop(3).joinToString(" ") else null

        val selection = playerSelections[sender.uniqueId]

        if (selection?.pos1 == null || selection.pos2 == null) {
            sender.sendMessage("${ChatColor.RED}Please set both positions first using /ec region pos1 and /ec region pos2")
            return
        }

        val world = selection.world ?: run {
            sender.sendMessage("${ChatColor.RED}World not set for selection")
            return
        }

        val region = Region(
            name = name,
            worldName = world,
            pos1 = selection.pos1!!,
            pos2 = selection.pos2!!,
            description = description
        )

        if (!configManager.addRegion(region)) {
            sender.sendMessage("${ChatColor.RED}A region with name '$name' already exists!")
            return
        }

        sender.sendMessage("${ChatColor.GREEN}Region '$name' created successfully!")
        sender.sendMessage("${ChatColor.GRAY}${region.toDisplayString()}")

        val volume = calculateVolume(selection.pos1!!, selection.pos2!!)
        sender.sendMessage("${ChatColor.GRAY}Volume: $volume blocks")

        // Clear selection
        playerSelections.remove(sender.uniqueId)
    }

    private fun handleRegionDelete(sender: Player, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage("${ChatColor.RED}Usage: /ec region delete <name>")
            return
        }

        val name = args[2]

        if (!configManager.removeRegion(name)) {
            sender.sendMessage("${ChatColor.RED}Region '$name' does not exist!")
            return
        }

        sender.sendMessage("${ChatColor.GREEN}Region '$name' deleted successfully!")
        sender.sendMessage("${ChatColor.GRAY}All event rules associated with this region have been removed.")
    }

    private fun handleRegionList(sender: Player, args: Array<out String>) {
        val worldFilter = if (args.size > 2) args[2] else null

        val regions = if (worldFilter != null) {
            configManager.getRegionsForWorld(worldFilter)
        } else {
            configManager.getAllRegions()
        }

        if (regions.isEmpty()) {
            if (worldFilter != null) {
                sender.sendMessage("${ChatColor.YELLOW}No regions found in world: $worldFilter")
            } else {
                sender.sendMessage("${ChatColor.YELLOW}No regions defined.")
            }
            return
        }

        val header = if (worldFilter != null) {
            "${ChatColor.GOLD}${ChatColor.BOLD}Regions in $worldFilter (${regions.size}):"
        } else {
            "${ChatColor.GOLD}${ChatColor.BOLD}All Regions (${regions.size}):"
        }

        sender.sendMessage(header)

        regions.forEach { region ->
            sender.sendMessage("${ChatColor.YELLOW}• ${region.name} ${ChatColor.GRAY}(${region.worldName})")
            if (region.description != null) {
                sender.sendMessage("  ${ChatColor.GRAY}${region.description}")
            }
            sender.sendMessage("  ${ChatColor.GRAY}${region.pos1.toDisplayString()} to ${region.pos2.toDisplayString()}")
        }
    }

    private fun handleRegionInfo(sender: Player, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage("${ChatColor.RED}Usage: /ec region info <name>")
            return
        }

        val name = args[2]
        val region = configManager.getRegion(name)

        if (region == null) {
            sender.sendMessage("${ChatColor.RED}Region '$name' does not exist!")
            return
        }

        sender.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}Region Information:")
        sender.sendMessage("${ChatColor.YELLOW}Name: ${ChatColor.WHITE}${region.name}")
        sender.sendMessage("${ChatColor.YELLOW}World: ${ChatColor.WHITE}${region.worldName}")
        sender.sendMessage("${ChatColor.YELLOW}Position 1: ${ChatColor.WHITE}${region.pos1.toDisplayString()}")
        sender.sendMessage("${ChatColor.YELLOW}Position 2: ${ChatColor.WHITE}${region.pos2.toDisplayString()}")

        val volume = calculateVolume(region.pos1, region.pos2)
        sender.sendMessage("${ChatColor.YELLOW}Volume: ${ChatColor.WHITE}$volume blocks")

        if (region.description != null) {
            sender.sendMessage("${ChatColor.YELLOW}Description: ${ChatColor.WHITE}${region.description}")
        }

        // Show active event rules for this region
        val rules = configManager.getAllEventRules().filter { it.regionName == name }
        if (rules.isNotEmpty()) {
            sender.sendMessage("${ChatColor.YELLOW}Active Event Rules (${rules.size}):")
            rules.forEach { rule ->
                val status = if (rule.enabled) "${ChatColor.GREEN}✓" else "${ChatColor.RED}✗"
                sender.sendMessage("  $status ${ChatColor.WHITE}${rule.eventName}")
            }
        } else {
            sender.sendMessage("${ChatColor.GRAY}No event rules for this region")
        }

        // Check if player is in the region
        if (sender.world.name == region.worldName) {
            val isInside = region.contains(sender.location)
            val status = if (isInside) "${ChatColor.GREEN}inside" else "${ChatColor.RED}outside"
            sender.sendMessage("${ChatColor.GRAY}You are currently $status this region")
        }
    }

    private fun calculateVolume(pos1: SerializableLocation, pos2: SerializableLocation): Long {
        val dx = Math.abs(pos1.x - pos2.x).toLong() + 1
        val dy = Math.abs(pos1.y - pos2.y).toLong() + 1
        val dz = Math.abs(pos1.z - pos2.z).toLong() + 1
        return dx * dy * dz
    }

    // ==================== TAB COMPLETION ====================

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        return when (args.size) {
            1 -> listOf("help", "list", "cancel", "allow", "info", "events", "region", "reload")
                .filter { it.startsWith(args[0].lowercase()) }
            2 -> when (args[0].lowercase()) {
                "cancel", "allow", "info" -> eventDiscovery.getEventNames()
                    .filter { it.lowercase().startsWith(args[1].lowercase()) }
                    .toList()
                "region" -> listOf("help", "pos1", "pos2", "create", "delete", "list", "info")
                    .filter { it.startsWith(args[1].lowercase()) }
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "cancel", "allow" -> listOf("global", "world", "region")
                    .filter { it.startsWith(args[2].lowercase()) }
                "region" -> when (args[1].lowercase()) {
                    "delete", "info" -> configManager.getAllRegions().map { it.name }
                        .filter { it.lowercase().startsWith(args[2].lowercase()) }
                    "list" -> sender.server.worlds.map { it.name }
                        .filter { it.lowercase().startsWith(args[2].lowercase()) }
                    else -> emptyList()
                }
                else -> emptyList()
            }
            4 -> when (args[0].lowercase()) {
                "cancel", "allow" -> when (args[2].lowercase()) {
                    "region" -> configManager.getAllRegions().map { it.name }
                        .filter { it.lowercase().startsWith(args[3].lowercase()) }
                    "world" -> sender.server.worlds.map { it.name }
                        .filter { it.lowercase().startsWith(args[3].lowercase()) }
                    else -> emptyList()
                }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}
