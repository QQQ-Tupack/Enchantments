package me.enchantments.utils;

import me.enchantments.main.Enchantments;
import me.enchantments.mechanics.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.*;

public final class EnchantmentRegistry {

    private static List<Mechanic> mechanics = List.of();
    private static Map<String, List<String>> itemTags = Map.of();
    private static PackMeta packMeta = PackMeta.DEFAULT;

    private EnchantmentRegistry() {}

    public static void load() {
        ConfigurationSection cfg = Enchantments.getInstance().getConfig();

        packMeta = readPackMeta(cfg.getConfigurationSection("pack"));
        itemTags = readItemTags(cfg.getConfigurationSection("item_tags"));
        mechanics = readMechanics(cfg.getConfigurationSection("enchantments"));
    }

    public static void registerAll(Plugin plugin) {
        for (Mechanic m : mechanics) {
            Bukkit.getPluginManager().registerEvents(m, plugin);
            if (m.commandName() != null && m instanceof org.bukkit.command.CommandExecutor ce) {
                var cmd = plugin.getServer().getPluginCommand(m.commandName());
                if (cmd != null) cmd.setExecutor(ce);
            }
        }
    }

    public static List<Mechanic> mechanics() { return mechanics; }
    public static Map<String, List<String>> itemTags() { return itemTags; }
    public static PackMeta packMeta() { return packMeta; }

    private static PackMeta readPackMeta(ConfigurationSection s) {
        if (s == null) return PackMeta.DEFAULT;
        List<String> locales = s.getStringList("lang_locales");
        return new PackMeta(s.getInt("format", 48), s.getString("description", "Enchantments"),
                locales.isEmpty() ? List.of("en_us") : locales);
    }

    private static Map<String, List<String>> readItemTags(ConfigurationSection s) {
        if (s == null) return Map.of();
        Map<String, List<String>> tags = new LinkedHashMap<>();
        for (String key : s.getKeys(false)) tags.put(key, s.getStringList(key));
        return tags;
    }

    private static List<Mechanic> readMechanics(ConfigurationSection root) {
        if (root == null) return List.of();
        List<Mechanic> result = new ArrayList<>();
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            EnchantMeta meta = readMeta(key, s);
            result.add(switch (meta.triggers().isEmpty() ? "" : meta.triggers().get(0).mechanic()) {
                case "double_jump" -> new DoubleJumpMechanic(meta);
                case "effect" -> new EffectMechanic(meta);
                case "axe_throw" -> new AxeThrowMechanic(meta);
                case "axe_pull" -> new AxePullMechanic(meta);
                case "axe_cleave" -> new AxeCleaveMechanic(meta);
                case "parry" -> new ParryMechanic(meta);
                case "parachute" -> new ParachuteMechanic(meta);
                case "grappling_hook" -> new GrapplingHookMechanic(meta);
                case "wolf_companion" -> new WolfCompanionMechanic(meta);
                case "wolf_sniff" -> new WolfSniffMechanic(meta);
                case "wolf_fetch" -> new WolfFetchMechanic(meta);
                case "horse_companion" -> new HorseCompanionMechanic(meta);
                default -> throw new IllegalStateException("Unknown mechanic: " + (meta.triggers().isEmpty() ? "none" : meta.triggers().get(0).mechanic()));
            });
        }
        return result;
    }

    private static EnchantMeta readMeta(String key, ConfigurationSection s) {
        List<String> rawSlots = s.getStringList("slots");
        List<EnchantSlot> slots = rawSlots.isEmpty()
                ? List.of(EnchantSlot.values())
                : rawSlots.stream().map(v -> EnchantSlot.valueOf(v.toUpperCase())).toList();

        List<TriggerConfig> triggers = new ArrayList<>();
        List<Map<?, ?>> rawTriggers = s.getMapList("triggers");
        for (Map<?, ?> m : rawTriggers) {
            TriggerConfig trigger = TriggerConfig.from(m);
            if (trigger != null) triggers.add(trigger);
        }

        return new EnchantMeta(
                key,
                s.getString("name", key),
                s.getString("translation_key", "enchantment.enchantments." + key),
                s.getString("supported_items", ""),
                s.getInt("weight", 5),
                s.getInt("max_level", 3),
                new EnchantMeta.Cost(s.getInt("min_cost_base", 10), s.getInt("min_cost_per_level", 8)),
                new EnchantMeta.Cost(s.getInt("max_cost_base", 40), s.getInt("max_cost_per_level", 8)),
                s.getInt("anvil_cost", 2),
                slots,
                s.contains("exclusive_set") ? s.getString("exclusive_set") : null,
                s.getBoolean("hide_glint", false),
                readSources(s.getConfigurationSection("sources")),
                triggers
        );
    }

    private static EnchantMeta.Sources readSources(ConfigurationSection s) {
        if (s == null) return new EnchantMeta.Sources(true, List.of(), List.of());

        List<EnchantMeta.LootSource> loot = new ArrayList<>();
        for (Map<?, ?> m : s.getMapList("loot_tables")) {
            loot.add(new EnchantMeta.LootSource(
                    String.valueOf(m.get("table")),
                    m.get("chance") instanceof Number n ? n.doubleValue() : 0.1,
                    m.get("min_level") instanceof Number n ? n.intValue() : 1,
                    m.get("max_level") instanceof Number n ? n.intValue() : 1
            ));
        }

        List<EnchantMeta.TradeSource> trades = new ArrayList<>();
        for (Map<?, ?> m : s.getMapList("villager_trades")) {
            trades.add(new EnchantMeta.TradeSource(
                    String.valueOf(m.get("profession")),
                    m.get("villager_level") instanceof Number n ? n.intValue() : 1,
                    m.get("min_level") instanceof Number n ? n.intValue() : 1,
                    m.get("max_level") instanceof Number n ? n.intValue() : 1,
                    m.get("price") instanceof Number n ? n.intValue() : 32
            ));
        }

        return new EnchantMeta.Sources(s.getBoolean("enchanting_table", true), loot, trades);
    }
}

record PackMeta(int format, String description, List<String> langLocales) {
    static final PackMeta DEFAULT = new PackMeta(48, "Enchantments", List.of("en_us"));
}
