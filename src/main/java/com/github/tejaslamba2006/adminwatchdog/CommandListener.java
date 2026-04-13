package com.github.tejaslamba2006.adminwatchdog;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CommandListener implements Listener {

    private static final String TIME_PLACEHOLDER = "%time%";
    private static final String PLAYER_PLACEHOLDER = "%player%";
    private static final String SENDER_PLACEHOLDER = "%sender%";
    private static final String COMMAND_PLACEHOLDER = "%command%";
    private static final String ITEM_PLACEHOLDER = "%item%";
    private static final String MATERIAL_PLACEHOLDER = "%material%";
    private static final String AMOUNT_PLACEHOLDER = "%amount%";
    private static final String OTHER_PLAYER_PLACEHOLDER = "%other_player%";
    private static final String MATCHED_LORE_PLACEHOLDER = "%matched_lore%";
    private static final String LORE_PATTERN_PLACEHOLDER = "%lore_pattern%";
    private static final String MATCHED_MATERIAL_PLACEHOLDER = "%matched_material%";
    private static final String MATERIAL_PATTERN_PLACEHOLDER = "%material_pattern%";
    private static final String CREATIVE_ACTION_PLACEHOLDER = "%creative_action%";
    private static final String LORE_TRIGGER_LOG_PREFIX = "[LORE-TRIGGER] ";
    private static final String MATERIAL_TRIGGER_LOG_PREFIX = "[MATERIAL-TRIGGER] ";

    private final File logFile;
    private final AdminWatchdog plugin;

    private final Map<UUID, DroppedItemInfo> trackedCreativeDrops = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> repeatedCommandHistory = new ConcurrentHashMap<>();

    public CommandListener(AdminWatchdog plugin) {
        this.plugin = plugin;
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        logFile = new File(dataFolder, "commands.log");
        try {
            if (!logFile.exists()) {
                boolean created = logFile.createNewFile();
                if (!created) {
                    plugin.getLogger().warning(plugin.getConfigManager().getMessage("errors.log-file-creation-failed"));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        startDropCleanupTask();
    }

    private void startDropCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            int trackingDuration = plugin.getConfigManager().getCreativeItemDropTrackingDuration();
            long expiryTime = TimeUnit.SECONDS.toMillis(trackingDuration);

            trackedCreativeDrops.entrySet().removeIf(entry -> (now - entry.getValue().dropTime()) > expiryTime);
        }, 20L * 60, 20L * 60);
    }

    @EventHandler
    public void onGamemodeChange(PlayerGameModeChangeEvent event) {
        if (!plugin.getConfigManager().isGamemodeMonitoringEnabled()) {
            return;
        }

        Player player = event.getPlayer();

        if (player.hasPermission("adminwatchdog.bypass.gamemode")) {
            return;
        }

        String playerName = player.getName();
        GameMode newMode = event.getNewGameMode();
        GameMode oldMode = player.getGameMode();

        boolean shouldLog = plugin.getConfigManager().isOpsMonitoringEnabled() && player.isOp();

        if (plugin.getConfigManager().isPermissionMonitoringEnabled()) {
            List<String> monitoredPerms = plugin.getConfigManager().getMonitoredPermissions();
            for (String perm : monitoredPerms) {
                if (player.hasPermission(perm)) {
                    shouldLog = true;
                    break;
                }
            }
        }

        if (!shouldLog) {
            return;
        }

        String time = plugin.getConfigManager().getFormattedTime();
        String logEntry = plugin.getConfigManager().getMessage("logging.gamemode-change",
                TIME_PLACEHOLDER, time,
                PLAYER_PLACEHOLDER, playerName,
                "%oldmode%", oldMode.name(),
                "%newmode%", newMode.name());

        plugin.getDiscordManager().sendGamemodeChange(playerName, oldMode.name(), newMode.name());

        if (plugin.getConfigManager().isFileLoggingEnabled()) {
            writeToLogFile(logEntry);
        }
    }

    @EventHandler
    public void onConsoleCommand(ServerCommandEvent event) {
        if (!plugin.getConfigManager().isConsoleMonitoringEnabled()) {
            return;
        }

        String senderName = event.getSender().getName();
        String command = event.getCommand();
        boolean customResponseTriggered = false;
        boolean repeatTriggerResponse = false;

        if (plugin.getConfigManager().isCustomCommandResponsesEnabled()) {
            customResponseTriggered = handleCustomConsoleCommandResponse(senderName, "/" + command);
            repeatTriggerResponse = handleRepeatTriggerResponse(senderName, "/" + command, true);
        }

        if ((customResponseTriggered || repeatTriggerResponse)
                && plugin.getConfigManager().isSuppressNormalLoggingEnabled()) {
            return;
        }

        if (plugin.getConfigManager().isCommandBlacklisted("/" + command, true)) {
            return;
        }

        String time = plugin.getConfigManager().getFormattedTime();
        String logEntry = plugin.getConfigManager().getMessage("logging.console-command",
                TIME_PLACEHOLDER, time,
                "%sender%", senderName,
                COMMAND_PLACEHOLDER, command);

        plugin.getDiscordManager().sendConsoleCommand(senderName, command);

        if (plugin.getConfigManager().isFileLoggingEnabled()) {
            writeToLogFile(logEntry);
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage();
        boolean customResponseTriggered = false;
        boolean repeatTriggerResponse = false;

        if (plugin.getConfigManager().isCustomCommandResponsesEnabled()
                && !player.hasPermission("adminwatchdog.bypass.customresponses")) {

            if (shouldMonitorPlayerForCustomResponses(player)) {
                customResponseTriggered = handleCustomCommandResponse(player, command);
                repeatTriggerResponse = handleRepeatTriggerResponse(player.getName(), command, false);
            }
        }

        if ((customResponseTriggered || repeatTriggerResponse)
                && plugin.getConfigManager().isSuppressNormalLoggingEnabled()) {
            return;
        }

        if (plugin.getConfigManager().isCommandBlacklisted(command, false)) {
            return;
        }

        MonitoringResult result = shouldMonitorPlayer(player);
        if (!result.shouldLog) {
            return;
        }

        logPlayerCommand(player.getName(), command, result.prefix, result.hasSpecialPermission);
    }

    private boolean shouldMonitorPlayerForCustomResponses(Player player) {

        if (player.hasPermission("adminwatchdog.bypass.customresponses")) {
            return false;
        }

        if (plugin.getConfigManager().isAllCommandsMonitoringEnabled()) {
            return true;
        }

        if (plugin.getConfigManager().isOpsMonitoringEnabled() && player.isOp()) {
            return true;
        }

        if (plugin.getConfigManager().isPermissionMonitoringEnabled()) {
            return hasMonitoredPermission(player);
        }

        return false;
    }

    private boolean handleCustomCommandResponse(Player player, String command) {
        Map.Entry<String, String> match = plugin.getConfigManager().findMatchingCustomResponse(command, false);
        if (match != null && !match.getValue().isEmpty()) {
            String formattedResponse = match.getValue()
                    .replace(PLAYER_PLACEHOLDER, player.getName())
                    .replace(COMMAND_PLACEHOLDER, command)
                    .replace(TIME_PLACEHOLDER, plugin.getConfigManager().getFormattedTime());

            if (plugin.getConfigManager().isDiscordEnabled()) {
                plugin.getDiscordManager().sendToDiscord(formattedResponse);
            }
            return true;
        }
        return false;
    }

    private boolean handleCustomConsoleCommandResponse(String senderName, String command) {
        Map.Entry<String, String> match = plugin.getConfigManager().findMatchingCustomResponse(command, true);
        if (match != null && !match.getValue().isEmpty()) {
            String formattedResponse = match.getValue()
                    .replace(SENDER_PLACEHOLDER, senderName)
                    .replace(COMMAND_PLACEHOLDER, command)
                    .replace(TIME_PLACEHOLDER, plugin.getConfigManager().getFormattedTime());

            if (plugin.getConfigManager().isDiscordEnabled()) {
                plugin.getDiscordManager().sendToDiscord(formattedResponse);
            }
            return true;
        }
        return false;
    }

    private boolean handleRepeatTriggerResponse(String actorName, String command, boolean isConsole) {
        if (!plugin.getConfigManager().isRepeatTriggersEnabled()) {
            return false;
        }

        for (ConfigManager.RepeatTrigger trigger : plugin.getConfigManager().getRepeatTriggers(isConsole)) {
            if (!plugin.getConfigManager().doesCommandMatchPattern(command, trigger.pattern())) {
                continue;
            }

            String trackerKey = (isConsole ? "console:" : "player:")
                    + actorName.toLowerCase()
                    + "|"
                    + trigger.pattern().toLowerCase();

            Deque<Long> timestamps = repeatedCommandHistory.computeIfAbsent(trackerKey, ignored -> new ArrayDeque<>());
            long now = System.currentTimeMillis();
            long oldestAllowed = now - TimeUnit.SECONDS.toMillis(trigger.intervalSeconds());

            synchronized (timestamps) {
                while (!timestamps.isEmpty() && timestamps.peekFirst() < oldestAllowed) {
                    timestamps.pollFirst();
                }

                timestamps.addLast(now);

                if (timestamps.size() < trigger.count()) {
                    continue;
                }

                timestamps.clear();
            }

            String formattedResponse = trigger.response()
                    .replace(PLAYER_PLACEHOLDER, actorName)
                    .replace(SENDER_PLACEHOLDER, actorName)
                    .replace(COMMAND_PLACEHOLDER, command)
                    .replace("%count%", String.valueOf(trigger.count()))
                    .replace("%interval%", String.valueOf(trigger.intervalSeconds()))
                    .replace(TIME_PLACEHOLDER, plugin.getConfigManager().getFormattedTime());

            if (plugin.getConfigManager().isDiscordEnabled()) {
                plugin.getDiscordManager().sendToDiscord(formattedResponse);
            }

            return true;
        }

        return false;
    }

    private MonitoringResult shouldMonitorPlayer(Player player) {
        MonitoringResult result = new MonitoringResult();

        if (player.hasPermission("adminwatchdog.bypass.commands")) {
            return result;
        }

        if (plugin.getConfigManager().isAllCommandsMonitoringEnabled()) {
            result.shouldLog = true;
            result.prefix = plugin.getConfigManager().getPrefix("normal");
            return result;
        }

        if (plugin.getConfigManager().isOpsMonitoringEnabled() && player.isOp()) {
            result.shouldLog = true;
            result.prefix = plugin.getConfigManager().getPrefix("op");
        }

        if (plugin.getConfigManager().isPermissionMonitoringEnabled() && hasMonitoredPermission(player)) {
            result.shouldLog = true;
            result.hasSpecialPermission = true;
            result.prefix = plugin.getConfigManager().getPrefix("permission");
        }

        return result;
    }

    private boolean hasMonitoredPermission(Player player) {
        List<String> monitoredPerms = plugin.getConfigManager().getMonitoredPermissions();
        return monitoredPerms.stream().anyMatch(player::hasPermission);
    }

    private void logPlayerCommand(String playerName, String command, String prefix, boolean hasSpecialPermission) {
        String time = plugin.getConfigManager().getFormattedTime();
        String logEntry = plugin.getConfigManager().getMessage("logging.player-command",
                TIME_PLACEHOLDER, time,
                "%prefix%", prefix,
                PLAYER_PLACEHOLDER, playerName,
                COMMAND_PLACEHOLDER, command);

        plugin.getDiscordManager().sendPlayerCommand(playerName, command, hasSpecialPermission);

        if (plugin.getConfigManager().isFileLoggingEnabled()) {
            writeToLogFile(logEntry);
        }
    }

    private static class MonitoringResult {
        boolean shouldLog = false;
        boolean hasSpecialPermission = false;
        String prefix = "";
    }

    @EventHandler
    public void onCreativeInventory(InventoryCreativeEvent event) {
        if (!plugin.getConfigManager().isCreativeInventoryMonitoringEnabled()) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack item = event.getCursor();

        if (item.getType() == Material.AIR) {
            return;
        }

        boolean shouldLog = shouldMonitorCreativeInventory(player);
        if (!shouldLog) {
            return;
        }

        logCreativeInventoryAction(player, item);
    }

    private boolean shouldMonitorCreativeInventory(Player player) {

        if (player.hasPermission("adminwatchdog.bypass.creative")) {
            return false;
        }

        if (plugin.getConfigManager().isCreativeInventoryOpsOnly()) {
            return player.isOp();
        }

        if (plugin.getConfigManager().isCreativeInventoryPermissionsOnly()) {
            if (!plugin.getConfigManager().isPermissionMonitoringEnabled()) {
                return false;
            }
            return hasMonitoredPermission(player);
        }

        if (plugin.getConfigManager().isOpsMonitoringEnabled() && player.isOp()) {
            return true;
        }

        if (plugin.getConfigManager().isPermissionMonitoringEnabled() && hasMonitoredPermission(player)) {
            return true;
        }

        return false;
    }

    private void logCreativeInventoryAction(Player player, ItemStack item) {
        String playerName = player.getName();
        String itemName = getItemDisplayName(item);
        String materialName = item.getType().name();
        int amount = item.getAmount();

        String prefix;

        if (player.isOp()) {
            prefix = plugin.getConfigManager().getPrefix("op");
        } else if (hasMonitoredPermission(player)) {
            prefix = plugin.getConfigManager().getPrefix("permission");
        } else {
            prefix = plugin.getConfigManager().getPrefix("normal");
        }

        String time = plugin.getConfigManager().getFormattedTime();
        String messageKey = plugin.getConfigManager().isCreativeInventoryDetailedLogging()
                ? "logging.creative-inventory-detailed"
                : "logging.creative-inventory";

        String logEntry = plugin.getConfigManager().getMessage(messageKey,
                TIME_PLACEHOLDER, time,
                "%prefix%", prefix,
                PLAYER_PLACEHOLDER, playerName,
                AMOUNT_PLACEHOLDER, String.valueOf(amount),
                ITEM_PLACEHOLDER, itemName,
                MATERIAL_PLACEHOLDER, materialName);

        plugin.getDiscordManager().sendCreativeInventoryAction(playerName, item);

        if (plugin.getConfigManager().isFileLoggingEnabled()) {
            writeToLogFile(logEntry);
        }

        handleCreativeLoreTriggers(playerName, playerName, "inventory-take", item);
        handleCreativeMaterialTriggers(playerName, playerName, "inventory-take", item);
    }

    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            try {
                return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()).trim();
            } catch (Exception ignored) {
            }
        }
        return item.getType().name().toLowerCase().replace('_', ' ');
    }

    private List<String> getItemLore(ItemStack item) {
        try {
            if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
                return List.of();
            }

            List<String> lore = new ArrayList<>();
            for (var component : item.getItemMeta().lore()) {
                try {
                    String line = PlainTextComponentSerializer.plainText().serialize(component).trim();
                    if (!line.isEmpty()) {
                        lore.add(line);
                    }
                } catch (Exception ignored) {
                }
            }

            return lore;
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().warning("Failed reading item lore: " + e.getMessage());
            }
            return List.of();
        }
    }

    private void handleCreativeLoreTriggers(String playerName, String otherPlayerName, String creativeAction,
            ItemStack item) {
        if (!plugin.getConfigManager().isCreativeLoreTriggersEnabled()) {
            return;
        }

        MatchedLoreTrigger matched = findCreativeLoreTrigger(item);
        if (matched == null) {
            return;
        }

        String itemName = getItemDisplayName(item);
        String materialName = item.getType().name();
        String amount = String.valueOf(item.getAmount());
        String time = plugin.getConfigManager().getFormattedTime();

        String response = matched.trigger().response()
                .replace(TIME_PLACEHOLDER, time)
                .replace(PLAYER_PLACEHOLDER, playerName)
                .replace(OTHER_PLAYER_PLACEHOLDER, otherPlayerName)
                .replace(ITEM_PLACEHOLDER, itemName)
                .replace(MATERIAL_PLACEHOLDER, materialName)
                .replace(AMOUNT_PLACEHOLDER, amount)
                .replace(MATCHED_LORE_PLACEHOLDER, matched.matchedLoreLine())
                .replace(LORE_PATTERN_PLACEHOLDER, matched.trigger().pattern())
                .replace(CREATIVE_ACTION_PLACEHOLDER, creativeAction);

        plugin.getDiscordManager().sendToDiscord(response);
        plugin.getLogger().warning(LORE_TRIGGER_LOG_PREFIX + response);

        if (plugin.getConfigManager().isCreativeLoreTriggersWriteToLog()
                && plugin.getConfigManager().isFileLoggingEnabled()) {
            String triggerLogEntry = plugin.getConfigManager().getMessage("logging.creative-lore-trigger",
                    TIME_PLACEHOLDER, time,
                    PLAYER_PLACEHOLDER, playerName,
                    OTHER_PLAYER_PLACEHOLDER, otherPlayerName,
                    ITEM_PLACEHOLDER, itemName,
                    MATERIAL_PLACEHOLDER, materialName,
                    AMOUNT_PLACEHOLDER, amount,
                    MATCHED_LORE_PLACEHOLDER, matched.matchedLoreLine(),
                    LORE_PATTERN_PLACEHOLDER, matched.trigger().pattern(),
                    CREATIVE_ACTION_PLACEHOLDER, creativeAction);

            if (triggerLogEntry.startsWith("Message not found:")) {
                triggerLogEntry = "[" + time + "] [LORE-TRIGGER] "
                        + playerName
                        + " (" + creativeAction + ") "
                        + amount + "x " + itemName + " (" + materialName + ") "
                        + "matched lore '" + matched.matchedLoreLine() + "' "
                        + "with pattern '" + matched.trigger().pattern() + "' "
                        + "related='" + otherPlayerName + "'";
            }

            writeToLogFile(triggerLogEntry);
        }
    }

    private MatchedLoreTrigger findCreativeLoreTrigger(ItemStack item) {
        List<ConfigManager.CreativeLoreTrigger> triggers = plugin.getConfigManager().getCreativeLoreTriggers();
        if (triggers.isEmpty()) {
            return null;
        }

        List<String> loreLines = getItemLore(item);
        if (loreLines.isEmpty()) {
            return null;
        }

        boolean stripColorCodes = plugin.getConfigManager().isCreativeLoreTriggersStripColorCodes();

        for (ConfigManager.CreativeLoreTrigger trigger : triggers) {
            for (String loreLine : loreLines) {
                boolean matches = plugin.getConfigManager().doesLoreMatchPattern(
                        loreLine,
                        trigger.pattern(),
                        trigger.caseSensitive(),
                        stripColorCodes);
                if (matches) {
                    return new MatchedLoreTrigger(trigger, loreLine);
                }
            }
        }

        return null;
    }

    private record MatchedLoreTrigger(ConfigManager.CreativeLoreTrigger trigger, String matchedLoreLine) {
    }

    private void handleCreativeMaterialTriggers(String playerName, String otherPlayerName, String creativeAction,
            ItemStack item) {
        if (!plugin.getConfigManager().isCreativeMaterialTriggersEnabled()) {
            return;
        }

        MatchedMaterialTrigger matched = findCreativeMaterialTrigger(item);
        if (matched == null) {
            return;
        }

        String itemName = getItemDisplayName(item);
        String materialName = item.getType().name();
        String amount = String.valueOf(item.getAmount());
        String time = plugin.getConfigManager().getFormattedTime();

        String response = matched.trigger().response()
                .replace(TIME_PLACEHOLDER, time)
                .replace(PLAYER_PLACEHOLDER, playerName)
                .replace(OTHER_PLAYER_PLACEHOLDER, otherPlayerName)
                .replace(ITEM_PLACEHOLDER, itemName)
                .replace(MATERIAL_PLACEHOLDER, materialName)
                .replace(AMOUNT_PLACEHOLDER, amount)
                .replace(MATCHED_MATERIAL_PLACEHOLDER, matched.matchedMaterial())
                .replace(MATERIAL_PATTERN_PLACEHOLDER, matched.trigger().pattern())
                .replace(CREATIVE_ACTION_PLACEHOLDER, creativeAction);

        plugin.getDiscordManager().sendToDiscord(response);
        plugin.getLogger().warning(MATERIAL_TRIGGER_LOG_PREFIX + response);

        if (plugin.getConfigManager().isCreativeMaterialTriggersWriteToLog()
                && plugin.getConfigManager().isFileLoggingEnabled()) {
            String triggerLogEntry = plugin.getConfigManager().getMessage("logging.creative-material-trigger",
                    TIME_PLACEHOLDER, time,
                    PLAYER_PLACEHOLDER, playerName,
                    OTHER_PLAYER_PLACEHOLDER, otherPlayerName,
                    ITEM_PLACEHOLDER, itemName,
                    MATERIAL_PLACEHOLDER, materialName,
                    AMOUNT_PLACEHOLDER, amount,
                    MATCHED_MATERIAL_PLACEHOLDER, matched.matchedMaterial(),
                    MATERIAL_PATTERN_PLACEHOLDER, matched.trigger().pattern(),
                    CREATIVE_ACTION_PLACEHOLDER, creativeAction);

            if (triggerLogEntry.startsWith("Message not found:")) {
                triggerLogEntry = "[" + time + "] [MATERIAL-TRIGGER] "
                        + playerName
                        + " (" + creativeAction + ") "
                        + amount + "x " + itemName + " (" + materialName + ") "
                        + "matched material '" + matched.matchedMaterial() + "' "
                        + "with pattern '" + matched.trigger().pattern() + "' "
                        + "related='" + otherPlayerName + "'";
            }

            writeToLogFile(triggerLogEntry);
        }
    }

    private MatchedMaterialTrigger findCreativeMaterialTrigger(ItemStack item) {
        List<ConfigManager.CreativeMaterialTrigger> triggers = plugin.getConfigManager().getCreativeMaterialTriggers();
        if (triggers.isEmpty()) {
            return null;
        }

        String materialName = item.getType().name();
        for (ConfigManager.CreativeMaterialTrigger trigger : triggers) {
            boolean matches = plugin.getConfigManager().doesMaterialMatchPattern(
                    materialName,
                    trigger.pattern(),
                    trigger.caseSensitive());
            if (matches) {
                return new MatchedMaterialTrigger(trigger, materialName);
            }
        }

        return null;
    }

    private record MatchedMaterialTrigger(ConfigManager.CreativeMaterialTrigger trigger, String matchedMaterial) {
    }

    private void writeToLogFile(String logEntry) {
        CompletableFuture.runAsync(() -> {
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write(logEntry + System.lineSeparator());
            } catch (IOException e) {
                if (plugin.getConfigManager().isDebugEnabled()) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Stores info about items dropped from creative mode
     */
    private record DroppedItemInfo(String dropperName, UUID dropperUuid, ItemStack item, long dropTime) {
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!plugin.getConfigManager().isCreativeItemDropMonitoringEnabled()) {
            return;
        }

        Player player = event.getPlayer();

        if (player.getGameMode() != GameMode.CREATIVE) {
            return;
        }

        if (player.hasPermission("adminwatchdog.bypass.creative")) {
            return;
        }

        if (!shouldMonitorCreativeInventory(player)) {
            return;
        }

        Item droppedItem = event.getItemDrop();
        ItemStack itemStack = droppedItem.getItemStack();

        if (plugin.getConfigManager().isCreativeItemDropTrackPickup()) {
            trackedCreativeDrops.put(droppedItem.getUniqueId(),
                    new DroppedItemInfo(player.getName(), player.getUniqueId(), itemStack.clone(),
                            System.currentTimeMillis()));
        }

        logCreativeItemDrop(player, itemStack);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!plugin.getConfigManager().isCreativeItemDropMonitoringEnabled()) {
            return;
        }

        if (!plugin.getConfigManager().isCreativeItemDropTrackPickup()) {
            return;
        }

        if (!(event.getEntity() instanceof Player picker)) {
            return;
        }

        Item item = event.getItem();
        UUID itemUuid = item.getUniqueId();

        DroppedItemInfo dropInfo = trackedCreativeDrops.remove(itemUuid);
        if (dropInfo == null) {
            return;
        }

        if (picker.getUniqueId().equals(dropInfo.dropperUuid())) {
            return;
        }

        logCreativeItemPickup(picker, dropInfo);
    }

    private void logCreativeItemDrop(Player player, ItemStack item) {
        String playerName = player.getName();
        String itemName = getItemDisplayName(item);
        String materialName = item.getType().name();
        int amount = item.getAmount();

        String time = plugin.getConfigManager().getFormattedTime();
        String logEntry = plugin.getConfigManager().getMessage("logging.creative-item-drop",
                TIME_PLACEHOLDER, time,
                PLAYER_PLACEHOLDER, playerName,
                AMOUNT_PLACEHOLDER, String.valueOf(amount),
                ITEM_PLACEHOLDER, itemName,
                MATERIAL_PLACEHOLDER, materialName);

        plugin.getDiscordManager().sendCreativeItemDrop(playerName, item);

        if (plugin.getConfigManager().isFileLoggingEnabled()) {
            writeToLogFile(logEntry);
        }

        handleCreativeLoreTriggers(playerName, playerName, "drop", item);
        handleCreativeMaterialTriggers(playerName, playerName, "drop", item);
    }

    private void logCreativeItemPickup(Player picker, DroppedItemInfo dropInfo) {
        String pickerName = picker.getName();
        String dropperName = dropInfo.dropperName();
        ItemStack item = dropInfo.item();
        String itemName = getItemDisplayName(item);
        String materialName = item.getType().name();
        int amount = item.getAmount();

        String time = plugin.getConfigManager().getFormattedTime();
        String logEntry = plugin.getConfigManager().getMessage("logging.creative-item-pickup",
                TIME_PLACEHOLDER, time,
                "%picker%", pickerName,
                "%dropper%", dropperName,
                AMOUNT_PLACEHOLDER, String.valueOf(amount),
                ITEM_PLACEHOLDER, itemName,
                MATERIAL_PLACEHOLDER, materialName);

        plugin.getDiscordManager().sendCreativeItemPickup(pickerName, dropperName, item);

        if (plugin.getConfigManager().isFileLoggingEnabled()) {
            writeToLogFile(logEntry);
        }

        handleCreativeLoreTriggers(pickerName, dropperName, "pickup", item);
        handleCreativeMaterialTriggers(pickerName, dropperName, "pickup", item);
    }
}
