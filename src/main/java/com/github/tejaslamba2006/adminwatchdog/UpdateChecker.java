package com.github.tejaslamba2006.adminwatchdog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class UpdateChecker {

    private static final String MODRINTH_PROJECT_SLUG = "adminwatchdog";
    private static final String MODRINTH_API_URL = "https://api.modrinth.com/v2/project/%s/version";
    private static final String MODRINTH_VERSION_PAGE_URL = "https://modrinth.com/plugin/%s/version/%s";
    private static final long STARTUP_DELAY_TICKS = 100L;
    private static final int CONNECTION_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 10000;

    private final AdminWatchdog plugin;
    private final String currentVersion;
    private ScheduledTask updateTask;
    private String latestVersion;
    private String downloadUrl;
    private boolean updateAvailable = false;

    public UpdateChecker(AdminWatchdog plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getPluginMeta().getVersion();
    }

    public void startUpdateChecker() {
        if (!plugin.getConfigManager().isUpdateCheckerEnabled()) {
            return;
        }

        updateTask = plugin.getServer().getAsyncScheduler().runDelayed(
                plugin,
                task -> checkForUpdates(),
                STARTUP_DELAY_TICKS * 50L,
                TimeUnit.MILLISECONDS);

        plugin.getLogger().info("Update checker started. Will check on startup only.");
    }

    /**
     * Stop the update checker task
     */
    public void stopUpdateChecker() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    /**
     * Check for updates manually (for command use)
     */
    public CompletableFuture<UpdateResult> checkForUpdatesSync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return performUpdateCheck();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to check for updates: " + e.getMessage(), e);
                return new UpdateResult(false, currentVersion, currentVersion, null,
                        "Failed to check: " + e.getMessage());
            }
        });
    }

    /**
     * Async update check
     */
    private void checkForUpdates() {
        try {
            UpdateResult result = performUpdateCheck();

            if (result.isUpdateAvailable()) {
                this.updateAvailable = true;
                this.latestVersion = result.getLatestVersion();
                this.downloadUrl = result.getDownloadUrl();

                plugin.getLogger().info("===================================");
                plugin.getLogger().info("UPDATE AVAILABLE!");
                plugin.getLogger().info("Current: " + currentVersion);
                plugin.getLogger().info("Latest: " + latestVersion);
                plugin.getLogger().info("Download: " + downloadUrl);
                plugin.getLogger().info("===================================");

                plugin.getServer().getGlobalRegionScheduler().execute(plugin, this::notifyAdministrators);

                if (plugin.getConfigManager().isUpdateNotificationDiscordEnabled()) {
                    sendDiscordUpdateNotification();
                }
            } else if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("No updates available. Current version " + currentVersion + " is up to date.");
            }

        } catch (Exception e) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().log(Level.WARNING, "Failed to check for updates: " + e.getMessage(), e);
            }
        }
    }

    private UpdateResult performUpdateCheck() throws IOException {
        String projectSlug = MODRINTH_PROJECT_SLUG;
        String apiUrl = String.format(MODRINTH_API_URL, projectSlug);

        URL url = URI.create(apiUrl).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "AdminWatchdog-UpdateChecker/1.0");
        connection.setConnectTimeout(CONNECTION_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("Modrinth API returned response code: " + responseCode);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        JsonArray versions = JsonParser.parseString(response.toString()).getAsJsonArray();
        JsonObject latestVersionEntry = findLatestReleaseVersion(versions);

        String fetchedLatestVersion = latestVersionEntry.get("version_number").getAsString();
        String latestVersionId = latestVersionEntry.get("id").getAsString();

        boolean isNewer = isNewerVersion(currentVersion, fetchedLatestVersion);
        String versionPageUrl = String.format(MODRINTH_VERSION_PAGE_URL, projectSlug, latestVersionId);

        return new UpdateResult(isNewer, currentVersion, fetchedLatestVersion, versionPageUrl, null);
    }

    private JsonObject findLatestReleaseVersion(JsonArray versions) throws IOException {
        if (versions == null || versions.isEmpty()) {
            throw new IOException("Modrinth API returned no versions");
        }

        JsonObject fallback = null;
        for (JsonElement element : versions) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject version = element.getAsJsonObject();
            if (fallback == null) {
                fallback = version;
            }

            if (version.has("version_type")
                    && "release".equalsIgnoreCase(version.get("version_type").getAsString())) {
                return version;
            }
        }

        if (fallback != null) {
            return fallback;
        }

        throw new IOException("Modrinth API returned no valid version objects");
    }

    /**
     * Compare versions to see if latest is newer
     */
    private boolean isNewerVersion(String current, String latest) {
        try {
            String[] currentParts = current.split("\\.");
            String[] latestParts = latest.split("\\.");

            int maxLength = Math.max(currentParts.length, latestParts.length);

            for (int i = 0; i < maxLength; i++) {
                int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
                int latestPart = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;

                if (latestPart > currentPart) {
                    return true;
                } else if (latestPart < currentPart) {
                    return false;
                }
            }

            return false;
        } catch (NumberFormatException e) {
            return !current.equals(latest);
        }
    }

    /**
     * Notify online admins about the update
     */
    private void notifyAdministrators() {
        if (!updateAvailable)
            return;

        String message = plugin.getConfigManager().getMessage("update.notification",
                "%current%", currentVersion,
                "%latest%", latestVersion,
                "%download%", downloadUrl);

        Component component = plugin.getConfigManager().deserializeConfiguredMessage(message);

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.hasPermission("adminwatchdog.update.notify")) {
                player.getScheduler().execute(plugin, () -> player.sendMessage(component), null, 1L);
            }
        }
    }

    /**
     * Send Discord update notification
     */
    private void sendDiscordUpdateNotification() {
        if (!plugin.getConfigManager().isDiscordEnabled()) {
            return;
        }

        String message = plugin.getConfigManager().getMessage("discord.update-available",
                "%current%", currentVersion,
                "%latest%", latestVersion,
                "%download%", downloadUrl);

        plugin.getDiscordManager().sendToDiscord(message);
    }

    /**
     * Get update status
     */
    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    /**
     * Update check result
     */
    public static class UpdateResult {
        private final boolean updateAvailable;
        private final String currentVersion;
        private final String latestVersion;
        private final String downloadUrl;
        private final String error;

        public UpdateResult(boolean updateAvailable, String currentVersion, String latestVersion, String downloadUrl,
                String error) {
            this.updateAvailable = updateAvailable;
            this.currentVersion = currentVersion;
            this.latestVersion = latestVersion;
            this.downloadUrl = downloadUrl;
            this.error = error;
        }

        public boolean isUpdateAvailable() {
            return updateAvailable;
        }

        public String getCurrentVersion() {
            return currentVersion;
        }

        public String getLatestVersion() {
            return latestVersion;
        }

        public String getDownloadUrl() {
            return downloadUrl;
        }

        public String getError() {
            return error;
        }

        public boolean hasError() {
            return error != null;
        }
    }
}