package com.github.tejaslamba2006.adminwatchdog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Commands implements TabExecutor {

    private final JavaPlugin plugin;
    private final AdminWatchdog adminWatchdog;

    public Commands(JavaPlugin plugin) {
        this.plugin = plugin;
        this.adminWatchdog = (AdminWatchdog) plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {

        if (args.length == 0) {
            sender.sendMessage(adminWatchdog.getConfigManager().getMessage("commands.usage"));
            return true;
        }

        String subcommand = args[0].toLowerCase();

        if (subcommand.equals("version") || subcommand.equals("v") || subcommand.equals("ver")) {
            String version = plugin.getPluginMeta().getVersion();
            String message = adminWatchdog.getConfigManager().getMessage("commands.version", "%version%", version);
            sender.sendMessage(message);
            return true;
        }

        if (subcommand.equals("reload") || subcommand.equals("rl")) {
            if (!sender.hasPermission("adminwatchdog.reload")) {
                sender.sendMessage(adminWatchdog.getConfigManager().getMessage("commands.no-permission"));
                return true;
            }

            try {
                adminWatchdog.getConfigManager().reloadConfigs();
                sender.sendMessage(adminWatchdog.getConfigManager().getMessage("commands.reload-success"));
            } catch (Exception e) {
                sender.sendMessage(adminWatchdog.getConfigManager().getMessage("commands.reload-failed"));
                adminWatchdog.getLogger().severe("Error reloading config: " + e.getMessage());
            }
            return true;
        }

        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = Arrays.asList("version", "v", "ver", "reload", "rl");
            List<String> result = new ArrayList<>();
            String current = args[0].toLowerCase();

            for (String option : completions) {
                if (option.startsWith(current)) {
                    result.add(option);
                }
            }
            return result;
        }

        return new ArrayList<>();
    }

}
