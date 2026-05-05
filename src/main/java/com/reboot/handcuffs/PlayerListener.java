package com.reboot.handcuffs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class PlayerListener implements Listener {

    private final RebootHandcuffs plugin;
    private final DataManager dataManager;
    private final LegacyComponentSerializer serializer;

    public PlayerListener(RebootHandcuffs plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.serializer = LegacyComponentSerializer.legacySection();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        // Check if event is already cancelled
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();

        // Check if using lead with "Наручники" name
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.LEAD) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }

        String displayName = meta.getDisplayName();
        if (displayName == null || !displayName.equalsIgnoreCase("Наручники")) {
            return;
        }

        // Check permission
        if (!hasPermission(player)) {
            return;
        }

        // Check if right-clicking on entity
        Entity clickedEntity = event.getRightClicked();
        if (!(clickedEntity instanceof Player target)) {
            return;
        }

        // Prevent default lead interaction
        event.setCancelled(true);

        // Check current state and perform action
        if (dataManager.isHandcuffed(target)) {
            // Unhandcuff - only if the clicking player has permission
            // Add delay before removing handcuffs
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (dataManager.isHandcuffed(target)) {
                    dataManager.unhandcuff(target);
                    String msg = plugin.getConfig().getString("msg-uncuff", "&aНаручники сняты");
                    Component component = serializer.deserialize(msg);
                    target.sendActionBar(component);
                    player.sendActionBar(component);
                    plugin.getLogger().info("Unhandcuffed " + target.getName() + " by " + player.getName());
                }
            }, 20L); // 1 second delay
        } else {
            // Handcuff - add a delay to prevent double-triggering and ensure server state is consistent
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // Double-check if still not handcuffed (in case of race condition)
                if (!dataManager.isHandcuffed(target)) {
                    dataManager.handcuff(target, player);
                    String cuffMsg = plugin.getConfig().getString("msg-cuff", "&c✖ ВЫ СВЯЗАНЫ ✖");
                    String successMsg = plugin.getConfig().getString("msg-judge-success", "&aИгрок закован");
                    target.sendActionBar(serializer.deserialize(cuffMsg));
                    player.sendActionBar(serializer.deserialize(successMsg));
                    plugin.getLogger().info("Handcuffed " + target.getName() + " by " + player.getName());
                }
            }, 15L); // 15 ticks delay (0.75 seconds)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!dataManager.isHandcuffed(player)) {
            return;
        }

        // Only allow head rotation (yaw/pitch), block position changes
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) {
            return;
        }

        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            // Block movement - teleport back
            event.setTo(from);
        }
        // Allow yaw/pitch changes (head rotation)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("restrictions.allow-break", false)) {
            if (dataManager.isHandcuffed(event.getPlayer())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("restrictions.allow-place", false)) {
            if (dataManager.isHandcuffed(event.getPlayer())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!dataManager.isHandcuffed(player)) {
            return;
        }

        // If victim shouldn't take damage
        if (!plugin.getConfig().getBoolean("restrictions.allow-damage", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Prevent handcuffed player from attacking
        if (event.getDamager() instanceof Player damager) {
            if (dataManager.isHandcuffed(damager)) {
                if (!plugin.getConfig().getBoolean("restrictions.allow-attack", false)) {
                    event.setCancelled(true);
                }
            }
        }

        // Prevent handcuffed player from receiving damage (already handled in EntityDamageEvent)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (dataManager.isHandcuffed(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (dataManager.isHandcuffed(player)) {
            String msg = plugin.getConfig().getString("msg-still-cuffed", "&c✖ ВЫ ВСЕ ЕЩЕ СВЯЗАНЫ ✖");
            player.sendActionBar(serializer.deserialize(msg));

            // Ensure hands are empty
            dataManager.restoreHandItemsOnLogin(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (dataManager.isHandcuffed(player)) {
            dataManager.restoreHandItemsOnLogout(player);
        }
    }

    private boolean hasPermission(Player player) {
        String permMode = plugin.getConfig().getString("permission", "op");

        if ("op".equalsIgnoreCase(permMode)) {
            return player.isOp();
        } else {
            return player.hasPermission(permMode);
        }
    }
}
