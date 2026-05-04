package com.reboot.handcuffs;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DataManager {

    private final RebootHandcuffs plugin;
    private final File handcuffsFile;
    private FileConfiguration handcuffsConfig;

    // Map<VictimUUID, JudgeUUID>
    private final Map<UUID, UUID> handcuffedPlayers;

    // Map<VictimUUID, ItemStack[]> - stores [MainHand, OffHand]
    private final Map<UUID, ItemStack[]> savedHands;

    public DataManager(RebootHandcuffs plugin) {
        this.plugin = plugin;
        this.handcuffedPlayers = new HashMap<>();
        this.savedHands = new HashMap<>();

        this.handcuffsFile = new File(plugin.getDataFolder(), "handcuffs.yml");
        loadHandcuffsData();
    }

    public Map<UUID, UUID> getHandcuffedPlayers() {
        return handcuffedPlayers;
    }

    public Map<UUID, ItemStack[]> getSavedHands() {
        return savedHands;
    }

    public boolean isHandcuffed(Player player) {
        return handcuffedPlayers.containsKey(player.getUniqueId());
    }

    public UUID getJudge(Player victim) {
        return handcuffedPlayers.get(victim.getUniqueId());
    }

    public void handcuff(Player victim, Player judge) {
        // Check if already handcuffed to prevent double-toggling
        if (handcuffedPlayers.containsKey(victim.getUniqueId())) {
            return;
        }

        handcuffedPlayers.put(victim.getUniqueId(), judge.getUniqueId());

        // Save current hand items
        ItemStack[] hands = new ItemStack[2];
        hands[0] = victim.getInventory().getItemInMainHand().clone();
        hands[1] = victim.getInventory().getItemInOffHand().clone();
        savedHands.put(victim.getUniqueId(), hands);

        // Clear hands
        victim.getInventory().setItemInMainHand(null);
        victim.getInventory().setItemInOffHand(null);

        saveHandcuffsData();
    }

    public void unhandcuff(Player victim) {
        UUID judgeUuid = handcuffedPlayers.remove(victim.getUniqueId());

        // Restore hand items
        ItemStack[] hands = savedHands.remove(victim.getUniqueId());
        if (hands != null) {
            if (hands[0] != null) {
                victim.getInventory().setItemInMainHand(hands[0]);
            }
            if (hands[1] != null) {
                victim.getInventory().setItemInOffHand(hands[1]);
            }
        }

        saveHandcuffsData();
    }

    public void saveHandcuffsData() {
        FileConfiguration config = new YamlConfiguration();

        for (Map.Entry<UUID, UUID> entry : handcuffedPlayers.entrySet()) {
            String victimKey = entry.getKey().toString();
            config.set(victimKey + ".judge", entry.getValue().toString());
            
            // Save hand items
            ItemStack[] hands = savedHands.get(entry.getKey());
            if (hands != null) {
                config.set(victimKey + ".mainHand", hands[0]);
                config.set(victimKey + ".offHand", hands[1]);
            }
        }

        try {
            config.save(handcuffsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save handcuffs.yml: " + e.getMessage());
        }
    }

    public void loadHandcuffsData() {
        if (!handcuffsFile.exists()) {
            return;
        }

        handcuffsConfig = YamlConfiguration.loadConfiguration(handcuffsFile);

        for (String key : handcuffsConfig.getKeys(false)) {
            try {
                UUID victimUuid = UUID.fromString(key);
                
                // Load judge UUID
                String judgePath = key + ".judge";
                String judgeStr = handcuffsConfig.getString(judgePath);
                if (judgeStr == null) {
                    // Old format compatibility
                    judgeStr = handcuffsConfig.getString(key);
                }
                
                if (judgeStr != null) {
                    UUID judgeUuid = UUID.fromString(judgeStr);
                    handcuffedPlayers.put(victimUuid, judgeUuid);
                    
                    // Load hand items
                    ItemStack mainHand = handcuffsConfig.getItemStack(key + ".mainHand");
                    ItemStack offHand = handcuffsConfig.getItemStack(key + ".offHand");
                    
                    ItemStack[] hands = new ItemStack[2];
                    hands[0] = mainHand;
                    hands[1] = offHand;
                    savedHands.put(victimUuid, hands);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in handcuffs.yml: " + key);
            }
        }
    }

    public void restoreHandItemsOnLogout(Player player) {
        // Items are already saved in savedHands map
        // They will be restored when unhandcuffed or on next login if still handcuffed
        saveHandcuffsData();
    }

    public void restoreHandItemsOnLogin(Player player) {
        // If player is still handcuffed, their hands should remain empty
        // Items are preserved in savedHands map
        if (isHandcuffed(player)) {
            ItemStack[] hands = savedHands.get(player.getUniqueId());
            if (hands == null) {
                // Edge case: player was handcuffed before plugin restart without proper save
                hands = new ItemStack[2];
                hands[0] = null;
                hands[1] = null;
                savedHands.put(player.getUniqueId(), hands);
            }
            // Keep hands empty
            player.getInventory().setItemInMainHand(null);
            player.getInventory().setItemInOffHand(null);
        }
    }
}
