package me.enchantments.mechanics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import me.enchantments.main.Mechanic;

import java.util.List;
import java.util.Map;

public final class VoidRescueMechanic extends Mechanic {

    @Override public String getId() { return "void_rescue"; }
    @Override public String getName() { return "Порятунок з безодні"; }
    @Override public String getDescription() { return "Рятує гравця від падіння у безодню, телепортуючи на найближчий клаптик землі, витрачаючи тотем"; }
    @Override public int getMaxLevel() { return 1; }
    @Override public List<Material> getSupportedItems() { return List.of(Material.TOTEM_OF_UNDYING); }
    @Override public List<EnchantSlot> getSlots() { return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND, EnchantSlot.HEAD); }

    @Override public double getEnchantingTableChance() { return 0.03; }
    @Override public int getWeight() { return 3; }

    @Override
    public List<String> getLootChests() {
        return List.of(
            "minecraft:chests/end_city_treasure",
            "minecraft:chests/stronghold_corridor",
            "minecraft:chests/ancient_city",
            "minecraft:chests/ruined_portal"
        );
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of("CLERIC", Map.of("villager-level", 5, "min-level", 1, "max-level", 1, "price", 40));
    }

    @Override public int getAnvilBaseCost() { return 14; }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "search-radius", 128,
            "search-step", 8,
            "loaded-chunks-only", true
        );
    }

    @Override
    protected void onDamaged(EntityDamageEvent event, Player player, int level) {
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) return;

        event.setCancelled(true);
        ItemStack totem = heldTotem(player);
        if (totem != null) consume(player, totem);

        Location safe = findNearestLand(player, getInt("search-radius"), getInt("search-step"));
        player.teleport(safe);
        player.setFallDistance(0);
        soundAt(safe, Sound.ITEM_TOTEM_USE, 1f, 1f);
        particles(safe, Particle.TOTEM_OF_UNDYING, 30, 0.5, 1, 0.5, 0.2);
    }

    /** Шукає саме зачарований тотем у руках, а не перший-ліпший (може бути в шоломі). */
    private ItemStack heldTotem(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        if (main.getType() == Material.TOTEM_OF_UNDYING && levelOf(main) > 0) return main;
        ItemStack off = p.getInventory().getItemInOffHand();
        if (off.getType() == Material.TOTEM_OF_UNDYING && levelOf(off) > 0) return off;
        return null;
    }

    private void consume(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            return;
        }
        if (item.equals(player.getInventory().getItemInMainHand())) {
            player.getInventory().setItemInMainHand(null);
        } else if (item.equals(player.getInventory().getItemInOffHand())) {
            player.getInventory().setItemInOffHand(null);
        }
    }

    private Location findNearestLand(Player player, int searchRadius, int step) {
        World world = player.getWorld();
        boolean loadedOnly = getBoolean("loaded-chunks-only");
        int centerX = player.getLocation().getBlockX();
        int centerZ = player.getLocation().getBlockZ();

        for (int r = 0; r <= searchRadius; r += step) {
            for (int dx = -r; dx <= r; dx += step) {
                for (int dz = -r; dz <= r; dz += step) {
                    if (r != 0 && Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    Location found = groundAt(world, centerX + dx, centerZ + dz, loadedOnly);
                    if (found != null) return found;
                }
            }
        }
        return world.getSpawnLocation();
    }

    private Location groundAt(World world, int x, int z, boolean loadedOnly) {
        // без цієї перевірки getHighestBlockAt генерує чанк синхронно і підвішує головний потік
        if (loadedOnly && !world.isChunkLoaded(x >> 4, z >> 4)) return null;

        Block ground = world.getHighestBlockAt(x, z);
        if (ground.getY() <= world.getMinHeight()) return null;

        String name = ground.getType().name();
        if (name.contains("LAVA") || name.contains("FIRE") || name.contains("MAGMA")) return null;
        if (!ground.getRelative(0, 1, 0).getType().isAir()) return null;
        if (!ground.getRelative(0, 2, 0).getType().isAir()) return null;

        return ground.getLocation().add(0.5, 1, 0.5);
    }
}
