package me.enchantments.utils;

import me.enchantments.main.Enchantments;
import me.enchantments.mechanics.Mechanic;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public final class DatapackGenerator {

    private static final String PACK_NAME = "enchantments";

    public static void install() {
        EnchantmentRegistry.load();

        World world = Bukkit.getWorld("world");
        if (world == null) {
            Enchantments.getInstance().getLogger().severe("Main world 'world' not found!");
            return;
        }

        List<Mechanic> mechanics = EnchantmentRegistry.mechanics();
        Path worldRoot = Bukkit.getWorldContainer().toPath().resolve(world.getName());
        Path packRoot = worldRoot.resolve("datapacks").resolve(PACK_NAME);

        Map<String, String> files = new LinkedHashMap<>();
        files.put("pack.mcmeta", packMcmeta());
        files.putAll(itemTagFiles());
        files.putAll(enchantmentFiles(mechanics));
        files.putAll(exclusiveSetTagFiles(mechanics));
        files.put("data/minecraft/tags/enchantment/in_enchanting_table.json", enchantingTableTag(mechanics));

        write(packRoot, files);
        writeLangFiles(mechanics);
    }

    private static void write(Path packRoot, Map<String, String> files) {
        boolean anyNew = false;
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path target = packRoot.resolve(entry.getKey());
            try {
                Files.createDirectories(target.getParent());
                if (Files.exists(target)
                        && Files.readString(target, StandardCharsets.UTF_8).equals(entry.getValue())) continue;
                Files.writeString(target, entry.getValue(), StandardCharsets.UTF_8);
                anyNew = true;
            } catch (IOException e) {
                Enchantments.getInstance().getLogger().severe("Failed to write " + target + ": " + e.getMessage());
            }
        }
        if (anyNew) {
            Enchantments.getInstance().getLogger().info("Installed/updated datapack '" + PACK_NAME + "'");
            Bukkit.getScheduler().runTask(Enchantments.getInstance(),
                    () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:reload"));
        }
    }

    private static String packMcmeta() {
        PackMeta meta = EnchantmentRegistry.packMeta();
        return """
                {
                  "pack": {
                    "pack_format": %d,
                    "description": "%s"
                  }
                }
                """.formatted(meta.format(), meta.description());
    }

    private static Map<String, String> itemTagFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : EnchantmentRegistry.itemTags().entrySet()) {
            files.put("data/enchantments/tags/item/" + e.getKey() + ".json", tagFile(e.getValue()));
        }
        return files;
    }

    private static String enchantmentJson(EnchantMeta m) {
        String desc = "{ \"translate\": \"" + m.translationKey() + "\", \"fallback\": \"" + m.name() + "\" }";
        String slots = m.slots().stream().map(s -> "\"" + s.datapackId() + "\"").collect(Collectors.joining(", "));
        String exclusive = m.exclusiveSet() == null ? "" : ",\n  \"exclusive_set\": \"" + m.exclusiveSet() + "\"";
        return """
                {
                  "description": %s,
                  "supported_items": "%s",
                  "primary_items": "%s",
                  "weight": %d,
                  "max_level": %d,
                  "min_cost": { "base": %d, "per_level_above_first": %d },
                  "max_cost": { "base": %d, "per_level_above_first": %d },
                  "anvil_cost": %d,
                  "slots": [%s]%s,
                  "effects": {}
                }
                """.formatted(desc, m.supportedItems(), m.supportedItems(), m.weight(), m.maxLevel(),
                m.minCost().base(), m.minCost().perLevel(), m.maxCost().base(), m.maxCost().perLevel(),
                m.anvilCost(), slots, exclusive);
    }

    private static Map<String, String> enchantmentFiles(List<Mechanic> mechanics) {
        Map<String, String> files = new LinkedHashMap<>();
        for (Mechanic m : mechanics) {
            files.put("data/enchantments/enchantment/" + m.meta().key() + ".json", enchantmentJson(m.meta()));
        }
        return files;
    }

    private static String enchantingTableTag(List<Mechanic> mechanics) {
        List<String> ids = mechanics.stream()
                .filter(m -> m.meta().sources().enchantingTable())
                .map(m -> "enchantments:" + m.meta().key())
                .sorted()
                .toList();
        return tagFile(ids);
    }

    private static Map<String, String> exclusiveSetTagFiles(List<Mechanic> mechanics) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (Mechanic m : mechanics) {
            if (m.meta().exclusiveSet() == null) continue;
            grouped.computeIfAbsent(m.meta().exclusiveSet(), k -> new ArrayList<>())
                    .add("enchantments:" + m.meta().key());
        }
        Map<String, String> files = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : grouped.entrySet()) {
            files.put(tagRefToPath(e.getKey()), tagFile(e.getValue()));
        }
        return files;
    }

    private static String tagRefToPath(String tagRef) {
        String noHash = tagRef.startsWith("#") ? tagRef.substring(1) : tagRef;
        String[] parts = noHash.split(":", 2);
        return "data/" + parts[0] + "/tags/enchantment/" + parts[1] + ".json";
    }

    private static String tagFile(List<String> ids) {
        StringBuilder sb = new StringBuilder("{\n  \"values\": [\n");
        for (int i = 0; i < ids.size(); i++) {
            sb.append("    \"").append(ids.get(i)).append("\"").append(i < ids.size() - 1 ? ",\n" : "\n");
        }
        return sb.append("  ]\n}\n").toString();
    }

    private static void writeLangFiles(List<Mechanic> mechanics) {
        Path langDir = Enchantments.getInstance().getDataFolder().toPath().resolve("generated").resolve("lang");
        try {
            Files.createDirectories(langDir);
        } catch (IOException e) {
            Enchantments.getInstance().getLogger().severe("Failed to create lang dir: " + e.getMessage());
            return;
        }

        String entries = mechanics.stream()
                .map(Mechanic::meta)
                .sorted(Comparator.comparing(EnchantMeta::key))
                .map(m -> "  \"" + m.translationKey() + "\": \"" + escapeJson(m.name()) + "\"")
                .collect(Collectors.joining(",\n"));
        String content = "{\n" + entries + "\n}\n";

        for (String locale : EnchantmentRegistry.packMeta().langLocales()) {
            Path target = langDir.resolve(locale + ".json");
            try {
                Files.writeString(target, content, StandardCharsets.UTF_8);
            } catch (IOException e) {
                Enchantments.getInstance().getLogger().severe("Failed to write lang file " + target + ": " + e.getMessage());
            }
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}