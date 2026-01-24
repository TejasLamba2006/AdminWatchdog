# AdminWatchdog

![alt text](logo.png)
<div align="center">

![License](https://img.shields.io/badge/License-Proprietary-red?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-21+-orange?style=for-the-badge&logo=java)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20+-green?style=for-the-badge&logo=minecraft)
![Downloads](https://img.shields.io/github/downloads/tejaslamba2006/AdminWatchdog/total?style=for-the-badge)
![Stars](https://img.shields.io/github/stars/tejaslamba2006/AdminWatchdog?style=for-the-badge)
![Sponsor](https://img.shields.io/github/sponsors/TejasLamba2006?style=for-the-badge&logo=github&logoColor=white)

**Server administration monitoring with Discord webhook integration**

[Download](https://modrinth.com/plugin/adminwatchdog) | [Documentation](#features) | [Issues](https://github.com/tejaslamba2006/AdminWatchdog/issues)

</div>

---

## Overview

AdminWatchdog monitors administrative actions on Minecraft servers and dispatches notifications via Discord webhooks. The plugin supports granular permission-based monitoring, wildcard command pattern matching, and async I/O operations for minimal performance impact.

### Core Features

- **Discord Webhook Integration** - Async HTTP POST to Discord webhook endpoints with embed support
- **Pattern-Based Command Matching** - Wildcard patterns for monitoring specific command structures (e.g., `lp user * permission set *`)
- **Permission-Driven Monitoring** - Configurable monitoring based on OP status or permission nodes
- **Creative Inventory Tracking** - ItemStack monitoring with Guava-cached item metadata
- **Bypass System** - Granular bypass permissions for trusted administrators
- **Async I/O** - Non-blocking file logging and webhook dispatch via CompletableFuture

## Architecture

```
AdminWatchdog
├── CommandListener      # Event handlers for PlayerCommandPreprocessEvent, ServerCommandEvent, etc.
├── ConfigManager        # YAML configuration with wildcard pattern matching engine
├── DiscordManager       # Async webhook dispatch with embed builder
├── MinecraftApiHelper   # Guava-cached item data with 512-entry LRU cache
└── UpdateChecker        # GitHub API polling for version checks
```

## Screenshots

### Discord Webhook Output

**Creative Inventory Embed**

<img width="421" height="433" alt="image" src="https://github.com/user-attachments/assets/86ed1745-3765-43f7-b030-d618a49c4d44" />

**Command Logging**

<img width="993" height="150" alt="image" src="https://github.com/user-attachments/assets/31d78544-8499-4ab1-89ea-0edcb153023a" />

**Custom Response Triggers**

<img width="806" height="70" alt="image" src="https://github.com/user-attachments/assets/e64af8ac-be4c-403a-b376-a0694cce6191" />

**Gamemode Change Events**

<img width="641" height="144" alt="image" src="https://github.com/user-attachments/assets/de961a9e-2f48-41a2-b208-d2fe41ddab50" />

## Features

### Discord Integration

- Webhook POST with JSON payload construction
- Rich embed support with thumbnails, fields, and color coding
- Role/user mention parsing (`<@&ROLE_ID>`, `<@USER_ID>`)
- Automatic retry-safe async dispatch

### Monitoring Subsystems

| Subsystem | Event Source | Configurable |
|-----------|--------------|--------------|
| Command Monitoring | `PlayerCommandPreprocessEvent` | Yes |
| Console Monitoring | `ServerCommandEvent` | Yes |
| Creative Inventory | `InventoryCreativeEvent` | Yes |
| Gamemode Changes | `PlayerGameModeChangeEvent` | Yes |
| Custom Responses | Pattern-matched commands | Yes |

### Wildcard Pattern Matching

The custom-responses system supports three matching modes:

1. **Exact Match** - `ban` matches `/ban`
2. **Prefix Match** - `lp user` matches `/lp user Steve permission set fly`
3. **Wildcard Match** - `lp user * permission set *` where `*` matches any single argument

Pattern matching is evaluated in order: exact > wildcard > prefix for specificity.

### Bypass Permissions

Players with bypass permissions are excluded from monitoring:

| Permission | Scope |
|------------|-------|
| `adminwatchdog.bypass.commands` | Command logging |
| `adminwatchdog.bypass.creative` | Creative inventory |
| `adminwatchdog.bypass.customresponses` | Custom response triggers |
| `adminwatchdog.bypass.gamemode` | Gamemode change logging |

## Installation

```bash
# Download JAR from releases
wget https://github.com/tejaslamba2006/AdminWatchdog/releases/latest/download/AdminWatchdog.jar

# Deploy to plugins directory
mv AdminWatchdog.jar /path/to/server/plugins/

# Restart server to generate config
```

## Configuration

### config.yml

```yaml
discord:
  webhook-url: "https://discord.com/api/webhooks/..."
  enabled: true
  embeds:
    enabled: true
    creative-inventory: true
    color: "#00d4aa"

# Pattern-based command responses
# Supports wildcards: * matches any single argument
custom-responses:
  enabled: true
  # Basic command matching
  ban: "ADMIN ACTION - %player% used ban at %time%"
  op: "CRITICAL - %player% used OP command! <@&ROLE_ID> at %time%"
  
  # Subcommand matching
  "lp user": "LUCKPERMS - %player% modified user at %time%"
  
  # Wildcard patterns
  "lp user * permission set *": "PERMISSION GRANT - %player%: %command% at %time%"
  "lp user * parent add *": "RANK CHANGE - %player%: %command% at %time%"

monitoring:
  ops: true
  console: true
  gamemode-changes: true
  permissions:
    enabled: true
    list:
      - "adminwatchdog.monitor"
      - "minecraft.command.*"
  creative-inventory:
    enabled: true
    ops-only: false
    permissions-only: false
    detailed-logging: true
  command-blacklist:
    enabled: true
    commands:
      - "login"
      - "register"
      - "password"
```

### Placeholder Reference

| Placeholder | Context | Description |
|-------------|---------|-------------|
| `%player%` | Player commands | Player name |
| `%sender%` | Console commands | Sender name |
| `%command%` | All | Full command string |
| `%time%` | All | Formatted timestamp |

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/adminwatchdog version` | `adminwatchdog.use` | Display version info |
| `/adminwatchdog reload` | `adminwatchdog.reload` | Hot-reload configuration |
| `/adminwatchdog update` | `adminwatchdog.update.check` | Query GitHub API for updates |

Aliases: `aw`, `awdog`

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `adminwatchdog.use` | true | Base command access |
| `adminwatchdog.reload` | op | Configuration reload |
| `adminwatchdog.monitor` | op | Subject to monitoring |
| `adminwatchdog.update.check` | op | Manual update checks |
| `adminwatchdog.update.notify` | op | Update notifications |
| `adminwatchdog.bypass.*` | false | All bypass permissions |
| `adminwatchdog.bypass.commands` | false | Bypass command monitoring |
| `adminwatchdog.bypass.creative` | false | Bypass creative monitoring |
| `adminwatchdog.bypass.customresponses` | false | Bypass custom responses |
| `adminwatchdog.bypass.gamemode` | false | Bypass gamemode monitoring |

## Building from Source

### Requirements

- JDK 21+
- Gradle 8.x+
- Paper API 1.20.1+

### Build

```bash
git clone https://github.com/tejaslamba2006/AdminWatchdog.git
cd AdminWatchdog
gradle build
# Output: build/libs/AdminWatchdog.jar
```

### Dependencies

```gradle
dependencies {
    compileOnly 'io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT'
    implementation 'com.google.code.gson:gson:2.10.1'
    // Guava provided by Paper runtime
}
```

## Performance Notes

- File logging uses `CompletableFuture.runAsync()` to avoid blocking the main thread
- Discord webhooks dispatch asynchronously
- Item metadata cached with Guava `Cache<Material, ItemData>` (512 entries, 30-min TTL)
- Pattern matching uses compiled regex with caching for repeated evaluations

## Contributing

This project uses a proprietary license. Code contributions are not accepted.

**Accepted contributions:**

- Bug reports via GitHub Issues
- Feature requests with detailed use cases
- Documentation improvements via issues

**Not accepted:**

- Pull requests with code changes
- Forks intended for redistribution

For licensing inquiries: [contact](https://github.com/TejasLamba2006)

## Support

1. Check existing [Issues](https://github.com/tejaslamba2006/AdminWatchdog/issues)
2. Join [Discord](https://discord.gg/msEkYDWpXM)
3. Open a new issue with reproduction steps

## License

Proprietary - see [LICENSE](LICENSE)

- Source code viewing permitted
- Modification, redistribution, derivative works prohibited
- Commercial use of compiled plugin permitted

---

<div align="center">

[GitHub](https://github.com/TejasLamba2006/AdminWatchdog) | [Modrinth](https://modrinth.com/plugin/adminwatchdog) | [Discord](https://discord.gg/msEkYDWpXM)

</div>
