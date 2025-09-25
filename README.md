# 🛡️ AdminWatchdog

<div align="center">

![License](https://img.shields.io/badge/License-Proprietary-red?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-17+-orange?style=for-the-badge&logo=java)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20+-green?style=for-the-badge&logo=minecraft)
![Downloads](https://img.shields.io/github/downloads/tejaslamba2006/AdminWatchdog/total?style=for-the-badge)
![Stars](https://img.shields.io/github/stars/tejaslamba2006/AdminWatchdog?style=for-the-badge)
![Sponsor](https://img.shields.io/github/sponsors/TejasLamba2006?style=for-the-badge&logo=github&logoColor=white)

**A comprehensive Minecraft server monitoring plugin that tracks admin activities and sends Discord notifications**

[📥 Download](https://modrinth.com/plugin/adminwatchdog) • [📖 Documentation](#-features) • [🐛 Report Bug](https://github.com/tejaslamba2006/AdminWatchdog/issues) • [💡 Request Feature](https://github.com/tejaslamba2006/AdminWatchdog/issues)

</div>

## 🌟 Overview

AdminWatchdog is a powerful Minecraft plugin designed to monitor and log administrative activities on your server. With real-time Discord notifications, rich embeds, and comprehensive logging, you'll never miss important server events again.

### ✨ Key Highlights

- 🔔 **Real-time Discord Notifications** - Instant webhook alerts for admin actions
- 🎨 **Rich Embeds** - Beautiful Discord embeds with item images and detailed information
- 🎯 **Custom Command Responses** - Configurable alerts for specific commands with role mentions
- 📊 **Creative Inventory Tracking** - Monitor items taken from creative mode
- 🔄 **Auto-Updates** - Built-in update checker keeps your plugin current
- 🛡️ **Permission-Based Monitoring** - Flexible monitoring based on permissions or OP status

## 📸 Screenshots

### Discord Webhook Examples

#### Creative Inventory Monitoring

<img width="421" height="433" alt="image" src="https://github.com/user-attachments/assets/86ed1745-3765-43f7-b030-d618a49c4d44" />


#### Command Execution Alerts

*Real-time command monitoring with player information*

![Command Alerts](screenshots/command-alerts.png)
> **Note:** Screenshot placeholder - Upload your webhook screenshot here

#### Custom Command Responses

*Configurable alerts with role mentions for critical commands*

![Custom Responses](screenshots/custom-responses.png)
> **Note:** Screenshot placeholder - Upload your webhook screenshot here

#### Gamemode Change Notifications

*Tracking gamemode switches*

![Gamemode Changes](screenshots/gamemode-changes.png)
> **Note:** Screenshot placeholder - Upload your webhook screenshot here

## 🚀 Features

### 📡 **Discord Integration**

- **Webhook Support** - Send notifications to any Discord channel
- **Rich Embeds** - Beautiful formatted messages with item images
- **Custom Responses** - Configure specific messages for any command
- **Role Mentions** - Alert specific roles for critical actions

### 🔍 **Monitoring Capabilities**

- **Command Monitoring** - Track all admin commands
- **Creative Inventory** - Monitor items taken from creative mode
- **Gamemode Changes** - Log all gamemode switches
- **Console Commands** - Track server console activity
- **Permission-Based** - Monitor by OP status or specific permissions

### ⚙️ **Configuration Options**

- **Flexible Monitoring** - Choose what to monitor and log
- **Command Blacklisting** - Exclude sensitive commands from logs
- **Time Formatting** - Customizable timestamp formats
- **Debug Mode** - Verbose logging for troubleshooting

### 🔄 **Additional Features**

- **Auto-Update Checker** - Get notified of new releases
- **File Logging** - Local log files with rotation
- **Tab Completion** - User-friendly command interface
- **Reload Support** - Update configuration without restart

## 📦 Installation

1. **Download** the latest release from the [Releases Page](https://github.com/tejaslamba2006/AdminWatchdog/releases)
2. **Place** the JAR file in your server's `plugins` folder
3. **Restart** your server
4. **Configure** the plugin by editing `plugins/AdminWatchdog/config.yml`
5. **Set up** your Discord webhook URL
6. **Reload** the plugin with `/adminwatchdog reload`

## 🔧 Configuration

### Basic Setup

```yaml
# Discord Integration
discord:
  webhook-url: "YOUR_DISCORD_WEBHOOK_URL_HERE"
  enabled: true
  embeds:
    enabled: true
    creative-inventory: true
    color: "#00d4aa"
```

### Custom Command Responses

Configure specific alerts for important commands:

```yaml
custom-responses:
  enabled: true
  ban: "⚠️ **ADMIN ACTION** - %player% banned someone at %time%"
  op: "🔐 **CRITICAL** - %player% used OP command! <@&ROLE_ID> at %time%"
  gamemode: "🎮 **GAMEMODE** - %player% changed gamemode at %time%"
```

### Monitoring Settings

```yaml
monitoring:
  ops: true                    # Monitor operators
  console: true               # Monitor console commands
  gamemode-changes: true     # Monitor gamemode switches
  creative-inventory:
    enabled: true
    detailed-logging: true
```

## 🎯 Custom Command Responses

One of AdminWatchdog's most powerful features is the ability to configure custom Discord messages for specific commands:

### Available Placeholders

- `%player%` - The player who executed the command (for player commands)
- `%sender%` - The sender who executed the command (for console commands)
- `%command%` - The full command that was executed  
- `%time%` - Formatted timestamp

### Discord Mentions

- `<@USER_ID>` - Mention a specific user
- `<@&ROLE_ID>` - Mention a specific role
- `@everyone` - Mention everyone

### Example Configurations

```yaml
custom-responses:
  enabled: true
  
  # Critical Commands (Player)
  op: "🔐 **CRITICAL** - %player% gave OP to someone! <@&123456789> %time%"
  deop: "🔐 **CRITICAL** - %player% removed OP from someone! <@&123456789> %time%"
  
  # Critical Commands (Console)
  "/stop": "🛑 **SERVER SHUTDOWN** - Console is stopping the server! <@&123456789> %time%"
  "/reload": "🔄 **SERVER RELOAD** - %sender% reloaded server at %time%"
  
  # Moderation Commands  
  ban: "⚠️ **BAN** - %player% banned a player at %time%"
  kick: "⚠️ **KICK** - %player% kicked a player at %time%"
  
  # World Management
  gamemode: "🎮 **GAMEMODE** - %player% changed gamemode at %time%"
  tp: "🌐 **TELEPORT** - %player% teleported at %time%"
  
  # Item Management
  give: "🎁 **GIVE** - %player% gave items to someone at %time%"
```

## 📋 Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/adminwatchdog` | `adminwatchdog.use` | Show plugin information |
| `/adminwatchdog version` | `adminwatchdog.use` | Display plugin version |
| `/adminwatchdog reload` | `adminwatchdog.reload` | Reload configuration |
| `/adminwatchdog update` | `adminwatchdog.update.check` | Check for updates |

## 🔑 Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `adminwatchdog.use` | Basic plugin access | `true` |
| `adminwatchdog.reload` | Reload configuration | `op` |
| `adminwatchdog.update.check` | Check for updates | `op` |
| `adminwatchdog.update.notify` | Receive update notifications | `op` |
| `adminwatchdog.monitor` | Be monitored by the plugin | `false` |

## 🛠️ Development

### Building from Source

```bash
git clone https://github.com/tejaslamba2006/AdminWatchdog.git
cd AdminWatchdog
./gradlew build
```

### Requirements

- Java 17 or higher
- Minecraft 1.20+
- Paper/Spigot server

**⚠️ Note:** The source code is provided for reference only. Modification, redistribution, or reuse is prohibited under the license terms.

## 🤝 Contributing

**Important:** This project uses a proprietary license that restricts code modifications and reuse.

### How to Contribute

- 🐛 **Report Bugs** - [Open an issue](https://github.com/tejaslamba2006/AdminWatchdog/issues) describing the problem
- 💡 **Suggest Features** - [Request new features](https://github.com/tejaslamba2006/AdminWatchdog/issues) with detailed descriptions
- 📖 **Improve Documentation** - Suggest documentation improvements via issues
- 🧪 **Test Beta Versions** - Help test pre-release versions

### ⚠️ Contribution Restrictions

- ❌ **No code pull requests** - Source code modifications are not accepted
- ❌ **No forks for modification** - Creating derivative works is prohibited  
- ❌ **No code suggestions** - Direct code contributions cannot be merged
- ✅ **Ideas and feedback welcome** - Conceptual suggestions are appreciated

For special licensing arrangements or partnership opportunities, please [contact me directly](https://github.com/TejasLamba2006).

## 💡 Feature Requests

Have an idea for AdminWatchdog? [Open an issue](https://github.com/tejaslamba2006/AdminWatchdog/issues) with the `enhancement` label!

## 📞 Support

Need help? Here's how to get support:

1. 📖 Check the [Documentation](#-features)
2. 🔍 Search [Existing Issues](https://github.com/tejaslamba2006/AdminWatchdog/issues)
3. 💬 Join our [Discord Server](https://discord.gg/msEkYDWpXM)
4. 🐛 [Create a New Issue](https://github.com/tejaslamba2006/AdminWatchdog/issues/new)

## 📄 License

This project is licensed under a **Proprietary License** - see the [LICENSE](LICENSE) file for details.

### ⚠️ Important License Notes

- ❌ **Source code cannot be copied, modified, or reused**
- ❌ **No derivative works or modifications allowed**  
- ❌ **Reverse engineering is prohibited**
- ✅ **Commercial use of compiled plugin is allowed**
- ✅ **Distribution of original compiled plugin is permitted**
- ⚖️ **All rights reserved by TejasLamba2006**

For licensing inquiries or special permissions, please [contact me](https://github.com/TejasLamba2006).

## ⭐ Show Your Support

If you found this plugin helpful, please consider:

- ⭐ **Starring** this repository
- 🍴 **Forking** for your own modifications
- 📢 **Sharing** with other server administrators
- ☕ **[Sponsoring on GitHub](https://github.com/sponsors/TejasLamba2006)** to support development

## 🙏 Acknowledgments

- Thanks to the Minecraft modding community
- Discord for their excellent webhook API
- All contributors and users who provide feedback

---

<div align="center">

**Made with ❤️ for the Minecraft community**

[⬆ Back to Top](#️-adminwatchdog)

</div>
