package com.github.tejaslamba2006.adminwatchdog;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public final class DiscordManager {

    private static final String TIME_PLACEHOLDER = "%time%";
    private static final String PLAYER_PLACEHOLDER = "%player%";
    private static final String COMMAND_PLACEHOLDER = "%command%";
    private static final String SENDER_PLACEHOLDER = "%sender%";
    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int MAX_LORE_LENGTH = 200;

    private final AdminWatchdog plugin;
    private final MinecraftApiHelper apiHelper;
    private final ConcurrentLinkedQueue<String> batchedMessages = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean flushingBatch = new AtomicBoolean(false);
    private ScheduledTask batchFlushTask;

    public DiscordManager(AdminWatchdog plugin) {
        this.plugin = plugin;
        this.apiHelper = new MinecraftApiHelper();
        startBatchingTaskIfEnabled();
    }

    public void sendToDiscord(String message) {
        if (!plugin.getConfigManager().isDiscordEnabled()) {
            return;
        }

        if (plugin.getConfigManager().isDiscordBatchingEnabled()) {
            batchedMessages.offer(message);

            int maxBatchMessages = plugin.getConfigManager().getDiscordBatchMaxMessages();
            if (batchedMessages.size() >= maxBatchMessages) {
                CompletableFuture.runAsync(this::flushBatchedMessages);
            }
            return;
        }

        CompletableFuture.runAsync(() -> {
            JsonObject payload = new JsonObject();
            payload.addProperty("content", truncate(sanitize(message)));
            JsonObject allowedMentions = new JsonObject();
            JsonArray parse = new JsonArray();
            parse.add("users");
            parse.add("roles");
            parse.add("everyone");
            allowedMentions.add("parse", parse);
            payload.add("allowed_mentions", allowedMentions);
            postToWebhook(payload.toString());
        });
    }

    private void startBatchingTaskIfEnabled() {
        if (!plugin.getConfigManager().isDiscordBatchingEnabled()) {
            return;
        }

        long intervalMillis = plugin.getConfigManager().getDiscordBatchIntervalMs();
        batchFlushTask = plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin,
                task -> flushBatchedMessages(),
                intervalMillis,
                intervalMillis,
                TimeUnit.MILLISECONDS);
    }

    private void flushBatchedMessages() {
        if (!plugin.getConfigManager().isDiscordBatchingEnabled()) {
            return;
        }

        if (!flushingBatch.compareAndSet(false, true)) {
            return;
        }

        try {
            String nextBatch;
            while ((nextBatch = buildNextBatchMessage()) != null) {
                JsonObject payload = new JsonObject();
                payload.addProperty("content", truncate(sanitize(nextBatch)));
                postToWebhook(payload.toString());
            }
        } finally {
            flushingBatch.set(false);
        }
    }

    private String buildNextBatchMessage() {
        String firstMessage = batchedMessages.poll();
        if (firstMessage == null) {
            return null;
        }

        int maxBatchMessages = plugin.getConfigManager().getDiscordBatchMaxMessages();
        int maxCombinedLength = plugin.getConfigManager().getDiscordBatchMaxCombinedLength();

        StringBuilder combined = new StringBuilder(firstMessage);
        int messagesAdded = 1;

        while (messagesAdded < maxBatchMessages) {
            String nextMessage = batchedMessages.peek();
            if (nextMessage == null) {
                break;
            }

            int nextLength = combined.length() + 1 + nextMessage.length();
            if (nextLength > maxCombinedLength) {
                break;
            }

            batchedMessages.poll();
            combined.append('\n').append(nextMessage);
            messagesAdded++;
        }

        return combined.toString();
    }

    public void shutdown() {
        if (batchFlushTask != null) {
            batchFlushTask.cancel();
            batchFlushTask = null;
        }

        if (!batchedMessages.isEmpty()) {
            flushBatchedMessages();
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

    public void sendCreativeInventoryAction(String playerName, ItemStack item) {
        if (!plugin.getConfigManager().isDiscordEnabled()) {
            return;
        }

        if (plugin.getConfigManager().isDiscordEmbedsEnabled()
                && plugin.getConfigManager().isCreativeInventoryEmbedsEnabled()) {
            sendEmbed(item, "🎨 Creative Inventory Action", null,
                    List.of(Map.entry("Player", playerName)),
                    () -> sendCreativeInventorySimple(playerName, item));
        } else {
            sendCreativeInventorySimple(playerName, item);
        }
    }

    private void sendCreativeInventorySimple(String playerName, ItemStack item) {
        String time = plugin.getConfigManager().getFormattedTime();
        String itemName = getItemDisplayName(item);
        String materialName = item.getType().name();
        int amount = item.getAmount();

        String messageKey = plugin.getConfigManager().isCreativeInventoryDetailedLogging()
                ? "discord.creative-inventory-detailed"
                : "discord.creative-inventory";

        String message = plugin.getConfigManager().getMessage(messageKey,
                PLAYER_PLACEHOLDER, playerName,
                "%amount%", String.valueOf(amount),
                "%item%", itemName,
                "%material%", materialName,
                TIME_PLACEHOLDER, time);
        sendToDiscord(message);
    }

    public void sendCreativeItemDrop(String playerName, ItemStack item) {
        if (!plugin.getConfigManager().isDiscordEnabled()) {
            return;
        }

        if (plugin.getConfigManager().isDiscordEmbedsEnabled()) {
            sendEmbed(item, "📦 Creative Item Dropped", null,
                    List.of(Map.entry("Dropped By", playerName)),
                    () -> sendCreativeItemDropSimple(playerName, item));
        } else {
            sendCreativeItemDropSimple(playerName, item);
        }
    }

    private void sendCreativeItemDropSimple(String playerName, ItemStack item) {
        String time = plugin.getConfigManager().getFormattedTime();
        String itemName = getItemDisplayName(item);
        int amount = item.getAmount();

        String message = plugin.getConfigManager().getMessage("discord.creative-item-drop",
                PLAYER_PLACEHOLDER, playerName,
                "%amount%", String.valueOf(amount),
                "%item%", itemName,
                "%material%", item.getType().name(),
                TIME_PLACEHOLDER, time);
        sendToDiscord(message);
    }

    public void sendCreativeItemPickup(String pickerName, String dropperName, ItemStack item) {
        if (!plugin.getConfigManager().isDiscordEnabled()) {
            return;
        }

        if (plugin.getConfigManager().isDiscordEmbedsEnabled()) {
            sendEmbed(item, "⚠️ Creative Item Picked Up", 0xFF9800,
                    List.of(Map.entry("Picked Up By", pickerName), Map.entry("Originally Dropped By", dropperName)),
                    () -> sendCreativeItemPickupSimple(pickerName, dropperName, item));
        } else {
            sendCreativeItemPickupSimple(pickerName, dropperName, item);
        }
    }

    private void sendCreativeItemPickupSimple(String pickerName, String dropperName, ItemStack item) {
        String time = plugin.getConfigManager().getFormattedTime();
        String itemName = getItemDisplayName(item);
        int amount = item.getAmount();

        String message = plugin.getConfigManager().getMessage("discord.creative-item-pickup",
                "%picker%", pickerName,
                "%dropper%", dropperName,
                "%amount%", String.valueOf(amount),
                "%item%", itemName,
                "%material%", item.getType().name(),
                TIME_PLACEHOLDER, time);
        sendToDiscord(message);
    }

    /**
     * Builds and sends an embed for an item-related admin action. {@code leadingFields} are
     * placed before the common Item/Amount[/Material] fields; falls back to a plain-text
     * message via {@code fallback} if fetching item data or sending fails.
     */
    private void sendEmbed(ItemStack item, String title, Integer colorOverride,
            List<Map.Entry<String, String>> leadingFields, Runnable fallback) {
        ItemStack snapshot = item.clone();
        apiHelper.getItemData(snapshot).thenAcceptAsync(itemData -> {
            try {
                String embedJson = buildEmbedJson(title, colorOverride, snapshot, itemData, leadingFields);
                postToWebhook(embedJson);
            } catch (Exception e) {
                if (plugin.getConfigManager().isFallbackToSimple()) {
                    fallback.run();
                } else if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().warning("Failed to send embed: " + e.getMessage());
                }
            }
        }).exceptionally(ex -> {
            if (plugin.getConfigManager().isFallbackToSimple()) {
                fallback.run();
            }
            return null;
        });
    }

    private String buildEmbedJson(String title, Integer colorOverride, ItemStack item,
            MinecraftApiHelper.ItemData itemData, List<Map.Entry<String, String>> leadingFields) {
        int colorInt = colorOverride != null ? colorOverride : parseEmbedColor();

        JsonObject embed = new JsonObject();
        embed.addProperty("title", title);
        embed.addProperty("color", colorInt);

        JsonObject thumbnail = new JsonObject();
        thumbnail.addProperty("url", itemData.imageUrl());
        embed.add("thumbnail", thumbnail);

        JsonArray fields = new JsonArray();
        for (Map.Entry<String, String> field : leadingFields) {
            fields.add(inlineField(field.getKey(), "**" + sanitize(field.getValue()) + "**"));
        }
        fields.add(inlineField("Item", "**" + sanitize(itemData.name()) + "**"));
        fields.add(inlineField("Amount", "**" + item.getAmount() + "**"));

        if (plugin.getConfigManager().isIncludeTechnicalDetails()) {
            fields.add(inlineField("Material ID", "`" + item.getType().name() + "`"));
        }

        String enchantments = getEnchantmentsString(item);
        if (!enchantments.isEmpty()) {
            fields.add(blockField("Enchantments", sanitize(enchantments)));
        }

        String customName = getCustomItemName(item);
        if (!customName.isEmpty()) {
            fields.add(inlineField("Custom Name", sanitize(customName)));
        }

        List<String> lore = getItemLore(item);
        if (!lore.isEmpty()) {
            String loreText = String.join("\n", lore);
            if (loreText.length() > MAX_LORE_LENGTH) {
                loreText = loreText.substring(0, MAX_LORE_LENGTH - 3) + "...";
            }
            fields.add(blockField("Lore", sanitize(loreText)));
        }

        embed.add("fields", fields);

        JsonObject footer = new JsonObject();
        footer.addProperty("text", plugin.getConfigManager().getFormattedTime() + " • AdminWatchdog Plugin");
        embed.add("footer", footer);

        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        JsonObject payload = new JsonObject();
        payload.add("embeds", embeds);
        return payload.toString();
    }

    private int parseEmbedColor() {
        String embedColor = plugin.getConfigManager().getEmbedColor().replace("#", "");
        try {
            return Integer.parseInt(embedColor, 16);
        } catch (NumberFormatException e) {
            return 0x00d4aa;
        }
    }

    private JsonObject inlineField(String name, String value) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value);
        field.addProperty("inline", true);
        return field;
    }

    private JsonObject blockField(String name, String value) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value);
        field.addProperty("inline", false);
        return field;
    }

    private void postToWebhook(String jsonPayload) {
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
            connection.setRequestProperty("User-Agent", "AdminWatchdog-Plugin/1.0");

            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 204 && responseCode != 200) {
                String errorMessage = plugin.getConfigManager().getMessage("errors.webhook-failed", "%code%",
                        String.valueOf(responseCode));
                plugin.getLogger().warning(errorMessage);

                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().warning("Failed JSON payload: " + jsonPayload);
                }
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error sending Discord webhook: " + e.getMessage());
            if (plugin.getConfigManager().isDebugEnabled()) {
                e.printStackTrace();
                plugin.getLogger().warning("Failed JSON payload: " + jsonPayload);
            }
        }
    }

    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            try {
                return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(item.getItemMeta().displayName()).trim();
            } catch (Exception e) {

            }
        }
        return item.getType().name().toLowerCase().replace('_', ' ');
    }

    private String getEnchantmentsString(ItemStack item) {
        try {
            if (!item.hasItemMeta() || item.getItemMeta().getEnchants().isEmpty()) {
                return "";
            }

            Map<Enchantment, Integer> enchants = item.getItemMeta().getEnchants();
            return enchants.entrySet().stream()
                    .map(entry -> {
                        try {
                            String enchantName = entry.getKey().getKey().getKey();
                            enchantName = enchantName.replace('_', ' ');
                            enchantName = enchantName.substring(0, 1).toUpperCase() + enchantName.substring(1);
                            return enchantName + " " + entry.getValue();
                        } catch (Exception e) {
                            return "Unknown Enchantment";
                        }
                    })
                    .collect(Collectors.joining(", "));
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().warning("Error getting enchantments: " + e.getMessage());
            }
            return "";
        }
    }

    private String getCustomItemName(ItemStack item) {
        try {
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(item.getItemMeta().displayName()).trim();
            }
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().warning("Error getting custom name: " + e.getMessage());
            }
        }
        return "";
    }

    private List<String> getItemLore(ItemStack item) {
        try {
            if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
                return List.of();
            }

            return item.getItemMeta().lore().stream()
                    .map(component -> {
                        try {
                            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                                    .serialize(component).trim();
                        } catch (Exception e) {
                            return "[Invalid Text]";
                        }
                    })
                    .filter(line -> !line.isEmpty())
                    .toList();
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().warning("Error getting item lore: " + e.getMessage());
            }
            return List.of();
        }
    }

    private String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replaceAll("[\u0000-\u001F\u007F-\u009F]", "")
                .replaceAll("\u00A7[0-9a-fk-or]", "");
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() > MAX_MESSAGE_LENGTH) {
            return text.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
        }
        return text;
    }
}
