package com.github.tejaslamba2006.adminwatchdog;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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

    public DiscordManager(AdminWatchdog plugin) {
        this.plugin = plugin;
        this.apiHelper = new MinecraftApiHelper();
    }

    public void sendToDiscord(String message) {
        CompletableFuture.runAsync(() -> {
            if (!plugin.getConfigManager().isDiscordEnabled())
                return;

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

                // Include allowed_mentions to enable pings
                String jsonPayload = String.format(
                    "{\"content\":\"%s\",\"allowed_mentions\":{\"parse\":[\"users\",\"roles\",\"everyone\"]}}",
                    safeJsonString(message));

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                int responseCode = connection.getResponseCode();
                if (responseCode != 204) {
                    String errorMessage = plugin.getConfigManager().getMessage("errors.webhook-failed", "%code%",
                            String.valueOf(responseCode));
                    plugin.getLogger().warning(errorMessage);
                }

            } catch (Exception e) {
                if (plugin.getConfigManager().isDebugEnabled()) {
                    e.printStackTrace();
                }
            }
        });
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
            sendCreativeInventoryEmbed(playerName, item);
        } else {
            sendCreativeInventorySimple(playerName, item);
        }
    }

    private void sendCreativeInventoryEmbed(String playerName, ItemStack item) {
        CompletableFuture<MinecraftApiHelper.ItemData> itemDataFuture = apiHelper.getItemData(item);

        itemDataFuture.thenAcceptAsync(itemData -> {
            try {
                String embedJson = createCreativeInventoryEmbed(playerName, item, itemData);
                sendJsonToDiscord(embedJson);
            } catch (Exception e) {
                handleEmbedError(e, playerName, item);
            }
        }).exceptionally(ex -> {
            handleEmbedError(ex, playerName, item);
            return null;
        });
    }

    private void handleEmbedError(Throwable e, String playerName, ItemStack item) {
        if (plugin.getConfigManager().isFallbackToSimple()) {
            sendCreativeInventorySimple(playerName, item);
        } else if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().warning("Failed to send embed: " + e.getMessage());
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

    private String createCreativeInventoryEmbed(String playerName, ItemStack item,
            MinecraftApiHelper.ItemData itemData) {
        String time = plugin.getConfigManager().getFormattedTime();
        int amount = item.getAmount();
        String embedColor = plugin.getConfigManager().getEmbedColor().replace("#", "");

        int colorInt;
        try {
            colorInt = Integer.parseInt(embedColor, 16);
        } catch (NumberFormatException e) {
            colorInt = 0x00d4aa;
        }

        StringBuilder embedJson = new StringBuilder();
        embedJson.append("{\"embeds\":[{");
        embedJson.append("\"title\":\"🎨 Creative Inventory Action\",");
        embedJson.append("\"color\":").append(colorInt).append(",");
        embedJson.append("\"thumbnail\":{\"url\":\"").append(safeJsonString(itemData.imageUrl())).append("\"},");
        embedJson.append("\"fields\":[");
        embedJson.append("{\"name\":\"Player\",\"value\":\"**").append(safeJsonString(playerName))
                .append("**\",\"inline\":true},");
        embedJson.append("{\"name\":\"Item\",\"value\":\"**").append(safeJsonString(itemData.name()))
                .append("**\",\"inline\":true},");
        embedJson.append("{\"name\":\"Amount\",\"value\":\"**").append(amount).append("**\",\"inline\":true}");

        if (plugin.getConfigManager().isIncludeTechnicalDetails()) {
            embedJson.append(",{\"name\":\"Material ID\",\"value\":\"`").append(item.getType().name())
                    .append("`\",\"inline\":true}");
        }

        String enchantments = getEnchantmentsString(item);
        if (!enchantments.isEmpty()) {
            embedJson.append(",{\"name\":\"Enchantments\",\"value\":\"").append(safeJsonString(enchantments))
                    .append("\",\"inline\":false}");
        }

        String customName = getCustomItemName(item);
        if (!customName.isEmpty()) {
            embedJson.append(",{\"name\":\"Custom Name\",\"value\":\"").append(safeJsonString(customName))
                    .append("\",\"inline\":true}");
        }

        List<String> lore = getItemLore(item);
        if (!lore.isEmpty()) {
            String loreText = String.join("\\n", lore);
            if (loreText.length() > MAX_LORE_LENGTH) {
                loreText = loreText.substring(0, MAX_LORE_LENGTH - 3) + "...";
            }
            embedJson.append(",{\"name\":\"Lore\",\"value\":\"").append(safeJsonString(loreText))
                    .append("\",\"inline\":false}");
        }

        embedJson.append("],");
        embedJson.append("\"footer\":{\"text\":\"").append(time);
        embedJson.append(" • AdminWatchdog Plugin");
        embedJson.append("\"}");
        embedJson.append("}]}");

        return embedJson.toString();
    }

    private void sendJsonToDiscord(String jsonPayload) {
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
                // Fallback to material name if there's an issue
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

    private String escapeJson(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replaceAll("[\u0000-\u001F\u007F-\u009F]", "")
                .replaceAll("§[0-9a-fk-or]", "");
    }

    private String safeJsonString(String text) {
        if (text == null) {
            return "";
        }

        String escaped = escapeJson(text);

        if (escaped.length() > MAX_MESSAGE_LENGTH) {
            escaped = escaped.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
        }

        return escaped;
    }

    // ==================== Creative Item Drop/Pickup Methods ====================

    public void sendCreativeItemDrop(String playerName, ItemStack item) {
        if (!plugin.getConfigManager().isDiscordEnabled()) {
            return;
        }

        if (plugin.getConfigManager().isDiscordEmbedsEnabled()) {
            sendCreativeItemDropEmbed(playerName, item);
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

    private void sendCreativeItemDropEmbed(String playerName, ItemStack item) {
        CompletableFuture<MinecraftApiHelper.ItemData> itemDataFuture = apiHelper.getItemData(item);

        itemDataFuture.thenAcceptAsync(itemData -> {
            try {
                String embedJson = createCreativeItemDropEmbed(playerName, item, itemData);
                sendJsonToDiscord(embedJson);
            } catch (Exception e) {
                if (plugin.getConfigManager().isFallbackToSimple()) {
                    sendCreativeItemDropSimple(playerName, item);
                }
            }
        }).exceptionally(ex -> {
            if (plugin.getConfigManager().isFallbackToSimple()) {
                sendCreativeItemDropSimple(playerName, item);
            }
            return null;
        });
    }

    private String createCreativeItemDropEmbed(String playerName, ItemStack item,
            MinecraftApiHelper.ItemData itemData) {
        String time = plugin.getConfigManager().getFormattedTime();
        int amount = item.getAmount();
        String embedColor = plugin.getConfigManager().getEmbedColor().replace("#", "");

        int colorInt;
        try {
            colorInt = Integer.parseInt(embedColor, 16);
        } catch (NumberFormatException e) {
            colorInt = 0x00d4aa;
        }

        StringBuilder embedJson = new StringBuilder();
        embedJson.append("{\"embeds\":[{");
        embedJson.append("\"title\":\"📦 Creative Item Dropped\",");
        embedJson.append("\"color\":").append(colorInt).append(",");
        embedJson.append("\"thumbnail\":{\"url\":\"").append(safeJsonString(itemData.imageUrl())).append("\"},");
        embedJson.append("\"fields\":[");
        embedJson.append("{\"name\":\"Dropped By\",\"value\":\"**").append(safeJsonString(playerName))
                .append("**\",\"inline\":true},");
        embedJson.append("{\"name\":\"Item\",\"value\":\"**").append(safeJsonString(itemData.name()))
                .append("**\",\"inline\":true},");
        embedJson.append("{\"name\":\"Amount\",\"value\":\"**").append(amount).append("**\",\"inline\":true}");

        if (plugin.getConfigManager().isIncludeTechnicalDetails()) {
            embedJson.append(",{\"name\":\"Material ID\",\"value\":\"`").append(item.getType().name())
                    .append("`\",\"inline\":true}");
        }

        embedJson.append("],");
        embedJson.append("\"footer\":{\"text\":\"").append(time);
        embedJson.append(" • AdminWatchdog Plugin");
        embedJson.append("\"}");
        embedJson.append("}]}");

        return embedJson.toString();
    }

    public void sendCreativeItemPickup(String pickerName, String dropperName, ItemStack item) {
        if (!plugin.getConfigManager().isDiscordEnabled()) {
            return;
        }

        if (plugin.getConfigManager().isDiscordEmbedsEnabled()) {
            sendCreativeItemPickupEmbed(pickerName, dropperName, item);
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

    private void sendCreativeItemPickupEmbed(String pickerName, String dropperName, ItemStack item) {
        CompletableFuture<MinecraftApiHelper.ItemData> itemDataFuture = apiHelper.getItemData(item);

        itemDataFuture.thenAcceptAsync(itemData -> {
            try {
                String embedJson = createCreativeItemPickupEmbed(pickerName, dropperName, item, itemData);
                sendJsonToDiscord(embedJson);
            } catch (Exception e) {
                if (plugin.getConfigManager().isFallbackToSimple()) {
                    sendCreativeItemPickupSimple(pickerName, dropperName, item);
                }
            }
        }).exceptionally(ex -> {
            if (plugin.getConfigManager().isFallbackToSimple()) {
                sendCreativeItemPickupSimple(pickerName, dropperName, item);
            }
            return null;
        });
    }

    private String createCreativeItemPickupEmbed(String pickerName, String dropperName, ItemStack item,
            MinecraftApiHelper.ItemData itemData) {
        String time = plugin.getConfigManager().getFormattedTime();
        int amount = item.getAmount();

        // Use orange/warning color for pickups
        int colorInt = 0xFF9800;

        StringBuilder embedJson = new StringBuilder();
        embedJson.append("{\"embeds\":[{");
        embedJson.append("\"title\":\"⚠️ Creative Item Picked Up\",");
        embedJson.append("\"color\":").append(colorInt).append(",");
        embedJson.append("\"thumbnail\":{\"url\":\"").append(safeJsonString(itemData.imageUrl())).append("\"},");
        embedJson.append("\"fields\":[");
        embedJson.append("{\"name\":\"Picked Up By\",\"value\":\"**").append(safeJsonString(pickerName))
                .append("**\",\"inline\":true},");
        embedJson.append("{\"name\":\"Originally Dropped By\",\"value\":\"**").append(safeJsonString(dropperName))
                .append("**\",\"inline\":true},");
        embedJson.append("{\"name\":\"Item\",\"value\":\"**").append(safeJsonString(itemData.name()))
                .append("**\",\"inline\":true},");
        embedJson.append("{\"name\":\"Amount\",\"value\":\"**").append(amount).append("**\",\"inline\":true}");

        if (plugin.getConfigManager().isIncludeTechnicalDetails()) {
            embedJson.append(",{\"name\":\"Material ID\",\"value\":\"`").append(item.getType().name())
                    .append("`\",\"inline\":true}");
        }

        embedJson.append("],");
        embedJson.append("\"footer\":{\"text\":\"").append(time);
        embedJson.append(" • AdminWatchdog Plugin");
        embedJson.append("\"}");
        embedJson.append("}]}");

        return embedJson.toString();
    }
}
