package com.github.tejaslamba2006.adminwatchdog;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ConfigManager {

    private final AdminWatchdog plugin;
    private FileConfiguration messagesConfig;
    private File messagesFile;

    public ConfigManager(AdminWatchdog plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    private void loadMessages() {
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public void reloadConfigs() {
        plugin.reloadConfig();
        loadMessages();
    }

    public String getMessage(String path) {
        return messagesConfig.getString(path, "Message not found: " + path);
    }

    public String getMessage(String path, String... placeholders) {
        String message = getMessage(path);
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                message = message.replace(placeholders[i], placeholders[i + 1]);
            }
        }
        return message;
    }

    public String getFormattedTime() {
        String pattern = plugin.getConfig().getString("general.time-format", "yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern));
    }

    public boolean isOpsMonitoringEnabled() {
        return plugin.getConfig().getBoolean("monitoring.ops", true);
    }

    public boolean isPermissionMonitoringEnabled() {
        return plugin.getConfig().getBoolean("monitoring.permissions.enabled", true);
    }

    public List<String> getMonitoredPermissions() {
        return plugin.getConfig().getStringList("monitoring.permissions.list");
    }

    public boolean isConsoleMonitoringEnabled() {
        return plugin.getConfig().getBoolean("monitoring.console", true);
    }

    public boolean isGamemodeMonitoringEnabled() {
        return plugin.getConfig().getBoolean("monitoring.gamemode-changes", true);
    }

    public boolean isAllCommandsMonitoringEnabled() {
        return plugin.getConfig().getBoolean("monitoring.all-commands", false);
    }

    public boolean isCommandBlacklisted(String command) {
        if (!plugin.getConfig().getBoolean("monitoring.command-blacklist.enabled", true)) {
            return false;
        }
        List<String> blacklist = plugin.getConfig().getStringList("monitoring.command-blacklist.commands");
        return blacklist.stream().anyMatch(cmd -> command.toLowerCase().startsWith("/" + cmd.toLowerCase()));
    }

    public boolean isDiscordEnabled() {
        return plugin.getConfig().getBoolean("discord.enabled", true);
    }

    public String getWebhookUrl() {
        return plugin.getConfig().getString("discord.webhook-url", "");
    }

    public boolean isFileLoggingEnabled() {
        return plugin.getConfig().getBoolean("logging.file-logging", true);
    }

    public boolean isDebugEnabled() {
        return plugin.getConfig().getBoolean("general.debug", false);
    }

    public String getPrefix(String type) {
        return getMessage("prefixes." + type, "");
    }

    public void saveMessages() {
        try {
            messagesConfig.save(messagesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save messages.yml: " + e.getMessage());
        }
    }
}