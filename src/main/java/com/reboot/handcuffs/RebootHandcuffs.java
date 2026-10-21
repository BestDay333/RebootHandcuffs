package com.reboot.handcuffs;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class RebootHandcuffs extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static final String PERM_ADMIN = "reboot.admin";

    private DataManager dataManager;
    private CuffTask cuffTask;
    private PluginSettings settings;

    /** Отложенные действия (заковать/снять) по цели. */
    private final Map<UUID, BukkitTask> pendingActions = new HashMap<>();
    /** Кулдаун между использованиями предмета по одной цели (простой анти-спам). */
    private final CooldownTracker cooldowns = new CooldownTracker(System::currentTimeMillis);

    @Override
    public void onEnable() {
        saveDefaultConfig();

        settings = new PluginSettings(this);
        settings.reload();
        cooldowns.setCooldownMillis(settings.cooldownMillis());

        dataManager = new DataManager(this);

        PlayerListener playerListener = new PlayerListener(this, dataManager, settings);
        getServer().getPluginManager().registerEvents(playerListener, this);

        for (String name : new String[]{"cuff", "uncuff", "cuffgive", "cufflist", "cuffreload"}) {
            var description = getCommand(name);
            if (description == null) {
                getLogger().severe("Command '" + name + "' is not declared in plugin.yml!");
                continue;
            }
            description.setExecutor(this);
            description.setTabCompleter(this);
        }

        cuffTask = new CuffTask(this, dataManager, settings);
        cuffTask.runTaskTimer(this, 0L, 1L);

        getLogger().info("RebootHandcuffs v" + getPluginMeta().getVersion()
                + " enabled (Paper 26.3), cuffed on load: " + dataManager.count());
    }

    @Override
    public void onDisable() {
        cancelAllPendingActions();
        if (cuffTask != null) {
            cuffTask.cancel();
        }
        if (dataManager != null) {
            dataManager.shutdown();
        }
        getLogger().info("RebootHandcuffs disabled!");
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public PluginSettings getSettings() {
        return settings;
    }

    // ------------------------------------------------------------------
    //  Отложенные действия
    // ------------------------------------------------------------------

    /**
     * Кулдаун между использованиями предмета по одной цели.
     *
     * @return true, если действие разрешено
     */
    public boolean tryStartAction(UUID targetId) {
        return cooldowns.tryStart(targetId);
    }

    /** Планирует заковывание/снятие через настройку cuff.delay-ticks. */
    public void scheduleToggle(Player victim, UUID judgeId, String judgeName, boolean cuff) {
        UUID victimId = victim.getUniqueId();
        cancelPendingAction(victimId);

        long delay = Math.max(1L, cuff ? settings.cuffDelay() : settings.uncuffDelay());
        BukkitTask task = getServer().getScheduler().runTaskLater(this, () -> {
            pendingActions.remove(victimId);
            if (!victim.isOnline()) {
                return;
            }
            if (cuff) {
                applyCuff(victim, judgeId, judgeName);
            } else {
                applyUncuff(victim, judgeId, judgeName, false);
            }
        }, delay);

        pendingActions.put(victimId, task);
    }

    public void cancelPendingAction(UUID victimId) {
        BukkitTask task = pendingActions.remove(victimId);
        if (task != null) {
            task.cancel();
        }
    }

    private void cancelAllPendingActions() {
        for (BukkitTask task : pendingActions.values()) {
            task.cancel();
        }
        pendingActions.clear();
    }

    private void applyCuff(Player victim, UUID judgeId, String judgeName) {
        if (!dataManager.handcuff(victim, judgeId, judgeName)) {
            return;
        }
        settings.send(victim, "msg-cuff", "&c✖ ВЫ СВЯЗАНЫ ✖");
        if (judgeId != null) {
            Player judge = Bukkit.getPlayer(judgeId);
            if (judge != null) {
                settings.send(judge, "msg-judge-success", "&aИгрок закован", "%player%", victim.getName());
            }
        }
        getLogger().info("Handcuffed " + victim.getName() + " by " + judgeName);
    }

    private void applyUncuff(Player victim, UUID actorId, String actorName, boolean byAdmin) {
        if (!dataManager.unhandcuff(victim.getUniqueId())) {
            return;
        }

        if (byAdmin) {
            settings.send(victim, "msg-uncuff-victim", "&aАдминистратор снял с вас наручники");
        } else {
            settings.send(victim, "msg-uncuff", "&aНаручники сняты");
        }

        Player actor = actorId == null ? null : Bukkit.getPlayer(actorId);
        if (actor != null) {
            if (byAdmin) {
                settings.sendChat(actor, "msg-uncuff-admin", "&aНаручники сняты с игрока %player%",
                        "%player%", victim.getName());
            } else {
                settings.send(actor, "msg-uncuff", "&aНаручники сняты");
            }
        }
        getLogger().info("Unhandcuffed " + victim.getName() + " by " + actorName);
    }

    // ------------------------------------------------------------------
    //  Команды
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "cuff" -> handleCuff(sender, args);
            case "uncuff" -> handleUncuff(sender, args);
            case "cuffgive" -> handleGive(sender, args);
            case "cufflist" -> handleList(sender);
            case "cuffreload" -> handleReload(sender);
            default -> false;
        };
    }

    private boolean handleCuff(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 1) {
            settings.sendChat(sender, "msg-usage-cuff", "&cИспользование: /cuff <игрок>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            settings.sendChat(sender, "msg-player-not-found", "&cИгрок не найден или оффлайн!");
            return true;
        }
        if (dataManager.isHandcuffed(target.getUniqueId())) {
            settings.sendChat(sender, "msg-already-cuffed", "&cИгрок уже закован в наручники!");
            return true;
        }

        UUID judgeId = sender instanceof Player player ? player.getUniqueId() : null;
        scheduleToggle(target, judgeId, sender.getName(), true);
        return true;
    }

    private boolean handleUncuff(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 1) {
            settings.sendChat(sender, "msg-usage-uncuff", "&cИспользование: /uncuff <игрок>");
            return true;
        }

        Player online = Bukkit.getPlayerExact(args[0]);
        if (online != null) {
            UUID victimId = online.getUniqueId();
            if (!dataManager.isHandcuffed(victimId)) {
                settings.sendChat(sender, "msg-not-cuffed", "&cИгрок не закован в наручники!");
                return true;
            }
            UUID adminId = sender instanceof Player player ? player.getUniqueId() : null;
            applyUncuff(online, adminId, sender.getName(), true);
            return true;
        }

        // Оффлайн: снимаем наручники, предметы вернутся игроку при следующем входе.
        CuffRecord record = findCuffedRecord(args[0]);
        if (record == null) {
            settings.sendChat(sender, "msg-player-not-found", "&cИгрок не найден или оффлайн!");
            return true;
        }

        if (dataManager.unhandcuff(record.getVictim())) {
            settings.sendChat(sender, "msg-uncuff-admin", "&aНаручники сняты с игрока %player%",
                    "%player%", record.getVictimName());
            getLogger().info("Unhandcuffed offline player " + record.getVictimName()
                    + " by " + sender.getName());
        }
        return true;
    }

    private CuffRecord findCuffedRecord(String name) {
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        if (offline != null) {
            CuffRecord record = dataManager.getRecord(offline.getUniqueId());
            if (record != null) {
                return record;
            }
        }
        return dataManager.findByName(name);
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }

        Player target;
        if (args.length >= 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                settings.sendChat(sender, "msg-player-not-found", "&cИгрок не найден или оффлайн!");
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            settings.sendChat(sender, "msg-usage-give", "&cИспользование: /cuffgive [игрок] [количество]");
            return true;
        }

        int amount = 1;
        if (args.length >= 2) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[1].trim())));
            } catch (NumberFormatException ex) {
                settings.sendChat(sender, "msg-usage-give", "&cИспользование: /cuffgive [игрок] [количество]");
                return true;
            }
        }

        ItemStack item = settings.createCuffItem(amount);
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
        leftovers.values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));

        settings.sendChat(sender, "msg-given", "&aВыдано наручников: %amount% шт. игроку %player%",
                "%amount%", String.valueOf(amount), "%player%", target.getName());
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return true;
        }

        Collection<CuffRecord> records = dataManager.getRecords();
        if (records.isEmpty()) {
            settings.sendChat(sender, "msg-list-empty", "&7Сейчас нет закованных игроков");
            return true;
        }

        // Сначала самые свежие заковывания - так список удобно читать.
        List<CuffRecord> sorted = new ArrayList<>(records);
        sorted.sort(Comparator.comparingLong(CuffRecord::getCuffedAt).reversed());

        settings.sendChat(sender, "msg-list-header", "&7Закованные игроки (&f%count%&7):",
                "%count%", String.valueOf(sorted.size()));
        for (CuffRecord record : sorted) {
            boolean online = Bukkit.getPlayer(record.getVictim()) != null;
            settings.sendChat(sender,
                    online ? "msg-list-entry" : "msg-list-entry-offline",
                    "&8- &f%victim% &7<- &f%judge% &8(%time%)",
                    "%victim%", record.getVictimName(),
                    "%judge%", record.getJudgeName(),
                    "%time%", Maths.formatDuration(System.currentTimeMillis() - record.getCuffedAt()));
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return true;
        }
        settings.reload();
        cooldowns.setCooldownMillis(settings.cooldownMillis());
        settings.sendChat(sender, "msg-reloaded", "&aКонфигурация RebootHandcuffs перезагружена");
        getLogger().info("Configuration reloaded by " + sender.getName());
        return true;
    }

    /** @return true, если у отправителя есть права (и ничего не отправлялось) */
    private boolean requireAdmin(CommandSender sender) {
        if (hasAdminPermission(sender)) {
            return true;
        }
        settings.sendChat(sender, "msg-no-permission", "&cУ вас нет прав на использование этой команды!");
        return false;
    }

    private static boolean hasAdminPermission(CommandSender sender) {
        return sender.isOp() || sender.hasPermission(PERM_ADMIN);
    }

    // ------------------------------------------------------------------
    //  Tab-complete
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (!hasAdminPermission(sender) || args.length != 1) {
            return result;
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "cuff" -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!dataManager.isHandcuffed(player.getUniqueId())) {
                        match(result, player.getName(), prefix);
                    }
                }
            }
            case "uncuff" -> {
                for (CuffRecord record : dataManager.getRecords()) {
                    match(result, record.getVictimName(), prefix);
                }
            }
            case "cuffgive" -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    match(result, player.getName(), prefix);
                }
            }
            default -> {
            }
        }
        return result;
    }

    private static void match(List<String> result, String value, String prefix) {
        if (value != null && value.toLowerCase(Locale.ROOT).startsWith(prefix)) {
            result.add(value);
        }
    }
}
