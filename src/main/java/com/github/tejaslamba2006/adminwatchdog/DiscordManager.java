package com.github.tejaslamba2006.adminwatchdog;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class DiscordManager {

    private static final String TIME_PLACEHOLDER = "%time%";
    private static final String PLAYER_PLACEHOLDER = "%player%";
    private static final String COMMAND_PLACEHOLDER = "%command%";
    private static final String SENDER_PLACEHOLDER = "%sender%";

    private final AdminWatchdog plugin;

    public DiscordManager(AdminWatchdog plugin) {
        this.plugin = plugin;
    }

    public void sendToDiscord(String message) {
        if (!plugin.getConfigManager().isDiscordEnabled()) {
            return;
        }

        String webhookUrl = plugin.getConfigManager().getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            plugin.getLogger().warning(plugin.getConfigManager().getMessage("errors.webhook-not-set"));
            return;
        }

        try {
            URL url = URI.create(webhookUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            String jsonPayload = String.format("{\"content\": \"%s\"}", escapeJson(message));

            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonPayload.getBytes());
                os.flush();
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 204) {
                String errorMessage = plugin.getConfigManager().getMessage("errors.webhook-failed", "%code%",
                        String.valueOf(responseCode));
                plugin.getLogger().warning(errorMessage);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendGamemodeChange(String playerName, String oldMode, String newMode) {
        String time = plugin.getConfigManager().getFormattedTime();
        String message = plugin.getConfigManager().getMessage("discord.gamemode-change",
                PLAYER_PLACEHOLDER, playerName,
                "%oldmode%", oldMode,
                "%newmode%", newMode,
                TIME_PLACEHOLDER, time);
        sendToDiscord(message);
    }

    public void sendConsoleCommand(String senderName, String command) {
        String time = plugin.getConfigManager().getFormattedTime();
        String message = plugin.getConfigManager().getMessage("discord.console-command",
                SENDER_PLACEHOLDER, senderName,
                COMMAND_PLACEHOLDER, command,
                TIME_PLACEHOLDER, time);
        sendToDiscord(message);
    }

    public void sendPlayerCommand(String playerName, String command, boolean hasPermission) {
        String time = plugin.getConfigManager().getFormattedTime();
        String messageKey = hasPermission ? "discord.permission-command" : "discord.player-command";
        String message = plugin.getConfigManager().getMessage(messageKey,
                PLAYER_PLACEHOLDER, playerName,
                COMMAND_PLACEHOLDER, command,
                TIME_PLACEHOLDER, time);
        sendToDiscord(message);
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
