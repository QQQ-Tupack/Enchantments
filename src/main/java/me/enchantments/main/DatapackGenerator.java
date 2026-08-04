package me.enchantments.main;

import me.enchantments.mechanics.Mechanic;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class DatapackGenerator {

    private static final String PACK_NAME = "enchantments";

    private DatapackGenerator() {}

    public static void install() {
        Collection<Mechanic> mechanics = Mechanic.getAll();
        if (mechanics.isEmpty()) {
            Enchantments.getInstance().getLogger().warning("Механік не зареєстровано — датапак не генерується.");
            return;
        }

        Path packRoot = Bukkit.getWorldContainer().toPath()
                .resolve(levelName())
                .resolve("datapacks")
                .resolve(PACK_NAME);

        Map<String, String> files = new LinkedHashMap<>();
        files.put("pack.mcmeta", packMcmeta());
        files.putAll(enchantmentFiles(mechanics));

        files.put("data/minecraft/tags/enchantment/in_enchanting_table.json",
                tagOf(mechanics, m -> flag(m, "obtaining.enchanting-table.enabled")));

        files.put("data/minecraft/tags/enchantment/on_random_loot.json",
                tagOf(mechanics, m -> flag(m, "obtaining.chests.enabled")
                        && m.getConfig().getStringList("obtaining.chests.list").isEmpty()));

        files.put("data/minecraft/tags/enchantment/tradeable.json",
                tagOf(mechanics, m -> flag(m, "obtaining.villagers.enabled")
                        && m.getConfig().getConfigurationSection("obtaining.villagers.trades") == null));

        write(packRoot, files);
    }

    // ==========================================
    //   ШЛЯХ ДО СВІТУ
    // ==========================================

    private static String levelName() {
        if (!Bukkit.getWorlds().isEmpty()) {
            return Bukkit.getWorlds().get(0).getName();
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("server.properties"))) {
            props.load(in);
        } catch (IOException e) {
            Enchantments.getInstance().getLogger().warning("Не вдалося прочитати server.properties, беру 'world'.");
        }
        return props.getProperty("level-name", "world");
    }

    // ==========================================
    //   ЗАПИС
    // ==========================================

    private static void write(Path packRoot, Map<String, String> files) {
        boolean changed = false;
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path target = packRoot.resolve(entry.getKey());
            try {
                Files.createDirectories(target.getParent());
                if (Files.exists(target)
                        && Files.readString(target, StandardCharsets.UTF_8).equals(entry.getValue())) continue;
                Files.writeString(target, entry.getValue(), StandardCharsets.UTF_8);
                changed = true;
            } catch (IOException e) {
                Enchantments.getInstance().getLogger().severe("Не вдалося записати " + target + ": " + e.getMessage());
            }
        }

        if (changed) {
            Enchantments.getInstance().getLogger().warning(
                    "Датапак '" + PACK_NAME + "' створено/оновлено. Реєстр зачарувань будується разом зі світом, "
                            + "тому ПОТРІБЕН ПОВНИЙ РЕСТАРТ СЕРВЕРА — /reload нових зачарувань не додасть.");
        }
    }

    // ==========================================
    //   ФАЙЛИ ПАКА
    // ==========================================

    private static String packMcmeta() {
        return """
                {
                  "pack": {
                    "pack_format": 88,
                    "supported_formats": { "min_inclusive": 48, "max_inclusive": 999 },
                    "description": "Enchantments"
                  }
                }
                """;
    }

    private static Map<String, String> enchantmentFiles(Collection<Mechanic> mechanics) {
        Map<String, String> files = new LinkedHashMap<>();
        for (Mechanic m : mechanics) {
            String id = m.getId().toLowerCase();
            files.put("data/enchantments/enchantment/" + id + ".json", enchantmentJson(m));
            files.put("data/enchantments/tags/item/" + id + ".json", itemTagJson(m));
        }
        return files;
    }

    private static String enchantmentJson(Mechanic m) {
        String id = m.getId().toLowerCase();
        FileConfiguration cfg = m.getConfig();

        String desc = "{ \"translate\": \"enchantment.enchantments." + id + "\", \"fallback\": \""
                + escape(cfg != null ? cfg.getString("name", m.getName()) : m.getName()) + "\" }";
        String itemsTag = "\"#enchantments:" + id + "\"";
        String slots = m.getSlots().stream()
                .map(slot -> "\"" + slot.datapackId() + "\"")
                .collect(Collectors.joining(", "));
        String effects = m.getEffects() == null || m.getEffects().isBlank() ? "{}" : m.getEffects().trim();

        int maxLevel = cfg != null ? cfg.getInt("max-level", m.getMaxLevel()) : m.getMaxLevel();
        int anvilCost = cfg != null ? cfg.getInt("anvil-cost.base", m.getAnvilBaseCost()) : m.getAnvilBaseCost();

        return """
                {
                  "description": %s,
                  "supported_items": %s,
                  "primary_items": %s,
                  "weight": %d,
                  "max_level": %d,
                  "min_cost": { "base": 1, "per_level_above_first": 10 },
                  "max_cost": { "base": 50, "per_level_above_first": 10 },
                  "anvil_cost": %d,
                  "slots": [%s],
                  "effects": %s
                }
                """.formatted(desc, itemsTag, itemsTag, m.weight(), maxLevel, anvilCost, slots, effects);
    }

    private static String itemTagJson(Mechanic m) {
        // resolveItems() уже врахував винятки: або supported мінус excluded,
        // або (якщо supported порожній) усі предмети гри мінус excluded
        List<String> ids = m.resolveItems().stream()
                .map(material -> "minecraft:" + material.name().toLowerCase())
                .sorted()
                .toList();
        return valuesJson(ids);
    }

    // ==========================================
    //   ТЕГИ
    // ==========================================

    private static boolean flag(Mechanic m, String path) {
        return m.getConfig().getBoolean(path, true);
    }

    private static String tagOf(Collection<Mechanic> mechanics, Predicate<Mechanic> filter) {
        List<String> ids = mechanics.stream()
                .filter(Mechanic::isMechanicEnabled)
                .filter(m -> m.getConfig() != null)
                .filter(filter)
                .map(m -> "enchantments:" + m.getId().toLowerCase())
                .sorted()
                .toList();
        return valuesJson(ids);
    }

    private static String valuesJson(List<String> ids) {
        if (ids.isEmpty()) return "{\n  \"values\": []\n}\n";
        String body = ids.stream()
                .map(id -> "    \"" + id + "\"")
                .collect(Collectors.joining(",\n"));
        return "{\n  \"values\": [\n" + body + "\n  ]\n}\n";
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}