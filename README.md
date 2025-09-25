# 🛡️ AdminWatchdog

<div align="center">

![License](https://img.shields.io/github/license/tejaslamba2006/AdminWatchdog?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-17+-orange?style=for-the-badge&logo=java)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20+-green?style=for-the-badge&logo=minecraft)
![Downloads](https://img.shields.io/github/downloads/tejaslamba2006/AdminWatchdog/total?style=for-the-badge)
![Stars](https://img.shields.io/github/stars/tejaslamba2006/AdminWatchdog?style=for-the-badge)

**A comprehensive Minecraft server monitoring plugin that tracks admin activities and sends Discord notifications**

[📥 Download](https://github.com/tejaslamba2006/AdminWatchdog/releases) • [📖 Documentation](#-features) • [🐛 Report Bug](https://github.com/tejaslamba2006/AdminWatchdog/issues) • [💡 Request Feature](https://github.com/tejaslamba2006/AdminWatchdog/issues)

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

*Rich embed showing detailed item information*

![Creative Inventory Embed](screenshots/creative-embed.png)
> **Note:** Screenshot placeholder - Upload your webhook screenshot here

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

- `%player%` - The player who executed the command
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
  
  # Critical Commands
  op: "🔐 **CRITICAL** - %player% gave OP to someone! <@&123456789> %time%"
  deop: "🔐 **CRITICAL** - %player% removed OP from someone! <@&123456789> %time%"
  
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

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📝 Changelog

### Latest Version

- ✨ Added custom command response system
- 🎨 Improved Discord embed formatting  
- 🐛 Fixed Material ID display in embeds
- 🔧 Enhanced error handling and logging
- 🚀 Production-ready optimizations

[View Full Changelog](CHANGELOG.md)

## 🐛 Known Issues

- None currently reported

## 💡 Feature Requests

Have an idea for AdminWatchdog? [Open an issue](https://github.com/tejaslamba2006/AdminWatchdog/issues) with the `enhancement` label!

## 📞 Support

Need help? Here's how to get support:

1. 📖 Check the [Documentation](#-features)
2. 🔍 Search [Existing Issues](https://github.com/tejaslamba2006/AdminWatchdog/issues)
3. 💬 Join our [Discord Server](https://discord.gg/YOUR_DISCORD) *(if you have one)*
4. 🐛 [Create a New Issue](https://github.com/tejaslamba2006/AdminWatchdog/issues/new)

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## ⭐ Show Your Support

If you found this plugin helpful, please consider:

- ⭐ **Starring** this repository
- 🍴 **Forking** for your own modifications
- 📢 **Sharing** with other server administrators
- ☕ **Supporting** development (if you have donation links)

## 🙏 Acknowledgments

- Thanks to the Minecraft modding community
- Discord for their excellent webhook API
- All contributors and users who provide feedback

---

<div align="center">

**Made with ❤️ for the Minecraft community**

[⬆ Back to Top](#️-adminwatchdog)

</div>
