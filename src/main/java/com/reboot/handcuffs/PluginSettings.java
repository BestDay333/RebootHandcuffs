package com.reboot.handcuffs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Кэш конфигурации.
 * Все значения читаются один раз при старте/перезагрузке, чтобы горячие
 * обработчики событий (вроде PlayerMoveEvent) не ходили в YAML каждый тик.
 */
public final class PluginSettings {

    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

    private final RebootHandcuffs plugin;
    private final NamespacedKey cuffItemKey;

    private String permissionMode = "op";
    private String messageDisplay = "actionbar";
    private boolean miniMessageFormat;
    private String rawCuffItemName = "Наручники";
    private String plainCuffItemName = "Наручники";
    private Material cuffItem = Material.LEAD;
    private long cuffDelay = 15L;
    private long uncuffDelay = 20L;
    private long cooldownTicks = 20L;
    /** 0 - автоснятие отключено. */
    private long autoUncuffSeconds;

    private double maxDistance = 1.5D;
    private double pullStrength = 0.15D;
    private double maxPullStrength = 0.8D;
    private double heightFollow = 0.12D;

    private boolean particleEnabled = true;
    private Particle particle = Particle.END_ROD;
    private int particleEveryNTicks = 2;
    private double particleDensity = 1.0D;
    private int particleMaxPoints = 12;
    private double particleMaxDistance = 32.0D;
    private int particleReceivers = 48;

    private boolean allowMove;
    private boolean allowBreak;
    private boolean allowPlace;
    private boolean allowAttack;
    private boolean allowDamage;
    private boolean allowInventory;
    private boolean allowDrop;
    private boolean allowSwapHands;
    private boolean allowVehicles;
    private boolean allowInteract;
    private Set<DamageCause> neverBlockDamage = defaultDamageExemptions();

    public PluginSettings(RebootHandcuffs plugin) {
        this.plugin = plugin;
        this.cuffItemKey = new NamespacedKey(plugin, "cuff_item");
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        permissionMode = cfg.getString("permission", "op");

        String display = cfg.getString("message-display", "actionbar");
        display = display == null ? "actionbar" : display.trim().toLowerCase(Locale.ROOT);
        messageDisplay = display.equals("chat") || display.equals("title") ? display : "actionbar";

        miniMessageFormat = "minimessage".equalsIgnoreCase(cfg.getString("message-format", "legacy"));

        String materialName = cfg.getString("cuff.material", "LEAD");
        Material parsed = materialName == null ? null : Material.matchMaterial(materialName);
        if (parsed == null || parsed.isAir()) {
            plugin.getLogger().warning("Unknown cuff material '" + materialName + "', falling back to LEAD");
            parsed = Material.LEAD;
        }
        cuffItem = parsed;

        rawCuffItemName = cfg.getString("cuff.name", "Наручники");
        if (rawCuffItemName == null) {
            rawCuffItemName = "Наручники";
        }
        plainCuffItemName = PlainTextComponentSerializer.plainText()
                .serialize(deserialize(rawCuffItemName)).trim();

        cuffDelay = Maths.clampLong(cfg.getLong("cuff.delay-ticks", 15L), 0L, 1200L);
        uncuffDelay = Maths.clampLong(cfg.getLong("cuff.uncuff-delay-ticks", 20L), 0L, 1200L);
        cooldownTicks = Maths.clampLong(cfg.getLong("cuff.cooldown-ticks", 20L), 0L, 1200L);
        autoUncuffSeconds = Maths.clampLong(
                cfg.getLong("cuff.auto-uncuff-after-seconds", 0L), 0L, 60L * 60L * 24L * 30L);

        maxDistance = Math.max(0.1D, cfg.getDouble("max-distance", 1.5D));
        pullStrength = Math.max(0.0D, cfg.getDouble("pull-strength", 0.15D));
        maxPullStrength = Math.max(0.0D, cfg.getDouble("max-pull-strength", 0.8D));
        heightFollow = cfg.getDouble("height-follow", 0.12D);

        particleEnabled = cfg.getBoolean("particles.enabled", true);
        String particleName = cfg.getString("particles.type", "END_ROD");
        particle = Particle.END_ROD;
        if (particleName != null) {
            try {
                particle = Particle.valueOf(particleName.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Unknown particle '" + particleName + "', falling back to END_ROD");
            }
        }
        particleEveryNTicks = (int) Maths.clampLong(cfg.getLong("particles.every-n-ticks", 2L), 1L, 20L);
        particleDensity = Maths.clampDouble(cfg.getDouble("particles.density", 1.0D), 0.1D, 32.0D);
        particleMaxPoints = (int) Maths.clampLong(cfg.getLong("particles.max-points", 12L), 1L, 128L);
        particleMaxDistance = Math.max(1.0D, cfg.getDouble("particles.max-distance", 32.0D));
        particleReceivers = (int) Maths.clampLong(cfg.getLong("particles.receivers", 48L), 1L, 512L);

        allowMove = cfg.getBoolean("restrictions.allow-move", false);
        allowBreak = cfg.getBoolean("restrictions.allow-break", false);
        allowPlace = cfg.getBoolean("restrictions.allow-place", false);
        allowAttack = cfg.getBoolean("restrictions.allow-attack", false);
        allowDamage = cfg.getBoolean("restrictions.allow-damage", false);
        allowInventory = cfg.getBoolean("restrictions.allow-inventory", false);
        allowDrop = cfg.getBoolean("restrictions.allow-drop", false);
        allowSwapHands = cfg.getBoolean("restrictions.allow-swap-hands", false);
        allowVehicles = cfg.getBoolean("restrictions.allow-vehicles", false);
        allowInteract = cfg.getBoolean("restrictions.allow-interact", false);

        EnumSet<DamageCause> causes = EnumSet.noneOf(DamageCause.class);
        for (String raw : cfg.getStringList("restrictions.never-block-damage-causes")) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                causes.add(DamageCause.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Unknown damage cause in config: " + raw);
            }
        }
        if (causes.isEmpty()) {
            causes = defaultDamageExemptions();
        }
        neverBlockDamage = causes;
    }

