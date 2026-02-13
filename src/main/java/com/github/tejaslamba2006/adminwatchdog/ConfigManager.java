package com.github.tejaslamba2006.adminwatchdog;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class ConfigManager {

    private static final int CURRENT_CONFIG_VERSION = 1;
    private static final String CONFIG_VERSION_KEY = "config-version";

    private final AdminWatchdog plugin;
    private FileConfiguration messagesConfig;
    private File messagesFile;

    public ConfigManager(AdminWatchdog plugin) {
        this.plugin = plugin;
        updateConfigIfNeeded();
        loadMessages();
    }

    private void loadMessages() {
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private void updateConfigIfNeeded() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        int currentVersion = plugin.getConfig().getInt(CONFIG_VERSION_KEY, 0);

        if (currentVersion < CURRENT_CONFIG_VERSION) {
            plugin.getLogger().info("Updating config from version " + currentVersion + " to " + CURRENT_CONFIG_VERSION);
            updateConfig();
        } else if (currentVersion > CURRENT_CONFIG_VERSION) {
            plugin.getLogger().warning("Config version (" + currentVersion + ") is newer than plugin version ("
                    + CURRENT_CONFIG_VERSION + "). Some features may not work correctly.");
        }
    }

    private void updateConfig() {
        try {
            FileConfiguration currentConfig = plugin.getConfig();

            InputStream defaultConfigStream = plugin.getResource("config.yml");
            if (defaultConfigStream == null) {
                plugin.getLogger().warning("Could not find default config.yml in plugin jar!");
                return;
            }

            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(defaultConfigStream, java.nio.charset.StandardCharsets.UTF_8));

            addMissingKeys(currentConfig, defaultConfig, "");

            currentConfig.set(CONFIG_VERSION_KEY, CURRENT_CONFIG_VERSION);

            plugin.saveConfig();
            plugin.getLogger().info("Config updated successfully to version " + CURRENT_CONFIG_VERSION);

            defaultConfigStream.close();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to update config: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void addMissingKeys(FileConfiguration current, FileConfiguration defaults, String path) {
        Set<String> defaultKeys = defaults.getConfigurationSection(path.isEmpty() ? "" : path) != null
                ? defaults.getConfigurationSection(path.isEmpty() ? "" : path).getKeys(false)
                : defaults.getKeys(false);

        for (String key : defaultKeys) {
            String fullPath = path.isEmpty() ? key : path + "." + key;

            if (defaults.isConfigurationSection(fullPath)) {
                if (!current.isConfigurationSection(fullPath)) {
                    current.createSection(fullPath);
                }
                addMissingKeys(current, defaults, fullPath);
            } else {
                if (!current.contains(fullPath)) {
                    Object defaultValue = defaults.get(fullPath);
                    current.set(fullPath, defaultValue);
                    plugin.getLogger().info("Added new config key: " + fullPath + " = " + defaultValue);
                }
            }
        }
    }

    public void reloadConfigs() {
        updateConfigIfNeeded();
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

    public boolean isCreativeInventoryMonitoringEnabled() {
        return plugin.getConfig().getBoolean("monitoring.creative-inventory.enabled", true);
    }

    public boolean isCreativeInventoryOpsOnly() {
        return plugin.getConfig().getBoolean("monitoring.creative-inventory.ops-only", false);
    }

    public boolean isCreativeInventoryPermissionsOnly() {
        return plugin.getConfig().getBoolean("monitoring.creative-inventory.permissions-only", false);
    }

    public boolean isCreativeInventoryDetailedLogging() {
        return plugin.getConfig().getBoolean("monitoring.creative-inventory.detailed-logging", true);
    }

    public boolean isCreativeItemDropMonitoringEnabled() {
        return plugin.getConfig().getBoolean("monitoring.creative-item-drops.enabled", true);
    }

    public boolean isCreativeItemDropTrackPickup() {
        return plugin.getConfig().getBoolean("monitoring.creative-item-drops.track-pickup", true);
    }

    public int getCreativeItemDropTrackingDuration() {
        return plugin.getConfig().getInt("monitoring.creative-item-drops.tracking-duration", 300);
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

    public boolean isDiscordEmbedsEnabled() {
        return plugin.getConfig().getBoolean("discord.embeds.enabled", true);
    }

    public boolean isCreativeInventoryEmbedsEnabled() {
        return plugin.getConfig().getBoolean("discord.embeds.creative-inventory", true);
    }

    public String getEmbedColor() {
        return plugin.getConfig().getString("discord.embeds.color", "#00d4aa");
    }

    public boolean isIncludeDescription() {
        return plugin.getConfig().getBoolean("discord.embeds.include-description", true);
    }

    public boolean isIncludeTechnicalDetails() {
        return plugin.getConfig().getBoolean("discord.embeds.include-technical-details", true);
    }

    public boolean isFallbackToSimple() {
        return plugin.getConfig().getBoolean("discord.embeds.fallback-to-simple", true);
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

    public boolean isUpdateCheckerEnabled() {
        return plugin.getConfig().getBoolean("update-checker.enabled", true);
    }

    public String getUpdateCheckerRepo() {
        return "tejaslamba2006/AdminWatchdog";
    }

    public int getUpdateCheckInterval() {
        return 60;
    }

    public boolean isUpdateNotificationDiscordEnabled() {
        return plugin.getConfig().getBoolean("update-checker.discord-notifications", true);
    }

    public void saveMessages() {
        try {
            messagesConfig.save(messagesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save messages.yml: " + e.getMessage());
        }
    }

    public boolean hasCustomCommandResponse(String command) {
        return findMatchingCustomResponse(command) != null;
    }

    public String getCustomCommandResponse(String command) {
        Map.Entry<String, String> match = findMatchingCustomResponse(command);
        return match != null ? match.getValue() : "";
    }

    /**
     * Finds a matching custom response for a command, supporting wildcards.
     * Patterns can use:
     * - Simple command: "lp" matches "/lp" only
     * - Command with subcommand: "lp user" matches "/lp user ..."
     * - Wildcards: "lp user * permission set *" where * matches any single argument
     * 
     * @param command The full command (e.g., "/lp user Steve permission set *")
     * @return Map entry with pattern key and response value, or null if no match
     */
    public Map.Entry<String, String> findMatchingCustomResponse(String command) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("custom-responses");
        if (section == null) {
            return null;
        }

        String cleanCommand = command.toLowerCase().replaceFirst("^/", "");

        // Get all keys except 'enabled' and sort by specificity (longer patterns first)
        List<String> keys = section.getKeys(false).stream()
                .filter(key -> !key.equalsIgnoreCase("enabled"))
                .sorted((a, b) -> {
                    // Count words in pattern (wildcards count as words)
                    int aWords = a.split("\\s+").length;
                    int bWords = b.split("\\s+").length;
                    // Longer patterns (more words) come first
                    if (aWords != bWords) {
                        return Integer.compare(bWords, aWords);
                    }
                    // If same length, wildcards come before non-wildcards
                    boolean aHasWildcard = a.contains("*");
                    boolean bHasWildcard = b.contains("*");
                    if (aHasWildcard != bHasWildcard) {
                        return aHasWildcard ? -1 : 1;
                    }
                    return 0;
                })
                .toList();

        if (plugin.getConfig().getBoolean("general.debug", false)) {
            plugin.getLogger().info("Matching command: '" + cleanCommand + "'");
            plugin.getLogger().info("Pattern order: " + keys);
        }

        // Try all patterns in order of specificity
        for (String key : keys) {
            String pattern = key.toLowerCase();

            // Check wildcard patterns
            if (pattern.contains("*")) {
                boolean matches = matchesWildcardPattern(cleanCommand, pattern);
                if (plugin.getConfig().getBoolean("general.debug", false)) {
                    plugin.getLogger().info("Testing wildcard pattern '" + pattern + "': " + matches);
                }
                if (matches) {
                    String response = section.getString(key, "");
                    if (!response.isEmpty()) {
                        if (plugin.getConfig().getBoolean("general.debug", false)) {
                            plugin.getLogger().info("Matched pattern: " + pattern);
                        }
                        return new AbstractMap.SimpleEntry<>(key, response);
                    }
                }
            }
            // Check exact matches and prefix matches
            else {
                boolean matches = cleanCommand.equals(pattern) || cleanCommand.startsWith(pattern + " ");
                if (plugin.getConfig().getBoolean("general.debug", false)) {
                    plugin.getLogger().info("Testing simple pattern '" + pattern + "': " + matches);
                }
                if (matches) {
                    String response = section.getString(key, "");
                    if (!response.isEmpty()) {
                        if (plugin.getConfig().getBoolean("general.debug", false)) {
                            plugin.getLogger().info("Matched pattern: " + pattern);
                        }
                        return new AbstractMap.SimpleEntry<>(key, response);
                    }
                }
            }
        }

        return null;
    }

    /**
     * Matches a command against a wildcard pattern.
     * Pattern: "lp user * permission set *"
     * Command: "lp user Steve permission set essentials.fly"
     * 
     * @param command The actual command (without leading /)
     * @param pattern The pattern with * wildcards
     * @return true if the command matches the pattern
     */
    private boolean matchesWildcardPattern(String command, String pattern) {
        // Split pattern by asterisks, quote each part, then join with wildcard regex
        String[] parts = pattern.split("\\*", -1);
        StringBuilder regexBuilder = new StringBuilder("^");

        for (int i = 0; i < parts.length; i++) {
            // Quote this part to escape any regex special characters
            String quotedPart = Pattern.quote(parts[i]);
            regexBuilder.append(quotedPart);

            // Add wildcard matcher between parts (but not after the last part)
            if (i < parts.length - 1) {
                regexBuilder.append("[^\\s]+"); // Match one or more non-whitespace characters
            }
        }

        // Allow the command to have more arguments after the pattern
        regexBuilder.append("($|\\s.*)");

        String regex = regexBuilder.toString();

        if (plugin.getConfig().getBoolean("general.debug", false)) {
            plugin.getLogger().info("Pattern: '" + pattern + "' -> Regex: '" + regex + "'");
        }

        try {
            boolean matches = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(command).matches();
            if (plugin.getConfig().getBoolean("general.debug", false)) {
                plugin.getLogger().info("Command '" + command + "' matches regex: " + matches);
            }
            return matches;
        } catch (Exception e) {
            // Invalid pattern, fall back to simple prefix match
            return command.startsWith(pattern.replace("*", "").trim());
        }
    }

    public boolean isCustomCommandResponsesEnabled() {
        return plugin.getConfig().getBoolean("custom-responses.enabled", false);
    }
}