package com.reboot.handcuffs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
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

            // Draw visible leash particles between victim and judge
            drawLeashParticles(victimLoc, judgeLoc);

            if (distance > maxDistance) {
                // Calculate direction vector to judge
                Vector direction = judgeLoc.toVector().subtract(victimLoc.toVector());
                direction.normalize();
                // Increased pull strength for better effect
                direction.multiply(pullStrength * 2.5);

                // Player follows the judge in the air like on a leash
                // No Y restriction - victim flies with judge
                // Anti-kick: set velocity smoothly, no teleport spam

                victim.setVelocity(direction);
                
                // Additional teleport assist only if pull is not effective (very far)
                if (distance > maxDistance * 4) {
                    Location newLoc = victimLoc.clone();
                    newLoc.add(direction.clone().multiply(0.8));
                    // Match judge's Y level for flying
                    newLoc.setY(judgeLoc.getY() + (victimLoc.getY() - judgeLoc.getY()) * 0.3);
                    victim.teleport(newLoc);
                }
            } else {
                // Within range - smooth velocity adjustment, allow flying
                Vector currentVelocity = victim.getVelocity();
                Vector targetVelocity = judge.getVelocity().clone().multiply(0.8);
                victim.setVelocity(targetVelocity);
            }
        }
    }

    private void drawLeashParticles(Location victimLoc, Location judgeLoc) {
        // Draw particle line from victim to judge to simulate visible leash
        double distance = victimLoc.distance(judgeLoc);
        int particles = (int) Math.ceil(distance * 3);
        
        if (particles < 1) return;
        
        Vector step = judgeLoc.toVector().subtract(victimLoc.toVector()).divide(new Vector(particles, particles, particles));
        
        Location current = victimLoc.clone().add(0, 0.5, 0); // Start from victim's chest height
        
        for (int i = 0; i < particles; i++) {
            current.add(step);
            victimLoc.getWorld().spawnParticle(Particle.END_ROD, current, 1, 0, 0, 0, 0);
        }
    }
}
