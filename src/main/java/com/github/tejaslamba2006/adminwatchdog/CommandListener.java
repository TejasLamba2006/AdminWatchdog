package com.github.tejaslamba2006.adminwatchdog;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class CommandListener implements Listener {

    private static final String TIME_PLACEHOLDER = "%time%";
    private static final String PLAYER_PLACEHOLDER = "%player%";
    private static final String COMMAND_PLACEHOLDER = "%command%";

    private final File logFile;
    private final AdminWatchdog plugin;

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
    }

    @EventHandler
    public void onGamemodeChange(PlayerGameModeChangeEvent event) {
        if (!plugin.getConfigManager().isGamemodeMonitoringEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        String playerName = player.getName();
        GameMode newMode = event.getNewGameMode();
        GameMode oldMode = player.getGameMode();

        boolean shouldLog = false;

        if (plugin.getConfigManager().isOpsMonitoringEnabled() && player.isOp()) {
            shouldLog = true;
        }

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
        if (plugin.getConfigManager().isCustomCommandResponsesEnabled()) {
            handleCustomCommandResponse(player, command);
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

    private void handleCustomCommandResponse(Player player, String command) {
        String cleanCommand = command.split(" ")[0];
        if (plugin.getConfigManager().hasCustomCommandResponse(cleanCommand)) {
            String response = plugin.getConfigManager().getCustomCommandResponse(cleanCommand);
            if (!response.isEmpty()) {
                String formattedResponse = response
                        .replace(PLAYER_PLACEHOLDER, player.getName())
                        .replace(COMMAND_PLACEHOLDER, command)
                        .replace(TIME_PLACEHOLDER, plugin.getConfigManager().getFormattedTime());

                if (plugin.getConfigManager().isDiscordEnabled()) {
                    plugin.getDiscordManager().sendToDiscord(formattedResponse);
                }
            }
        }
    }

    private void handleCustomConsoleCommandResponse(String senderName, String command) {
        String cleanCommand = command.split(" ")[0];
        if (plugin.getConfigManager().hasCustomCommandResponse(cleanCommand)) {
            String response = plugin.getConfigManager().getCustomCommandResponse(cleanCommand);
            if (!response.isEmpty()) {
                String formattedResponse = response
                        .replace("%sender%", senderName)
                        .replace(COMMAND_PLACEHOLDER, command)
                        .replace(TIME_PLACEHOLDER, plugin.getConfigManager().getFormattedTime());

                if (plugin.getConfigManager().isDiscordEnabled()) {
                    plugin.getDiscordManager().sendToDiscord(formattedResponse);
                }
            }
        }
    }

    private MonitoringResult shouldMonitorPlayer(Player player) {
        MonitoringResult result = new MonitoringResult();

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

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCursor();

        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        boolean shouldLog = shouldMonitorCreativeInventory(player);
        if (!shouldLog) {
            return;
        }

        logCreativeInventoryAction(player, item);
    }

    private boolean shouldMonitorCreativeInventory(Player player) {
        if (plugin.getConfigManager().isCreativeInventoryOpsOnly() && !player.isOp()) {
            return false;
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

        return !plugin.getConfigManager().isCreativeInventoryOpsOnly() &&
                !plugin.getConfigManager().isCreativeInventoryPermissionsOnly();
    }

    private void logCreativeInventoryAction(Player player, ItemStack item) {
        String playerName = player.getName();
        String itemName = getItemDisplayName(item);
        String materialName = item.getType().name();
        int amount = item.getAmount();

        String prefix = "";

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
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(logEntry + System.lineSeparator());
        } catch (IOException e) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                e.printStackTrace();
            }
        }
    }
}
