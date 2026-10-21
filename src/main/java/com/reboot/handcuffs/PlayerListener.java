package com.reboot.handcuffs;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Обработчики событий для закованных игроков.
 * Все проверки конфигурации вынесены в {@link PluginSettings} и выполняются
 * до обращения к карте наручников - это дешевле, чем наоборот.
 */
public final class PlayerListener implements Listener {

    private final RebootHandcuffs plugin;
    private final DataManager dataManager;
    private final PluginSettings settings;

    public PlayerListener(RebootHandcuffs plugin, DataManager dataManager, PluginSettings settings) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.settings = settings;
    }

    // ------------------------------------------------------------------
    //  Использование предмета наручников
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return;
        }

        ItemStack item = player.getInventory().getItem(hand);
        if (!settings.isCuffItem(item)) {
            return;
        }

        if (!(event.getRightClicked() instanceof Player target) || target.equals(player)) {
            return;
        }

        // Отключаем ванильный поводок в любом случае: иначе игрок без прав
        // привязывал бы жертву предметом LEAD без какой-либо механики плагина.
        event.setCancelled(true);

        if (!settings.hasPermission(player)) {
            // Уведомляем только самого игрока, никакой рассылки остальным.
            settings.send(player, "msg-no-permission-cuff",
                    "&cУ вас нет прав использовать наручники!");
            return;
        }

        if (!plugin.tryStartAction(target.getUniqueId())) {
            settings.send(player, "msg-cooldown", "&eСекундочка, повторите попытку чуть позже...");
            return;
        }

        boolean cuff = !dataManager.isHandcuffed(target);
        UUID judgeId = player.getUniqueId();
        plugin.scheduleToggle(target, judgeId, player.getName(), cuff);
    }

    // ------------------------------------------------------------------
    //  Движение
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (settings.allowMove()) {
            return;
        }

        var to = event.getTo();
        if (to == null) {
            return;
        }

        var from = event.getFrom();
        // Только поворот головы - не трогаем вообще (yaw/pitch сохраняется всегда).
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }

        CuffRecord record = dataManager.getRecord(event.getPlayer().getUniqueId());
        if (record == null) {
            return;
        }

        // Судьи нет рядом (или он в другом мире) - стоим на месте.
        UUID judgeId = record.getJudge();
        Player judge = judgeId == null ? null : Bukkit.getPlayer(judgeId);
        if (judge == null || judge.getWorld() != to.getWorld()) {
            Location frozen = from.clone();
            frozen.setYaw(to.getYaw());
            frozen.setPitch(to.getPitch());
            event.setTo(frozen);
            return;
        }

        // Жертва может двигаться только в радиусе max-distance от судьи ("поводок").
        // За границей радиуса позиция прижимается к границе сферы: направление
        // движения сохраняется, поэтому уходящего судью жертва плавно тянет
        // за собой, а не откатывает на старое место.
        double[] clamped = LeashMath.clampToLeash(
                to.getX(), to.getY(), to.getZ(),
                judge.getX(), judge.getY(), judge.getZ(),
                settings.maxDistance());
        if (clamped == null) {
            return;
        }

        event.setTo(new Location(to.getWorld(), clamped[0], clamped[1], clamped[2],
                to.getYaw(), to.getPitch()));
    }

    // ------------------------------------------------------------------
    //  Ограничения действий
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (settings.allowBreak()) {
            return;
        }
        if (dataManager.isHandcuffed(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (settings.allowPlace()) {
            return;
        }
        if (dataManager.isHandcuffed(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (settings.allowInventory()) {
            return;
        }
        if (event.getPlayer() instanceof Player player
                && dataManager.isHandcuffed(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (settings.allowDrop()) {
            return;
        }
        if (dataManager.isHandcuffed(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (settings.allowSwapHands()) {
            return;
        }
        if (dataManager.isHandcuffed(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (settings.allowInventory()) {
            return;
        }
        // Закованный не должен пополнять инвентарь с земли: руки у него всё равно пусты.
        if (event.getEntity() instanceof Player player
                && dataManager.isHandcuffed(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractBlock(PlayerInteractEvent event) {
        if (settings.allowInteract()) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (dataManager.isHandcuffed(event.getPlayer().getUniqueId())) {
            // Двери, кнопки, рычаги, кровати, сундуки и прочие механизмы.
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    //  Урон
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (settings.allowDamage()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!dataManager.isHandcuffed(player.getUniqueId())) {
            return;
        }
        // VOID/KILL не блокируем, иначе игрок может навсегда застрять в пустоте.
        if (settings.neverBlockDamage().contains(event.getCause())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (settings.allowAttack()) {
            return;
        }

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        if (dataManager.isHandcuffed(attacker.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Учитывает и прямой удар, и снаряды (лук, арбалет, тризубец). */
    private static Player resolveAttacker(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  Вход/выход
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        dataManager.handleJoin(player);
        if (dataManager.isHandcuffed(player.getUniqueId())) {
            settings.send(player, "msg-still-cuffed", "&c✖ ВЫ ВСЕ ЕЩЕ СВЯЗАНЫ ✖");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.cancelPendingAction(player.getUniqueId());
        dataManager.handleQuit(player);
    }
}