    // ------------------------------------------------------------------
    //  Гейтеры
    // ------------------------------------------------------------------

    public boolean hasPermission(Player player) {
        if (permissionMode == null || permissionMode.isBlank() || "op".equalsIgnoreCase(permissionMode)) {
            return player.isOp();
        }
        return player.hasPermission(permissionMode);
    }

    public long cuffDelay() {
        return cuffDelay;
    }

    public long uncuffDelay() {
        return uncuffDelay;
    }

    public long cooldownMillis() {
        return cooldownTicks * 50L;
    }

    /** 0 - автоснятие наручников отключено, иначе - секунды заковывания. */
    public long autoUncuffSeconds() {
        return autoUncuffSeconds;
    }

    public double maxDistance() {
        return maxDistance;
    }

    public double pullStrength() {
        return pullStrength;
    }

    public double maxPullStrength() {
        return maxPullStrength;
    }

    public double heightFollow() {
        return heightFollow;
    }

    public boolean particleEnabled() {
        return particleEnabled;
    }

    public Particle particle() {
        return particle;
    }

    public int particleEveryNTicks() {
        return particleEveryNTicks;
    }

    public double particleDensity() {
        return particleDensity;
    }

    public int particleMaxPoints() {
        return particleMaxPoints;
    }

    public double particleMaxDistance() {
        return particleMaxDistance;
    }

    public int particleReceivers() {
        return particleReceivers;
    }

    public boolean allowMove() {
        return allowMove;
    }

    public boolean allowBreak() {
        return allowBreak;
    }

    public boolean allowPlace() {
        return allowPlace;
    }

    public boolean allowAttack() {
        return allowAttack;
    }

    public boolean allowDamage() {
        return allowDamage;
    }

    public boolean allowInventory() {
        return allowInventory;
    }

    public boolean allowDrop() {
        return allowDrop;
    }

    public boolean allowSwapHands() {
        return allowSwapHands;
    }

    public boolean allowVehicles() {
        return allowVehicles;
    }

    public boolean allowInteract() {
        return allowInteract;
    }

    public Set<DamageCause> neverBlockDamage() {
        return neverBlockDamage;
    }

    // ------------------------------------------------------------------
    //  Предмет наручников
    // ------------------------------------------------------------------

    public NamespacedKey cuffItemKey() {
        return cuffItemKey;
    }

    public boolean isCuffItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        // PDC-метка важнее материала: предмет остаётся наручниками, даже если
        // cuff.material сменили в конфиге после выдачи.
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(cuffItemKey, PersistentDataType.STRING)) {
            return true;
        }
        if (item.getType() != cuffItem) {
            return false;
        }
        Component name = meta.displayName();
        if (name == null) {
            return false;
        }
        return PlainTextComponentSerializer.plainText().serialize(name).trim()
                .equalsIgnoreCase(plainCuffItemName);
    }

    public ItemStack createCuffItem(int amount) {
        ItemStack stack = new ItemStack(cuffItem);
        if (amount > 1) {
            stack.setAmount(Math.min(amount, Math.max(1, stack.getMaxStackSize())));
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(deserialize(rawCuffItemName));
            meta.getPersistentDataContainer().set(cuffItemKey, PersistentDataType.STRING, "1");
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ------------------------------------------------------------------
    //  Сообщения
    // ------------------------------------------------------------------

    public Component deserialize(String input) {
        if (input == null) {
            return Component.empty();
        }
        if (miniMessageFormat) {
            return MiniMessage.miniMessage().deserialize(input);
        }
        // legacy: поддерживаем и §-коды, и &-коды
        return input.indexOf('§') >= 0 ? SECTION.deserialize(input) : AMPERSAND.deserialize(input);
    }

    public String raw(String key, String def) {
        String value = plugin.getConfig().getString(key, def);
        return value == null ? def : value;
    }

    public Component component(String key, String def, String... placeholders) {
        return deserialize(Maths.applyPlaceholders(raw(key, def), placeholders));
    }

    /** Сообщение в чат - всегда в чат, независимо от message-display. */
    public void sendChat(CommandSender sender, String key, String def, String... placeholders) {
        sender.sendMessage(component(key, def, placeholders));
    }

    /** Игровое сообщение игроку в соответствии с message-display. */
    public void send(Player player, String key, String def, String... placeholders) {
        Component component = component(key, def, placeholders);
        switch (messageDisplay) {
            case "chat" -> player.sendMessage(component);
            case "title" -> player.showTitle(Title.title(
                    Component.empty(),
                    component,
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1500L), Duration.ofMillis(300L))));
            default -> player.sendActionBar(component);
        }
    }

    // ------------------------------------------------------------------
    //  Утилиты
    // ------------------------------------------------------------------

    private static EnumSet<DamageCause> defaultDamageExemptions() {
        return EnumSet.of(DamageCause.VOID, DamageCause.KILL);
    }
}
