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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class ConfigManager {

    private static final int CURRENT_CONFIG_VERSION = 3;
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

    public boolean isCommandBlacklisted(String command, boolean isConsole) {
        if (!plugin.getConfig().getBoolean("monitoring.command-blacklist.enabled", true)) {
            return false;
        }

        String section = isConsole ? "monitoring.command-blacklist.console" : "monitoring.command-blacklist.player";
        List<String> blacklist = plugin.getConfig().getStringList(section);

        // Fallback to old format if new format not found
        if (blacklist.isEmpty()) {
            blacklist = plugin.getConfig().getStringList("monitoring.command-blacklist.commands");
        }

        return blacklist.stream().anyMatch(cmd -> command.toLowerCase().startsWith("/" + cmd.toLowerCase()));
    }

    public boolean isDiscordEnabled() {
        return plugin.getConfig().getBoolean("discord.enabled", true);
    }

    public String getWebhookUrl() {
        return plugin.getConfig().getString("discord.webhook-url", "");
    }

    public boolean isDiscordBatchingEnabled() {
        return plugin.getConfig().getBoolean("discord.batching.enabled", false);
    }

    public int getDiscordBatchIntervalMs() {
        return Math.max(250, plugin.getConfig().getInt("discord.batching.interval-ms", 1000));
    }

    public int getDiscordBatchMaxMessages() {
        return Math.max(1, plugin.getConfig().getInt("discord.batching.max-messages", 10));
    }

    public int getDiscordBatchMaxCombinedLength() {
        return Math.max(200, plugin.getConfig().getInt("discord.batching.max-combined-length", 1800));
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

    public boolean hasCustomCommandResponse(String command, boolean isConsole) {
        return findMatchingCustomResponse(command, isConsole) != null;
    }

    public String getCustomCommandResponse(String command, boolean isConsole) {
        Map.Entry<String, String> match = findMatchingCustomResponse(command, isConsole);
        return match != null ? match.getValue() : "";
    }

    public boolean doesCommandMatchPattern(String command, String pattern) {
        String cleanCommand = command.toLowerCase().replaceFirst("^/", "");
        String normalizedPattern = pattern.toLowerCase().replaceFirst("^/", "");

        if (isAdvancedPattern(normalizedPattern)) {
            return matchesAdvancedPattern(cleanCommand, normalizedPattern);
        }

        return cleanCommand.equals(normalizedPattern) || cleanCommand.startsWith(normalizedPattern + " ");
    }

    /**
     * Finds a matching custom response for a command, with wildcard support.
     * Patterns:
     * - Simple: "lp" matches "/lp" only
     * - Subcommand: "lp user" matches "/lp user ..."
     * - Wildcards: "lp user * permission set *" where * matches any single argument
     * 
     * @param command   The full command (e.g., "/lp user Steve permission set *")
     * @param isConsole Whether this is a console command
     * @return Map entry with pattern key and response value, or null if no match
     */
    public Map.Entry<String, String> findMatchingCustomResponse(String command, boolean isConsole) {
        String sectionPath = isConsole ? "custom-responses.console" : "custom-responses.player";
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(sectionPath);

        // Fallback to old format (direct under custom-responses)
        if (section == null) {
            section = plugin.getConfig().getConfigurationSection("custom-responses");
            if (section == null) {
                return null;
            }
        }

        List<String> keys = section.getKeys(false).stream()
                .filter(key -> !key.equalsIgnoreCase("enabled"))
                .sorted((a, b) -> {

                    int aWords = a.split("\\s+").length;
                    int bWords = b.split("\\s+").length;

                    if (aWords != bWords) {
                        return Integer.compare(bWords, aWords);
                    }

                    boolean aHasWildcard = isAdvancedPattern(a);
                    boolean bHasWildcard = isAdvancedPattern(b);
                    if (aHasWildcard != bHasWildcard) {
                        return aHasWildcard ? -1 : 1;
                    }
                    return 0;
                })
                .toList();

        if (plugin.getConfig().getBoolean("general.debug", false)) {
            plugin.getLogger()
                    .info("Matching " + (isConsole ? "console" : "player") + " command: '"
                            + command.toLowerCase().replaceFirst("^/", "") + "'");
            plugin.getLogger().info("Pattern order: " + keys);
        }

        for (String key : keys) {
            String pattern = key.toLowerCase().replaceFirst("^/", "");

            boolean matches = doesCommandMatchPattern(command, pattern);
            if (plugin.getConfig().getBoolean("general.debug", false)) {
                plugin.getLogger().info("Testing pattern '" + pattern + "': " + matches);
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

        return null;
    }

    public boolean isAdvancedPattern(String pattern) {
        return pattern.contains("*") || pattern.contains(">") || pattern.contains("<") || pattern.contains("==");
    }

    /**
     * Checks if a command matches an advanced pattern.
     * Pattern: "give * * >=5"
     * Command: "give Steve diamond 10"
     * 
     * @param command The actual command (without leading /)
     * @param pattern The advanced pattern
     * @return true if the command matches
     */
    private boolean matchesAdvancedPattern(String command, String pattern) {

        String[] cmdArgs = command.trim().split("\\s+");
        String[] patArgs = pattern.trim().split("\\s+");

        if (cmdArgs.length < patArgs.length) {
            return false;
        }

        for (int i = 0; i < patArgs.length; i++) {
            String pWord = patArgs[i];
            String cWord = cmdArgs[i];

            if (pWord.equals("*")) {
                continue;
            } else if (pWord.matches("^(>|<|>=|<=|==)-?\\d+(\\.\\d+)?$")) {
                try {
                    double cValue = Double.parseDouble(cWord);
                    if (pWord.startsWith(">=")) {
                        double pValue = Double.parseDouble(pWord.substring(2));
                        if (!(cValue >= pValue)) return false;
                    } else if (pWord.startsWith("<=")) {
                        double pValue = Double.parseDouble(pWord.substring(2));
                        if (!(cValue <= pValue)) return false;
                    } else if (pWord.startsWith("==")) {
                        double pValue = Double.parseDouble(pWord.substring(2));
                        if (!(cValue == pValue)) return false;
                    } else if (pWord.startsWith(">")) {
                        double pValue = Double.parseDouble(pWord.substring(1));
                        if (!(cValue > pValue)) return false;
                    } else if (pWord.startsWith("<")) {
                        double pValue = Double.parseDouble(pWord.substring(1));
                        if (!(cValue < pValue)) return false;
                    } else {
                        return false;
                    }
                } catch (NumberFormatException e) {
                    return false;
                }
            } else if (pWord.contains("*")) {
                String regex = "^" + Pattern.quote(pWord).replace("*", "\\E.*\\Q") + "$";
                if (!Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(cWord).matches()) {
                    return false;
                }
            } else if (!pWord.equalsIgnoreCase(cWord)) {
                return false;
            }
        }

        return true;
    }

    public boolean isCustomCommandResponsesEnabled() {
        return plugin.getConfig().getBoolean("custom-responses.enabled", false);
    }

    public boolean isSuppressNormalLoggingEnabled() {
        return plugin.getConfig().getBoolean("custom-responses.suppress-normal-logging", false);
    }

    public boolean isRepeatTriggersEnabled() {
        return plugin.getConfig().getBoolean("custom-responses.repeat-triggers.enabled", false);
    }

    public List<RepeatTrigger> getRepeatTriggers(boolean isConsole) {
        if (!isRepeatTriggersEnabled()) {
            return List.of();
        }

        String path = isConsole
                ? "custom-responses.repeat-triggers.console"
                : "custom-responses.repeat-triggers.player";

        List<Map<?, ?>> rawTriggers = plugin.getConfig().getMapList(path);
        List<RepeatTrigger> parsedTriggers = new ArrayList<>();

        for (Map<?, ?> rawTrigger : rawTriggers) {
            Object patternRaw = rawTrigger.containsKey("pattern") ? rawTrigger.get("pattern") : "";
            Object responseRaw = rawTrigger.containsKey("response") ? rawTrigger.get("response") : "";

            String pattern = String.valueOf(patternRaw).trim();
            String response = String.valueOf(responseRaw).trim();

            int count = parsePositiveInt(rawTrigger.get("count"), 3);
            int intervalSeconds = parsePositiveInt(rawTrigger.get("interval-seconds"), 10);

            if (pattern.isEmpty() || response.isEmpty()) {
                continue;
            }

            if (count < 2 || intervalSeconds < 1) {
                continue;
            }

            parsedTriggers.add(new RepeatTrigger(pattern, count, intervalSeconds, response));
        }

        return parsedTriggers;
    }

    private int parsePositiveInt(Object rawValue, int defaultValue) {
        if (rawValue instanceof Number numberValue) {
            return numberValue.intValue();
        }

        if (rawValue instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }

        return defaultValue;
    }

    public record RepeatTrigger(String pattern, int count, int intervalSeconds, String response) {
    }
}