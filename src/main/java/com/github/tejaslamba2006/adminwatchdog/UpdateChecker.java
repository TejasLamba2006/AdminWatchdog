package com.github.tejaslamba2006.adminwatchdog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class UpdateChecker {

    private static final String DEFAULT_REPO = "tejaslamba2006/AdminWatchdog";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/%s/releases/latest";
    private static final long STARTUP_DELAY_TICKS = 100L;
    private static final int CONNECTION_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 10000;

    private final AdminWatchdog plugin;
    private final String githubRepo;
    private final String currentVersion;
    private BukkitTask updateTask;
    private String latestVersion;
    private String downloadUrl;
    private boolean updateAvailable = false;

    public UpdateChecker(AdminWatchdog plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getPluginMeta().getVersion();
        this.githubRepo = plugin.getConfigManager().getUpdateCheckerRepo();
    }

    public void startUpdateChecker() {
        if (!plugin.getConfigManager().isUpdateCheckerEnabled()) {
            return;
        }

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::checkForUpdates, STARTUP_DELAY_TICKS);

        plugin.getLogger().info("Update checker started. Will check on startup only.");
    }

    /**
     * Stop the update checker
     */
    public void stopUpdateChecker() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    /**
     * Manually check for updates (can be called from command)
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
     * Asynchronous update check
     */
    private void checkForUpdates() {
        try {
            UpdateResult result = performUpdateCheck();

            if (result.isUpdateAvailable()) {
                this.updateAvailable = true;
                this.latestVersion = result.getLatestVersion();
                this.downloadUrl = result.getDownloadUrl();

                plugin.getLogger().info("§e═══════════════════════════════════");
                plugin.getLogger().info("§e    UPDATE AVAILABLE!");
                plugin.getLogger().info("§e    Current: " + currentVersion);
                plugin.getLogger().info("§e    Latest: " + latestVersion);
                plugin.getLogger().info("§e    Download: " + downloadUrl);
                plugin.getLogger().info("§e═══════════════════════════════════");

                Bukkit.getScheduler().runTask(plugin, this::notifyAdministrators);

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
        String repoUrl = String.format(GITHUB_API_URL, githubRepo.isEmpty() ? DEFAULT_REPO : githubRepo);

        URL url = URI.create(repoUrl).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
        connection.setRequestProperty("User-Agent", "AdminWatchdog-UpdateChecker/1.0");
        connection.setConnectTimeout(CONNECTION_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("GitHub API returned response code: " + responseCode);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();

        String fetchedLatestVersion = json.get("tag_name").getAsString();
        if (fetchedLatestVersion.startsWith("v")) {
            fetchedLatestVersion = fetchedLatestVersion.substring(1);
        }

        boolean isNewer = isNewerVersion(currentVersion, fetchedLatestVersion);

        return new UpdateResult(isNewer, currentVersion, fetchedLatestVersion,
                "https://modrinth.com/plugin/adminwatchdog", null);
    }

    /**
     * Compare version strings to determine if one is newer
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
     * Notify online administrators about available update
     */
    private void notifyAdministrators() {
        if (!updateAvailable)
            return;

        String message = plugin.getConfigManager().getMessage("update.notification",
                "%current%", currentVersion,
                "%latest%", latestVersion,
                "%download%", downloadUrl);

        Component component = MiniMessage.miniMessage().deserialize(message);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("adminwatchdog.update.notify")) {
                player.sendMessage(component);
            }
        }
    }

    /**
     * Send Discord notification about available update
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
     * Get current update status
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
     * Result class for update checks
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