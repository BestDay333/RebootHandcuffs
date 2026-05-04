package com.reboot.handcuffs;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class RebootHandcuffs extends JavaPlugin implements CommandExecutor, TabCompleter {

    private DataManager dataManager;
    private PlayerListener playerListener;
    private CuffTask cuffTask;
    private LegacyComponentSerializer serializer;

    @Override
    public void onEnable() {
        // Save default config if not exists
        saveDefaultConfig();

        // Initialize serializer
        this.serializer = LegacyComponentSerializer.legacySection();

        // Initialize DataManager (loads handcuffs.yml)
        dataManager = new DataManager(this);

        // Register listener
        playerListener = new PlayerListener(this, dataManager);
        Bukkit.getPluginManager().registerEvents(playerListener, this);

        // Register command
        getCommand("uncuff").setExecutor(this);
        getCommand("uncuff").setTabCompleter(this);
        getCommand("cuff").setExecutor(this);
        getCommand("cuff").setTabCompleter(this);

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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("uncuff")) {
            return handleUncuff(sender, args);
        } else if (command.getName().equalsIgnoreCase("cuff")) {
            return handleCuff(sender, args);
        }
        return false;
    }

    private boolean handleUncuff(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭту команду может использовать только игрок!");
            return true;
        }

        if (!player.isOp() && !player.hasPermission("reboot.admin")) {
            player.sendMessage("§cУ вас нет прав на использование этой команды!");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§cИспользование: /uncuff <игрок>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage("§cИгрок не найден или оффлайн!");
            return true;
        }

        if (!dataManager.isHandcuffed(target)) {
            player.sendMessage("§cИгрок не закован в наручники!");
            return true;
        }

        dataManager.unhandcuffByAdmin(target, player);
        return true;
    }

    private boolean handleCuff(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭту команду может использовать только игрок!");
            return true;
        }

        if (!player.isOp() && !player.hasPermission("reboot.admin")) {
            player.sendMessage("§cУ вас нет прав на использование этой команды!");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§cИспользование: /cuff <игрок>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage("§cИгрок не найден или оффлайн!");
            return true;
        }

        if (dataManager.isHandcuffed(target)) {
            player.sendMessage("§cИгрок уже закован в наручники!");
            return true;
        }

        dataManager.handcuff(target, player);
        String cuffMsg = getConfig().getString("msg-cuff", "&c✖ ВЫ СВЯЗАНЫ ✖");
        String successMsg = getConfig().getString("msg-judge-success", "&aИгрок закован");
        target.sendActionBar(serializer.deserialize(cuffMsg));
        player.sendActionBar(serializer.deserialize(successMsg));
        getLogger().info("Handcuffed " + target.getName() + " by admin " + player.getName());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (command.getName().equalsIgnoreCase("uncuff") && dataManager.isHandcuffed(player)) {
                    completions.add(player.getName());
                } else if (command.getName().equalsIgnoreCase("cuff") && !dataManager.isHandcuffed(player)) {
                    completions.add(player.getName());
                }
            }
            return completions;
        }
        return new ArrayList<>();
    }

    public PlayerListener getPlayerListener() {
        return playerListener;
    }

    public DataManager getDataManager() {
        return dataManager;
    }
}
