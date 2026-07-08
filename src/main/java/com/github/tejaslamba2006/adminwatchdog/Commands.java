package com.github.tejaslamba2006.adminwatchdog;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class Commands implements TabExecutor {

    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "version", "v", "ver", "reload", "rl", "update", "checkupdate", "history", "hist");

    private final AdminWatchdog plugin;

    public Commands(AdminWatchdog plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {

        if (args.length == 0) {
            sender.sendMessage(plugin.getConfigManager().getMessageComponent("commands.usage"));
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "version", "v", "ver" -> {
                String version = plugin.getPluginMeta().getVersion();
                sender.sendMessage(plugin.getConfigManager().getMessageComponent(
                        "commands.version",
                        "%version%", version));
                return true;
            }
            case "reload", "rl" -> {
                if (!sender.hasPermission("adminwatchdog.reload")) {
                    sender.sendMessage(plugin.getConfigManager().getMessageComponent("commands.no-permission"));
                    return true;
                }

                try {
                    plugin.getConfigManager().reloadConfigs();
                    sender.sendMessage(plugin.getConfigManager().getMessageComponent("commands.reload-success"));
                } catch (Exception e) {
                    sender.sendMessage(plugin.getConfigManager().getMessageComponent("commands.reload-failed"));
                    plugin.getLogger().severe("Error reloading config: " + e.getMessage());
                }
                return true;
            }
            case "update", "checkupdate" -> {
                if (!sender.hasPermission("adminwatchdog.update.check")) {
                    sender.sendMessage(plugin.getConfigManager().getMessageComponent("commands.no-permission"));
                    return true;
                }

                sender.sendMessage(plugin.getConfigManager().getMessageComponent("update.check-start"));

                plugin.getUpdateChecker().checkForUpdatesSync().thenAccept(result -> {
                    if (result.hasError()) {
                    sendMessageSafely(sender, plugin.getConfigManager().getMessageComponent(
                                "update.check-failed",
                        "%error%", result.getError()));
                    } else if (result.isUpdateAvailable()) {
                    sendMessageSafely(sender, plugin.getConfigManager().getMessageComponent(
                                "update.available",
                                "%current%", result.getCurrentVersion(),
                        "%latest%", result.getLatestVersion()));
                    sendMessageSafely(sender, plugin.getConfigManager().getMessageComponent(
                                "update.download",
                        "%download%", result.getDownloadUrl()));
                    } else {
                    sendMessageSafely(sender, plugin.getConfigManager().getMessageComponent(
                                "update.up-to-date",
                        "%current%", result.getCurrentVersion()));
                    }
                }).exceptionally(ex -> {
                    sendMessageSafely(sender, plugin.getConfigManager().getMessageComponent(
                            "update.check-failed",
                            "%error%", ex.getMessage()));
                    return null;
                });

                return true;
            }
            case "history", "hist" -> {
                if (!sender.hasPermission("adminwatchdog.history")) {
                    sender.sendMessage(plugin.getConfigManager().getMessageComponent("commands.no-permission"));
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage(plugin.getConfigManager().getMessageComponent("history.usage"));
                    return true;
                }

                String targetPlayer = args[1];
                int limit = 10;
                if (args.length >= 3) {
                    try {
                        limit = Math.max(1, Math.min(50, Integer.parseInt(args[2])));
                    } catch (NumberFormatException ignored) {
                    }
                }

                plugin.getAuditLogStorage().getRecentEntries(targetPlayer, limit).thenAccept(entries -> {
                    if (entries.isEmpty()) {
                        sendMessageSafely(sender, plugin.getConfigManager().getMessageComponent(
                                "history.none", "%player%", targetPlayer));
                        return;
                    }

                    sendMessageSafely(sender, plugin.getConfigManager().getMessageComponent(
                            "history.header", "%player%", targetPlayer, "%count%", String.valueOf(entries.size())));

                    DateTimeFormatter timeFormat = DateTimeFormatter
                            .ofPattern(plugin.getConfig().getString("general.time-format", "yyyy-MM-dd HH:mm:ss"))
                            .withZone(ZoneId.systemDefault());

                    for (var entry : entries) {
                        String time = timeFormat.format(Instant.ofEpochMilli(entry.timestamp()));
                        sendMessageSafely(sender, plugin.getConfigManager().getMessageComponent(
                                "history.entry",
                                TIME_PLACEHOLDER, time,
                                "%type%", entry.type(),
                                "%detail%", entry.detail()));
                    }
                }).exceptionally(ex -> {
                    sendMessageSafely(sender, plugin.getConfigManager().getMessageComponent(
                            "history.failed", "%error%", ex.getMessage()));
                    return null;
                });

                return true;
            }
        }

        return false;
    }

    private static final String TIME_PLACEHOLDER = "%time%";

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            String current = args[0].toLowerCase();

            for (String option : SUB_COMMANDS) {
                if (option.startsWith(current)) {
                    result.add(option);
                }
            }
            return result;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("history") || args[0].equalsIgnoreCase("hist"))) {
            String current = args[1].toLowerCase();
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(current))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    private void sendMessageSafely(CommandSender sender, Component message) {
        if (sender instanceof Player player) {
            player.getScheduler().execute(plugin, () -> sender.sendMessage(message), null, 1L);
            return;
        }

        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> sender.sendMessage(message));
    }

}
