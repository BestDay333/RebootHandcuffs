package com.reboot.handcuffs;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Хранение состояния наручников.
 *
 * <p>Формат файла (v2):
 * <pre>
 * format: 2
 * cuffed:
 *   &lt;uuid жертвы&gt;: { judge, judge-name, victim-name, cuffed-at, main-hand, off-hand }
 * pending-restore:
 *   &lt;uuid&gt;: { main-hand, off-hand }   # предметы, которые ещё предстоит вернуть
 * </pre>
 *
 * Старый формат (где ключи - это просто UUID) загружается без изменений.
 *
 * <p>Запись файла выполняется в отдельном потоке и атомарно (через временный
 * файл), поэтому главный поток сервера никогда не ждёт операции ввода-вывода,
 * а файл не может испортиться при аварийном завершении.
 */
public final class DataManager {

    private static final String SECTION_CUFFED = "cuffed";
    private static final String SECTION_PENDING = "pending-restore";
    private static final String KEY_FORMAT = "format";
    private static final int FORMAT_VERSION = 2;

    private final RebootHandcuffs plugin;
    private final File handcuffsFile;

    private final Map<UUID, CuffRecord> handcuffed = new HashMap<>();
    private final Map<UUID, ItemStack[]> pendingRestores = new HashMap<>();
    private final Collection<CuffRecord> recordsView =
            Collections.unmodifiableCollection(handcuffed.values());

    private final Object saveLock = new Object();
    private final Object ioLock = new Object();
    private String pendingSnapshot;   // под saveLock
    private boolean saving;           // под saveLock
    private volatile boolean closed;  // true после shutdown()

    public DataManager(RebootHandcuffs plugin) {
        this.plugin = plugin;
        this.handcuffsFile = new File(plugin.getDataFolder(), "handcuffs.yml");
        loadHandcuffsData();
    }

    // ------------------------------------------------------------------
    //  Чтение состояния
    // ------------------------------------------------------------------

    public boolean isHandcuffed(Player player) {
        return handcuffed.containsKey(player.getUniqueId());
    }

    public boolean isHandcuffed(UUID playerId) {
        return handcuffed.containsKey(playerId);
    }

    public CuffRecord getRecord(UUID victimId) {
        return handcuffed.get(victimId);
    }

    /** UUID того, кто надел наручники, либо null (нет наручников или надеты консолью). */
    public UUID getJudge(UUID victimId) {
        CuffRecord record = handcuffed.get(victimId);
        return record == null ? null : record.getJudge();
    }

    public Collection<CuffRecord> getRecords() {
        return recordsView;
    }

    public int count() {
        return handcuffed.size();
    }

