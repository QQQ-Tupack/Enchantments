package me.enchantments.main;

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
        World world = Bukkit.getWorld("world");
        if (world == null) {
            Enchantments.getInstance().getLogger().severe("Main world 'world' not found!");
            return;
        }

        Collection<Mechanic> mechanics = Mechanic.getAll();
        Path worldRoot = Bukkit.getWorldContainer().toPath().resolve(world.getName());
        Path packRoot = worldRoot.resolve("datapacks").resolve(PACK_NAME);

        Map<String, String> files = new LinkedHashMap<>();
        files.put("pack.mcmeta", packMcmeta());
        files.putAll(enchantmentFiles(mechanics));
        files.put("data/minecraft/tags/enchantment/in_enchanting_table.json", enchantingTableTag(mechanics));

        write(packRoot, files);
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
        return """
                {
                  "pack": {
                    "pack_format": 48,
                    "description": "Enchantments"
                  }
                }
                """;
    }

    private static String enchantmentJson(Mechanic m) {
        String id = m.getId().toLowerCase();
        String desc = "{ \"translate\": \"enchantment.enchantments." + id + "\", \"fallback\": \"" + m.getName() + "\" }";
        String itemsTag = "\"#enchantments:" + id + "\"";
        String slots = m.getSlots().stream()
                .map(slot -> "\"" + slot.datapackId() + "\"")
                .collect(Collectors.joining(", "));
        return """
                {
                  "description": %s,
                  "supported_items": %s,
                  "primary_items": %s,
                  "weight": %d,
                  "max_level": %d,
                  "min_cost": { "base": 1, "per_level_above_first": 1 },
                  "max_cost": { "base": %d, "per_level_above_first": %d },
                  "anvil_cost": 2,
                  "slots": [%s],
                  "effects": {}
                }
                """.formatted(desc, itemsTag, itemsTag, m.weight(), m.getMaxLevel(),
                m.getAnvilBaseCost(), m.getAnvilCostPerLevel(), slots);
    }

    private static String itemTagJson(Mechanic m) {
        List<String> ids = m.getSupportedItems().stream()
                .map(material -> "\"minecraft:" + material.name().toLowerCase() + "\"")
                .toList();
        return "{\n  \"values\": [\n    " + String.join(",\n    ", ids) + "\n  ]\n}\n";
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

    private static String enchantingTableTag(Collection<Mechanic> mechanics) {
        List<String> ids = mechanics.stream()
                .filter(Mechanic::isEnchantingTableAllowed)
                .map(m -> "enchantments:" + m.getId().toLowerCase())
                .sorted()
                .toList();
        return tagFile(ids);
    }

    private static String tagFile(List<String> ids) {
        StringBuilder sb = new StringBuilder("{\n  \"values\": [\n");
        for (int i = 0; i < ids.size(); i++) {
            sb.append("    \"").append(ids.get(i)).append("\"").append(i < ids.size() - 1 ? ",\n" : "\n");
        }
        return sb.append("  ]\n}\n").toString();
    }
}