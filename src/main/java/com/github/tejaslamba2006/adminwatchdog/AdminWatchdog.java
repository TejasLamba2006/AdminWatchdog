package com.github.tejaslamba2006.adminwatchdog;

import org.bukkit.plugin.java.JavaPlugin;

public class AdminWatchdog extends JavaPlugin {

    private static AdminWatchdog instance;
    private DiscordManager dm;
    private ConfigManager configManager;

    @Override
    public void onEnable() {

        saveDefaultConfig();
        saveResource("messages.yml", false);

        instance = this;
        configManager = new ConfigManager(this);
        dm = new DiscordManager(this);

        getServer().getPluginManager().registerEvents(new CommandListener(this), this);

        Commands commandHandler = new Commands(this);
        this.getCommand("adminwatchdog").setExecutor(commandHandler);
        this.getCommand("adminwatchdog").setTabCompleter(commandHandler);

        getLogger().info(configManager.getMessage("plugin.enabled"));
    }

    @Override
    public void onDisable() {
        getLogger().info(configManager.getMessage("plugin.disabled"));
    }

    public static AdminWatchdog getInstance() {
        return instance;
    }

    public DiscordManager getDiscord() {
        return dm;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}