    /** Ищет закованного игрока по нику (работает и для оффлайна). */
    public CuffRecord findByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (CuffRecord record : handcuffed.values()) {
            if (name.equalsIgnoreCase(record.getVictimName())) {
                return record;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  Изменение состояния
    // ------------------------------------------------------------------

    /** @return true, если игрок был закован этим вызовом */
    public boolean handcuff(Player victim, UUID judgeId, String judgeName) {
        UUID victimId = victim.getUniqueId();
        if (handcuffed.containsKey(victimId)) {
            plugin.getLogger().warning("Ignored handcuff of already cuffed player: " + victim.getName());
            return false;
        }

        ItemStack mainHand = nonAir(victim.getInventory().getItemInMainHand());
        ItemStack offHand = nonAir(victim.getInventory().getItemInOffHand());

        handcuffed.put(victimId, new CuffRecord(
                victimId,
                victim.getName(),
                judgeId,
                judgeName,
                System.currentTimeMillis(),
                mainHand,
                offHand));

        // Руки должны быть пустыми, но содержимое ни в коем случае не выбрасываем:
        // предметы лежат в записи и вернутся при снятии наручников.
        victim.getInventory().setItemInMainHand(null);
        victim.getInventory().setItemInOffHand(null);
        victim.setFallDistance(0f);

        plugin.getLogger().info("Player " + victim.getName() + " handcuffed by " + judgeName);
        saveHandcuffsData();
        return true;
    }

    /** @return true, если наручники были сняты этим вызовом */
    public boolean unhandcuff(UUID victimId) {
        CuffRecord record = handcuffed.remove(victimId);
        if (record == null) {
            return false;
        }

        ItemStack mainHand = record.getMainHand();
        ItemStack offHand = record.getOffHand();
        Player online = Bukkit.getPlayer(victimId);

        if (online != null && online.isOnline()) {
            giveBack(online, mainHand, offHand);
        } else {
            // Игрок оффлайн - предметы отдадим при следующем входе.
            pendingRestores.put(victimId, new ItemStack[]{mainHand, offHand});
            plugin.getLogger().info("Deferred hand items restore for offline player "
                    + record.getVictimName());
        }

        plugin.getLogger().info("Player " + record.getVictimName() + " unhandcuffed");
        saveHandcuffsData();
        return true;
    }

    // ------------------------------------------------------------------
    //  Вход/выход игрока
    // ------------------------------------------------------------------

    public void handleJoin(Player player) {
        UUID playerId = player.getUniqueId();

        CuffRecord record = handcuffed.get(playerId);
        if (record != null) {
            record.setVictimName(player.getName());
            // Всё, что оказалось в руках, перекладываем в инвентарь, а не удаляем.
            moveHandsToInventory(player);
            return;
        }

        ItemStack[] pending = pendingRestores.remove(playerId);
        if (pending != null) {
            giveBack(player, pending[0], pending[1]);
            plugin.getLogger().info("Returned stored hand items to " + player.getName());
            saveHandcuffsData();
        }
    }

    public void handleQuit(Player player) {
        UUID playerId = player.getUniqueId();
        if (handcuffed.containsKey(playerId) || pendingRestores.containsKey(playerId)) {
            // Руки на момент выхода должны быть пусты: предметы хранятся у нас.
            saveHandcuffsData();
        }
    }

    // ------------------------------------------------------------------
    //  Работа с инвентарём
    // ------------------------------------------------------------------

    private static ItemStack nonAir(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        return stack.clone();
    }

    private static boolean hasItems(ItemStack stack) {
        return stack != null && !stack.getType().isAir();
    }

    /** Возвращает предметы в руки/инвентарь без единой потери. */
    private void giveBack(Player player, ItemStack mainHand, ItemStack offHand) {
        player.getInventory().setItemInMainHand(null);
        player.getInventory().setItemInOffHand(null);
        store(player, mainHand);
        store(player, offHand);
    }

    /** Перекладывает содержимое рук в инвентарь (руки при этом очищаются). */
    private void moveHandsToInventory(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (!hasItems(mainHand) && !hasItems(offHand)) {
            return;
        }
        store(player, mainHand);
        store(player, offHand);
        player.getInventory().setItemInMainHand(null);
        player.getInventory().setItemInOffHand(null);
    }

    private void store(Player player, ItemStack stack) {
        if (!hasItems(stack)) {
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
        for (ItemStack left : leftovers.values()) {
            if (hasItems(left)) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Загрузка
    // ------------------------------------------------------------------

    public void loadHandcuffsData() {
        if (!handcuffsFile.exists()) {
            return;
        }

        YamlConfiguration cfg;
        try {
            cfg = YamlConfiguration.loadConfiguration(handcuffsFile);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to read handcuffs.yml", ex);
            return;
        }

        ConfigurationSection cuffedSection = cfg.getConfigurationSection(SECTION_CUFFED);
        ConfigurationSection pendingSection = cfg.getConfigurationSection(SECTION_PENDING);
        int format = cfg.getInt(KEY_FORMAT, 0);
        if (format > FORMAT_VERSION) {
            plugin.getLogger().warning("handcuffs.yml has format " + format
                    + ", this build understands " + FORMAT_VERSION
                    + " - loading best-effort, unknown data will be dropped on save");
        }

        // Новый формат распознаётся по секциям ИЛИ по ключу format: секции могут быть
        // пустыми (например, только pending-restore) - в таком случае legacy-разбор
        // потерял бы отложенные предметы игроков.
        if (cuffedSection != null || pendingSection != null || cfg.isSet(KEY_FORMAT)) {
            if (cuffedSection != null) {
                for (String key : cuffedSection.getKeys(false)) {
                    UUID victimId = parseUuid(key);
                    if (victimId == null) {
                        plugin.getLogger().warning("Invalid victim UUID in handcuffs.yml: " + key);
                        continue;
                    }
                    ConfigurationSection section = cuffedSection.getConfigurationSection(key);
                    if (section == null) {
                        continue;
                    }
                    loadNewFormat(victimId, section);
                }
            }

            if (pendingSection != null) {
                for (String key : pendingSection.getKeys(false)) {
                    UUID playerId = parseUuid(key);
                    ConfigurationSection section = pendingSection.getConfigurationSection(key);
                    if (playerId == null || section == null) {
                        continue;
                    }
                    pendingRestores.put(playerId, new ItemStack[]{
                            section.getItemStack("main-hand"),
                            section.getItemStack("off-hand")});
                }
            }
            plugin.getLogger().info("Loaded " + handcuffed.size() + " cuffed player(s), "
                    + pendingRestores.size() + " pending restore(s)");
            return;
        }

        loadLegacyFormat(cfg);
    }

    private void loadNewFormat(UUID victimId, ConfigurationSection section) {
        String judgeStr = section.getString("judge");
        UUID judgeId = null;
        if (judgeStr != null && !judgeStr.isBlank() && !"console".equalsIgnoreCase(judgeStr)) {
            judgeId = parseUuid(judgeStr);
            if (judgeId == null) {
                plugin.getLogger().warning("Invalid judge UUID for " + victimId);
            }
        }
        ItemStack mainHand = section.getItemStack("main-hand");
        ItemStack offHand = section.getItemStack("off-hand");
        String victimName = section.getString("victim-name", victimId.toString());
        long cuffedAt = section.getLong("cuffed-at", System.currentTimeMillis());

        handcuffed.put(victimId, new CuffRecord(
                victimId, victimName, judgeId,
                section.getString("judge-name", "?"),
                cuffedAt, nonAir(mainHand), nonAir(offHand)));
    }

    /** Формат 1.x: ключи - UUID, значения - judge (+ mainHand/offHand). */
    private void loadLegacyFormat(YamlConfiguration cfg) {
        int loaded = 0;
        for (String key : cfg.getKeys(false)) {
            UUID victimId = parseUuid(key);
            if (victimId == null) {
                plugin.getLogger().warning("Invalid UUID in handcuffs.yml: " + key);
                continue;
            }

            String judgeStr = cfg.getString(key + ".judge");
            ConfigurationSection section = cfg.getConfigurationSection(key);
            if (judgeStr == null) {
                // Самый старый вариант: <uuid>: <judge-uuid>
                judgeStr = cfg.getString(key);
            }

            UUID judgeId = null;
            if (judgeStr != null && !judgeStr.isBlank() && !"console".equalsIgnoreCase(judgeStr)) {
                judgeId = parseUuid(judgeStr);
                if (judgeId == null) {
                    plugin.getLogger().warning("Invalid judge UUID for " + key);
                }
            }

            ItemStack mainHand = section == null ? null : section.getItemStack("mainHand");
            ItemStack offHand = section == null ? null : section.getItemStack("offHand");

            handcuffed.put(victimId, new CuffRecord(
                    victimId,
                    resolveName(victimId),
                    judgeId,
                    judgeId == null ? "console" : resolveName(judgeId),
                    System.currentTimeMillis(),
                    nonAir(mainHand),
                    nonAir(offHand)));
            loaded++;
        }

        if (loaded > 0) {
            plugin.getLogger().info("Migrated " + loaded + " record(s) from legacy handcuffs.yml format");
            saveHandcuffsData();
        }
    }

    private static String resolveName(UUID uuid) {
        try {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
            String name = offline.getName();
            return name == null ? uuid.toString() : name;
        } catch (RuntimeException ex) {
            return uuid.toString();
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    //  Сохранение
    // ------------------------------------------------------------------

    /** Запланировать сохранение (асинхронно, главный поток не блокируется). */
    public void saveHandcuffsData() {
        String snapshot = buildSnapshot();
        if (snapshot == null) {
            // Сериализация не удалась: не затираем последний удачный снимок,
            // он будет записан при следующем успешном сохранении.
            return;
        }
        synchronized (saveLock) {
            if (closed) {
                return;
            }
            pendingSnapshot = snapshot;
            if (saving) {
                // Уже выполняется запись - она подхватит последний снимок.
                return;
            }
            saving = true;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::performSave);
    }

    private void performSave() {
        while (true) {
            String snapshot;
            synchronized (saveLock) {
                snapshot = pendingSnapshot;
                pendingSnapshot = null;
                if (snapshot == null || closed) {
                    saving = false;
                    return;
                }
            }
            try {
                writeAtomic(snapshot);
            } catch (Throwable t) {
                synchronized (saveLock) {
                    // Возвращаем снимок в очередь: он допишется при следующем сохранении.
                    if (pendingSnapshot == null) {
                        pendingSnapshot = snapshot;
                    }
                    saving = false;
                }
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to save handcuffs.yml, snapshot queued for retry", t);
                return;
            }
        }
    }

    private String buildSnapshot() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set(KEY_FORMAT, FORMAT_VERSION);

        for (CuffRecord record : handcuffed.values()) {
            String base = SECTION_CUFFED + "." + record.getVictim();
            cfg.set(base + ".judge", record.getJudge() == null ? "console" : record.getJudge().toString());
            cfg.set(base + ".judge-name", record.getJudgeName());
            cfg.set(base + ".victim-name", record.getVictimName());
            cfg.set(base + ".cuffed-at", record.getCuffedAt());
            if (record.getMainHand() != null) {
                cfg.set(base + ".main-hand", record.getMainHand());
            }
            if (record.getOffHand() != null) {
                cfg.set(base + ".off-hand", record.getOffHand());
            }
        }

        for (Map.Entry<UUID, ItemStack[]> entry : pendingRestores.entrySet()) {
            String base = SECTION_PENDING + "." + entry.getKey();
            ItemStack[] hands = entry.getValue();
            if (hands[0] != null) {
                cfg.set(base + ".main-hand", hands[0]);
            }
            if (hands[1] != null) {
                cfg.set(base + ".off-hand", hands[1]);
            }
        }

        try {
            return cfg.saveToString();
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to serialize handcuffs data", ex);
            return null;
        }
    }

    private void writeAtomic(String content) throws IOException {
        synchronized (ioLock) {
            if (closed) {
                return;
            }
            writeToDisk(content);
        }
    }

    private void writeToDisk(String content) throws IOException {
        File parent = handcuffsFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create directory " + parent);
        }
        Path target = handcuffsFile.toPath();
        Path temp = target.resolveSibling(handcuffsFile.getName() + ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        try {
            Files.move(temp, target,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Синхронное сохранение при выключении сервера. */
    public void shutdown() {
        String snapshot = buildSnapshot();
        synchronized (saveLock) {
            closed = true;
            pendingSnapshot = null;
            saving = false;
        }
        if (snapshot == null) {
            return;
        }
        try {
            synchronized (ioLock) {
                writeToDisk(snapshot);
            }
            plugin.getLogger().info("Handcuffs data saved (" + handcuffed.size() + " record(s))");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to write handcuffs.yml on shutdown!", ex);
        }
    }
}
