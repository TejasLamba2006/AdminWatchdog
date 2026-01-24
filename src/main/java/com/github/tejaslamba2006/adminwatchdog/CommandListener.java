package com.github.tejaslamba2006.adminwatchdog;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CommandListener implements Listener {

    private static final String TIME_PLACEHOLDER = "%time%";
    private static final String PLAYER_PLACEHOLDER = "%player%";
    private static final String COMMAND_PLACEHOLDER = "%command%";

    private final File logFile;
    private final AdminWatchdog plugin;

    // Track items dropped by creative mode players: Item UUID -> Dropper info
    private final Map<UUID, DroppedItemInfo> trackedCreativeDrops = new ConcurrentHashMap<>();

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

        // Start cleanup task for expired tracked drops
        startDropCleanupTask();
    }

    private void startDropCleanupTask() {
        // Clean up expired drops every minute
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            int trackingDuration = plugin.getConfigManager().getCreativeItemDropTrackingDuration();
            long expiryTime = TimeUnit.SECONDS.toMillis(trackingDuration);

            trackedCreativeDrops.entrySet().removeIf(entry -> (now - entry.getValue().dropTime()) > expiryTime);
        }, 20L * 60, 20L * 60); // Run every minute
    }

    @EventHandler
    public void onGamemodeChange(PlayerGameModeChangeEvent event) {
        if (!plugin.getConfigManager().isGamemodeMonitoringEnabled()) {
            return;
        }

        Player player = event.getPlayer();

        // Check bypass permission
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

        if (plugin.getConfigManager().isCustomCommandResponsesEnabled()) {
            handleCustomConsoleCommandResponse(senderName, "/" + command);
        }

        if (plugin.getConfigManager().isCommandBlacklisted("/" + command)) {
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

        // Check bypass permission for custom responses
        if (plugin.getConfigManager().isCustomCommandResponsesEnabled()
                && !player.hasPermission("adminwatchdog.bypass.customresponses")) {
            // Only trigger custom responses if player should be monitored
            if (shouldMonitorPlayerForCustomResponses(player)) {
                handleCustomCommandResponse(player, command);
            }
        }

        if (plugin.getConfigManager().isCommandBlacklisted(command)) {
            return;
        }

        MonitoringResult result = shouldMonitorPlayer(player);
        if (!result.shouldLog) {
            return;
        }

        logPlayerCommand(player.getName(), command, result.prefix, result.hasSpecialPermission);
    }

    private boolean shouldMonitorPlayerForCustomResponses(Player player) {
        // Check if player has bypass permission
        if (player.hasPermission("adminwatchdog.bypass.customresponses")) {
            return false;
        }

        // If all-commands monitoring is enabled, monitor everyone
        if (plugin.getConfigManager().isAllCommandsMonitoringEnabled()) {
            return true;
        }

        // Check if player is op and ops monitoring is enabled
        if (plugin.getConfigManager().isOpsMonitoringEnabled() && player.isOp()) {
            return true;
        }

        // Check if player has monitored permissions
        if (plugin.getConfigManager().isPermissionMonitoringEnabled()) {
            return hasMonitoredPermission(player);
        }

        return false;
    }

    private void handleCustomCommandResponse(Player player, String command) {
        Map.Entry<String, String> match = plugin.getConfigManager().findMatchingCustomResponse(command);
        if (match != null && !match.getValue().isEmpty()) {
            String formattedResponse = match.getValue()
                    .replace(PLAYER_PLACEHOLDER, player.getName())
                    .replace(COMMAND_PLACEHOLDER, command)
                    .replace(TIME_PLACEHOLDER, plugin.getConfigManager().getFormattedTime());

            if (plugin.getConfigManager().isDiscordEnabled()) {
                plugin.getDiscordManager().sendToDiscord(formattedResponse);
            }
        }
    }

    private void handleCustomConsoleCommandResponse(String senderName, String command) {
        Map.Entry<String, String> match = plugin.getConfigManager().findMatchingCustomResponse(command);
        if (match != null && !match.getValue().isEmpty()) {
            String formattedResponse = match.getValue()
                    .replace("%sender%", senderName)
                    .replace(COMMAND_PLACEHOLDER, command)
                    .replace(TIME_PLACEHOLDER, plugin.getConfigManager().getFormattedTime());

            if (plugin.getConfigManager().isDiscordEnabled()) {
                plugin.getDiscordManager().sendToDiscord(formattedResponse);
            }
        }
    }

    private MonitoringResult shouldMonitorPlayer(Player player) {
        MonitoringResult result = new MonitoringResult();

        // Check bypass permission
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
        // Check bypass permission first
        if (player.hasPermission("adminwatchdog.bypass.creative")) {
            return false;
        }

        // If ops-only is enabled, only monitor ops
        if (plugin.getConfigManager().isCreativeInventoryOpsOnly()) {
            return player.isOp();
        }

        // If permissions-only is enabled, only monitor players with monitored
        // permissions
        if (plugin.getConfigManager().isCreativeInventoryPermissionsOnly()) {
            if (!plugin.getConfigManager().isPermissionMonitoringEnabled()) {
                return false;
            }
            return hasMonitoredPermission(player);
        }

        // Default behavior: respect monitoring settings (ops and/or permission holders)
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
                "%amount%", String.valueOf(amount),
                "%item%", itemName,
                "%material%", materialName);

        plugin.getDiscordManager().sendCreativeInventoryAction(playerName, item);

        if (plugin.getConfigManager().isFileLoggingEnabled()) {
            writeToLogFile(logEntry);
        }
    }

    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().displayName().toString();
        }
        return item.getType().name().toLowerCase().replace('_', ' ');
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

    // ==================== Creative Item Drop Tracking ====================

    /**
     * Record to store information about dropped items from creative mode
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

        // Only track drops from creative mode players
        if (player.getGameMode() != GameMode.CREATIVE) {
            return;
        }

        // Check bypass permission
        if (player.hasPermission("adminwatchdog.bypass.creative")) {
            return;
        }

        // Check if player should be monitored
        if (!shouldMonitorCreativeInventory(player)) {
            return;
        }

        Item droppedItem = event.getItemDrop();
        ItemStack itemStack = droppedItem.getItemStack();

        // Store the drop info for pickup tracking
        if (plugin.getConfigManager().isCreativeItemDropTrackPickup()) {
            trackedCreativeDrops.put(droppedItem.getUniqueId(),
                    new DroppedItemInfo(player.getName(), player.getUniqueId(), itemStack.clone(),
                            System.currentTimeMillis()));
        }

        // Log the drop
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

        // Only track player pickups
        if (!(event.getEntity() instanceof Player picker)) {
            return;
        }

        Item item = event.getItem();
        UUID itemUuid = item.getUniqueId();

        // Check if this was a tracked creative drop
        DroppedItemInfo dropInfo = trackedCreativeDrops.remove(itemUuid);
        if (dropInfo == null) {
            return; // Not a tracked creative drop
        }

        // Don't log if the same player picked it up
        if (picker.getUniqueId().equals(dropInfo.dropperUuid())) {
            return;
        }

        // Log the pickup
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
                "%amount%", String.valueOf(amount),
                "%item%", itemName,
                "%material%", materialName);

        plugin.getDiscordManager().sendCreativeItemDrop(playerName, item);

        if (plugin.getConfigManager().isFileLoggingEnabled()) {
            writeToLogFile(logEntry);
        }
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
                "%amount%", String.valueOf(amount),
                "%item%", itemName,
                "%material%", materialName);

        plugin.getDiscordManager().sendCreativeItemPickup(pickerName, dropperName, item);

        if (plugin.getConfigManager().isFileLoggingEnabled()) {
            writeToLogFile(logEntry);
        }
    }
}
