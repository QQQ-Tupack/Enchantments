package me.enchantments.mechanics;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import me.enchantments.main.Enchantments;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Mechanic implements Listener {

    private static final Map<String, Mechanic> REGISTERED_MECHANICS = new ConcurrentHashMap<>();
    private static JavaPlugin plugin = Enchantments.getInstance();

    private File configFile;
    private FileConfiguration config;

    public static void init(JavaPlugin javaPlugin) {
        plugin = javaPlugin;
    }

    // ==========================================
    //   ОБОВ'ЯЗКОВІ МЕТАДАНІ МЕХАНІКИ
    // ==========================================

    public abstract String getId();
    public abstract String getName();
    public abstract String getDescription();
    public abstract int getMaxLevel();
    public abstract List<Material> getSupportedItems();
    public abstract List<EnchantSlot> getSlots();

    // Compatibility records moved into Mechanic
    public static record EnchantMeta(
            String key,
            String name,
            String translationKey,
            String supportedItems,
            int weight,
            int maxLevel,
            Cost minCost,
            Cost maxCost,
            int anvilCost,
            java.util.List<EnchantSlot> slots,
            String exclusiveSet,
            boolean hideGlint,
            Object sources,
            java.util.List<Object> triggers
    ) {
        public static record Cost(int base, int perLevel) {}
        public static record LootSource(String table, double chance, int minLevel, int maxLevel) {}
        public static record TradeSource(String profession, int villagerLevel, int minLevel, int maxLevel, int price) {}
    }

    public enum EnchantSlot {
        MAINHAND(EquipmentSlot.HAND),
        OFFHAND(EquipmentSlot.OFF_HAND),
        FEET(EquipmentSlot.FEET),
        LEGS(EquipmentSlot.LEGS),
        CHEST(EquipmentSlot.CHEST),
        HEAD(EquipmentSlot.HEAD),
        BODY(EquipmentSlot.BODY);

        private final EquipmentSlot bukkit;
        EnchantSlot(EquipmentSlot bukkit) { this.bukkit = bukkit; }
        public EquipmentSlot bukkit() { return bukkit; }
        public String datapackId() { return name().toLowerCase(); }
    }

    // ==========================================
    //   ДЕФОЛТНІ ПАРАМЕТРИ ДЖЕРЕЛ (ОТРИМАННЯ)
    // ==========================================

    public boolean isEnchantingTableAllowed() {
        return true;
    }

    public double getEnchantingTableChance() {
        return 0.12;
    }

    public boolean isChestLootAllowed() {
        return true;
    }

    public double getChestLootChance() {
        return 0.05;
    }

    public List<String> getLootChests() {
        return Collections.emptyList();
    }

    public boolean isVillagerTradeAllowed() {
        return true;
    }

    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Collections.emptyMap();
    }

    // ==========================================
    //   ВАРТІСТЬ ТА НАЛАШТУВАННЯ
    // ==========================================

    public int getAnvilBaseCost() {
        return 8;
    }

    public int getAnvilCostPerLevel() {
        return 4;
    }

    public int getWeight() {
        return 10;
    }

    /**
     * Опціональні data-driven ефекти зачарування (поле "effects" у vanilla enchantment json).
     * Дозволяє описати прості ефекти декларативно, без @EventHandler, якщо ванільної
     * системи ефектів достатньо. Пишеться як звичайний JSON-текст (як і решта generate-методів
     * у DatapackGenerator). За замовчуванням — порожній об'єкт.
     *
     * Приклад (аналог ванільної "Гостроти"):
     * <pre>{@code
     * @Override
     * public String getEffects() {
     *     return """
     *             {
     *               "minecraft:damage": [
     *                 { "effect": { "type": "minecraft:add", "value": {
     *                     "type": "minecraft:linear", "base": 1.0, "per_level_above_first": 0.5
     *                 } } }
     *               ]
     *             }
     *             """;
     * }
     * }</pre>
     */
    public String getEffects() {
        return "{}";
    }

    public Map<String, Object> getDefaults() {
        return Collections.emptyMap();
    }

    // ==========================================
    //   РЕЄСТРАЦІЯ (PlaceholderAPI Style)
    // ==========================================

    protected void onEnable() {}
    protected void onDisable() {}

    public final boolean register() {
        if (plugin == null) {
            throw new IllegalStateException("Викличте Mechanic.init(plugin) в onEnable()!");
        }

        String id = getId().toLowerCase();
        if (REGISTERED_MECHANICS.containsKey(id)) {
            plugin.getLogger().warning("Механіка '" + id + "' вже зареєстрована!");
            return false;
        }

        setupConfigFile();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        REGISTERED_MECHANICS.put(id, this);
        onEnable();

        plugin.getLogger().info("[Mechanics] '" + getName() + "' (" + id + ") успішно зареєстровано!");
        return true;
    }

    private void setupConfigFile() {
        File folder = new File(plugin.getDataFolder(), "mechanics");
        if (!folder.exists()) {
            folder.mkdirs();
        }

        this.configFile = new File(folder, getId().toLowerCase() + ".yml");
        boolean isNew = !configFile.exists();

        try {
            if (isNew) {
                configFile.createNewFile();
            }
            this.config = YamlConfiguration.loadConfiguration(configFile);

            boolean updated = false;

            if (!config.contains("enabled")) { config.set("enabled", true); updated = true; }
            if (!config.contains("name")) { config.set("name", getName()); updated = true; }
            if (!config.contains("description")) { config.set("description", getDescription()); updated = true; }
            if (!config.contains("max-level")) { config.set("max-level", getMaxLevel()); updated = true; }
            if (!config.contains("supported-items")) {
                List<String> materialNames = new ArrayList<>();
                for (Material material : getSupportedItems()) {
                    materialNames.add(material.name());
                }
                config.set("supported-items", materialNames);
                updated = true;
            }

            if (!config.contains("obtaining.enchanting-table.enabled")) { 
                config.set("obtaining.enchanting-table.enabled", isEnchantingTableAllowed()); 
                updated = true; 
            }
            if (!config.contains("obtaining.enchanting-table.chance")) { 
                config.set("obtaining.enchanting-table.chance", getEnchantingTableChance()); 
                updated = true; 
            }

            if (!config.contains("obtaining.chests.enabled")) { 
                config.set("obtaining.chests.enabled", isChestLootAllowed()); 
                updated = true; 
            }
            if (!config.contains("obtaining.chests.chance")) { 
                config.set("obtaining.chests.chance", getChestLootChance()); 
                updated = true; 
            }
            if (!config.contains("obtaining.chests.list")) { 
                config.set("obtaining.chests.list", getLootChests()); 
                updated = true; 
            }

            if (!config.contains("obtaining.villagers.enabled")) { 
                config.set("obtaining.villagers.enabled", isVillagerTradeAllowed()); 
                updated = true; 
            }
            if (!config.contains("obtaining.villagers.trades")) {
                Map<String, Map<String, Object>> trades = getVillagerTrades();
                if (!trades.isEmpty()) {
                    config.createSection("obtaining.villagers.trades", trades);
                } else {
                    config.set("obtaining.villagers.trades", Collections.emptyMap());
                }
                updated = true;
            }

            if (!config.contains("anvil-cost.base")) { config.set("anvil-cost.base", getAnvilBaseCost()); updated = true; }
            if (!config.contains("anvil-cost.per-level")) { config.set("anvil-cost.per-level", getAnvilCostPerLevel()); updated = true; }

            if (!config.contains("weight")) { config.set("weight", getWeight()); updated = true; }

            if (!config.contains("hide-enchantment-glint")) { config.set("hide-enchantment-glint", hideEnchantmentGlintDefault()); updated = true; }

            Map<String, Object> defaults = getDefaults();
            if (defaults != null) {
                for (Map.Entry<String, Object> entry : defaults.entrySet()) {
                    String path = "settings." + entry.getKey();
                    if (!config.contains(path)) {
                        config.set(path, entry.getValue());
                        updated = true;
                    }
                }
            }

            if (updated || isNew) {
                config.save(configFile);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Помилка конфігу механіки: " + getId());
            e.printStackTrace();
        }
    }

    public void reloadConfig() {
        if (configFile != null) {
            this.config = YamlConfiguration.loadConfiguration(configFile);
        }
    }

    // ==========================================
    //   ЗЧИТУВАННЯ ПАРАМЕТРІВ З КОНФІГУ
    // ==========================================

    public FileConfiguration getConfig() {
        return config;
    }

    public boolean isMechanicEnabled() {
        return config != null && config.getBoolean("enabled", true);
    }

    public String getString(String key) {
        Object def = getDefaults().get(key);
        return config.getString("settings." + key, def != null ? def.toString() : "");
    }

    public int getInt(String key) {
        Object def = getDefaults().get(key);
        int defaultVal = (def instanceof Number n) ? n.intValue() : 0;
        return config.getInt("settings." + key, defaultVal);
    }

    public double getDouble(String key) {
        Object def = getDefaults().get(key);
        double defaultVal = (def instanceof Number n) ? n.doubleValue() : 0.0;
        return config.getDouble("settings." + key, defaultVal);
    }

    public boolean getBoolean(String key) {
        Object def = getDefaults().get(key);
        boolean defaultVal = def instanceof Boolean b && b;
        return config.getBoolean("settings." + key, defaultVal);
    }

    protected boolean hideEnchantmentGlintDefault() { return false; }

    public boolean hideEnchantmentGlint() {
        return config != null && config.getBoolean("hide-enchantment-glint", hideEnchantmentGlintDefault());
    }

    public int weight() {
        return config != null ? config.getInt("weight", getWeight()) : getWeight();
    }

    // ==========================================
    //   ДОПОМІЖНІ МЕТОДИ
    // ==========================================

    private Enchantment cachedEnchantment;

    public int levelOf(ItemStack item) {
        if (item == null) return 0;
        if (cachedEnchantment == null) {
            cachedEnchantment = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                    .get(NamespacedKey.fromString("enchantments:" + getId().toLowerCase()));
            if (cachedEnchantment == null) return 0;
        }
        return item.getEnchantmentLevel(cachedEnchantment);
    }

    public int levelOnPlayer(Player player) {
        if (player == null) return 0;
        // Check armor slots
        if (levelOf(player.getInventory().getHelmet()) > 0) return levelOf(player.getInventory().getHelmet());
        if (levelOf(player.getInventory().getChestplate()) > 0) return levelOf(player.getInventory().getChestplate());
        if (levelOf(player.getInventory().getLeggings()) > 0) return levelOf(player.getInventory().getLeggings());
        if (levelOf(player.getInventory().getBoots()) > 0) return levelOf(player.getInventory().getBoots());
        // Check hand
        if (levelOf(player.getInventory().getItemInMainHand()) > 0) return levelOf(player.getInventory().getItemInMainHand());
        if (levelOf(player.getInventory().getItemInOffHand()) > 0) return levelOf(player.getInventory().getItemInOffHand());
        return 0;
    }

    public static Mechanic get(String id) {
        return REGISTERED_MECHANICS.get(id.toLowerCase());
    }

    public static Collection<Mechanic> getAll() {
        return Collections.unmodifiableCollection(REGISTERED_MECHANICS.values());
    }

    public static void reloadAll() {
        REGISTERED_MECHANICS.values().forEach(Mechanic::reloadConfig);
    }

    public static JavaPlugin getPlugin() {
        return plugin;
    }
}