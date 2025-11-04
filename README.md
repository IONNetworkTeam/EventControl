# EventControl

A powerful Minecraft plugin for controlling and canceling Bukkit events with support for versions 1.8 through 1.21+.

## Features

- **Dynamic Event Discovery**: Automatically discovers all Bukkit events using reflection
- **Multi-Version Support**: Compatible with Minecraft 1.8-1.21.10
- **Flexible Event Cancellation**: Cancel events globally, per-world, or in specific regions
- **JSON Configuration**: All discovered events and rules stored in easy-to-read JSON files
- **Region System**: Define custom 3D regions for location-specific event control
- **Tab Completion**: Full tab completion support for all commands
- **Real-time Updates**: Changes take effect immediately without server restart

## Commands

### EventControl Commands

- `/eventcontrol help` (aliases: `/ec`, `/events`) - Show help menu
- `/ec list` - List all active event rules
- `/ec cancel <event> global` - Cancel an event globally
- `/ec cancel <event> world [world]` - Cancel an event in a specific world
- `/ec cancel <event> region <region>` - Cancel an event in a specific region
- `/ec allow <event> <scope>` - Remove event cancellation rule
- `/ec info <event>` - Get detailed information about an event
- `/ec events [filter]` - List all discovered events (optionally filtered)
- `/ec reload` - Reload configuration

### Region Commands

- `/ec region help` - Show region help menu
- `/ec region pos1` - Set first position for region creation
- `/ec region pos2` - Set second position for region creation
- `/ec region create <name> [description]` - Create a new region
- `/ec region delete <name>` - Delete a region
- `/ec region list [world]` - List all regions (optionally filtered by world)
- `/ec region info <name>` - Get detailed information about a region

## Permissions

All permissions default to `op` only for security.

**Note:** Players must have at least one EventControl permission to access any command (including help). Players without any permissions will receive a "no permission" message.

### Admin Permission
- `eventcontrol.admin` - Full access to all EventControl features, bypasses all other permission checks

### Event Management Permissions
- `eventcontrol.events.global` - Cancel/allow events globally
- `eventcontrol.events.world` - Cancel/allow events in specific worlds

### Region Management Permissions
- `eventcontrol.region.create` - Create regions and set positions (pos1/pos2)
- `eventcontrol.region.delete` - Delete regions
- `eventcontrol.region.manage` - Manage event rules for regions, view region list and info

## Usage Examples

### Cancel player damage globally
```
/ec cancel EntityDamageEvent global
```

### Cancel block breaking in specific world
```
/ec cancel BlockBreakEvent world world_nether
```

### Create a protected spawn region
```
/ec region pos1
/ec region pos2
/ec region create spawn "Protected spawn area"
/ec cancel BlockBreakEvent region spawn
/ec cancel BlockPlaceEvent region spawn
/ec cancel PlayerInteractEvent region spawn
```

### Allow events again
```
/ec allow BlockBreakEvent global
/ec allow PlayerInteractEvent region spawn
```

### List all discovered events
```
/ec events
/ec events player    # Filter events containing "player"
```

## How It Works

1. **Event Discovery**: On startup, the plugin scans the Bukkit API using reflection to find all Event classes
2. **Event Registration**: The plugin registers dynamic listeners for all cancellable events
3. **Event Handling**: When an event fires, the plugin checks if any rules match the event
4. **Priority Matching**: Rules are checked in order: Region > World > Global
5. **Event Cancellation**: If a matching rule is found, the event is cancelled

## Supported Event Scopes

- **Global**: Affects all worlds and all locations
- **World**: Affects only a specific world
- **Region**: Affects only a specific 3D region

## Version Compatibility

This plugin is designed to work across a wide range of Minecraft versions:

- Minecraft 1.8.x - 1.21.10
- Spigot, Paper, and compatible forks
- Events that don't exist in older versions are simply ignored
- New events in newer versions are automatically discovered

## Building from Source

This project uses [Gradle](https://gradle.org/).

```bash
./gradlew build
```

The compiled plugin will be available in `build/libs/EventControl.jar`

Other useful Gradle commands:
* Run `./gradlew clean` to clean all build outputs
* Run `./gradlew shadowJar` to build the plugin with dependencies