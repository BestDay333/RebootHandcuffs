package com.reboot.handcuffs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

public class CuffTask extends BukkitRunnable {

    private final RebootHandcuffs plugin;
    private final DataManager dataManager;

    public CuffTask(RebootHandcuffs plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    @Override
    public void run() {
        Map<UUID, UUID> handcuffedPlayers = dataManager.getHandcuffedPlayers();

        for (Map.Entry<UUID, UUID> entry : handcuffedPlayers.entrySet()) {
            UUID victimUuid = entry.getKey();
            UUID judgeUuid = entry.getValue();

            Player victim = Bukkit.getPlayer(victimUuid);
            Player judge = Bukkit.getPlayer(judgeUuid);

            if (victim == null) {
                continue; // Victim is offline
            }

            if (judge == null) {
                // Judge is offline - victim stays in place (handled by PlayerMoveEvent)
                // Just skip the pulling logic
                continue;
            }

            Location victimLoc = victim.getLocation();
            Location judgeLoc = judge.getLocation();

            double distance = victimLoc.distance(judgeLoc);
            double maxDistance = plugin.getConfig().getDouble("max-distance", 1.5);
            double pullStrength = plugin.getConfig().getDouble("pull-strength", 0.4);

            if (distance > maxDistance) {
                // Calculate direction vector to judge
                Vector direction = judgeLoc.toVector().subtract(victimLoc.toVector());
                direction.normalize();
                // Strong pull strength for better effect
                direction.multiply(pullStrength * 3.0);

                // Get victim's current velocity and preserve Y for gravity/jumping
                Vector currentVelocity = victim.getVelocity();
                
                // Apply horizontal pull while preserving vertical movement
                Vector newVelocity = new Vector(direction.getX(), currentVelocity.getY(), direction.getZ());
                victim.setVelocity(newVelocity);
                
                // Teleport assist if pull is not effective enough (distance still large)
                if (distance > maxDistance * 2.5) {
                    // Calculate target position closer to judge
                    Location targetLoc = judgeLoc.clone();
                    // Move target location slightly towards victim to avoid overlapping
                    Vector judgeToVictim = victimLoc.toVector().subtract(judgeLoc.toVector()).normalize().multiply(maxDistance);
                    targetLoc.add(judgeToVictim);
                    
                    // Keep victim's yaw/pitch
                    targetLoc.setYaw(victimLoc.getYaw());
                    targetLoc.setPitch(victimLoc.getPitch());
                    
                    // Teleport victim closer to judge
                    victim.teleport(targetLoc);
                }
            } else {
                // Within range - zero out X/Z velocity but preserve Y for gravity
                Vector currentVelocity = victim.getVelocity();
                victim.setVelocity(new Vector(0, currentVelocity.getY(), 0));
            }
        }
    }
}
