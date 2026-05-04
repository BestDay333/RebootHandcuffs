package com.reboot.handcuffs;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class RebootHandcuffs extends JavaPlugin {

    private DataManager dataManager;
    private PlayerListener playerListener;
    private CuffTask cuffTask;

    @Override
    public void onEnable() {
        // Save default config if not exists
        saveDefaultConfig();

        // Initialize DataManager (loads handcuffs.yml)
        dataManager = new DataManager(this);

        // Register listener
        playerListener = new PlayerListener(this, dataManager);
        Bukkit.getPluginManager().registerEvents(playerListener, this);

        // Start pull task (every 1 tick)
        cuffTask = new CuffTask(this, dataManager);
        cuffTask.runTaskTimer(this, 0L, 1L);

        getLogger().info("RebootHandcuffs enabled!");
    }

    @Override
    public void onDisable() {
        // Cancel task
        if (cuffTask != null) {
            cuffTask.cancel();
        }

        // Save data
        if (dataManager != null) {
            dataManager.saveHandcuffsData();
        }

        getLogger().info("RebootHandcuffs disabled!");
    }

    public DataManager getDataManager() {
        return dataManager;
    }
}
