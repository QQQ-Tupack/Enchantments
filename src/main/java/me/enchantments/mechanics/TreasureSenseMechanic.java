package me.enchantments.mechanics;

import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.Lootable;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.enchantments.main.Mechanic;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class TreasureSenseMechanic extends Mechanic {

	private static final Set<Material> TREASURE_BLOCKS = EnumSet.of(
	        Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.DISPENSER,
	        Material.DECORATED_POT, Material.SUSPICIOUS_SAND, Material.SUSPICIOUS_GRAVEL);

	private static final Predicate<Block> TREASURE_FILTER = block -> {
	    Material type = block.getType();
	    return TREASURE_BLOCKS.contains(type) || Tag.SHULKER_BOXES.isTagged(type);
	};
	
    private static final Color SPARK_COLOR = Color.fromRGB(0xFF, 0xE0, 0x8A);
    private static final int MARK_TICKS = 60;

    @Override public String getId() { return "treasure_sense"; }
    @Override public String getName() { return "Чуття Скарбів"; }
    @Override public String getDescription() { return "Подвійне присідання пускає світлячка до найближчої невідкритої скрині зі скарбом."; }
    @Override public int getMaxLevel() { return 3; }
    @Override public List<Material> getSupportedItems() { return List.of(Material.TOTEM_OF_UNDYING); }
    @Override public List<EnchantSlot> getSlots() { return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND, EnchantSlot.HEAD); }

    @Override public boolean isEnchantingTableAllowed() { return false; }
    @Override public List<String> getLootChests() { return List.of("minecraft:chests/desert_pyramid", "minecraft:chests/jungle_temple"); }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of("CARTOGRAPHER", Map.of("level", 3, "min-emeralds", 16, "max-emeralds", 30));
    }

    @Override public int getWeight() { return 6; }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "cooldown-seconds", 30,
            "base-radius", 16.0,
            "radius-per-level", 8.0,
            "sparkle-speed", 0.8
        );
    }

    @Override
    protected void onDoubleSneak(Player player, int level) {
        ItemStack item = carrier(player);
        if (item == null || !ready(player, item)) return;
        cooldown(player, item, getInt("cooldown-seconds") * 20);

        double radius = getDouble("base-radius") + getDouble("radius-per-level") * (level - 1);
        Location target = findTreasure(player, radius);

        if (target == null) {
            deny(player);
            return;
        }

        activation(player);
        soundFor(player, Sound.ITEM_SPYGLASS_USE, 0.7f, 1.4f);
        flySparkle(player, target);
    }

    private Location findTreasure(Player player, double radius) {
        Location from = player.getEyeLocation();
        World world = player.getWorld();
        double eyeX = from.getX(), eyeY = from.getY(), eyeZ = from.getZ();
        int chunkRadius = (int) Math.ceil(radius / 16.0);
        Chunk center = from.getChunk();
        BlockState best = null;
        double bestDistance = radius * radius;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int cx = center.getX() + dx;
                int cz = center.getZ() + dz;
                if (!world.isChunkLoaded(cx, cz)) continue;
                if (chunkDistanceSquared(cx, cz, eyeX, eyeZ) > bestDistance) continue;

                for (BlockState state : world.getChunkAt(cx, cz).getTileEntities(TREASURE_FILTER, false)) {
                    if (!(state instanceof Lootable lootable) || lootable.getLootTable() == null) continue;

                    double ox = state.getX() + 0.5 - eyeX;
                    double oy = state.getY() + 0.5 - eyeY;
                    double oz = state.getZ() + 0.5 - eyeZ;
                    double distance = ox * ox + oy * oy + oz * oz;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = state;
                    }
                }
            }
        }
        return best == null ? null : best.getLocation().add(0.5, 0.5, 0.5);
    }

    private double chunkDistanceSquared(int cx, int cz, double x, double z) {
        double nearX = Math.max(cx << 4, Math.min(x, (cx << 4) + 15));
        double nearZ = Math.max(cz << 4, Math.min(z, (cz << 4) + 15));
        double ox = nearX - x;
        double oz = nearZ - z;
        return ox * ox + oz * oz;
    }

    private void flySparkle(Player player, Location target) {
        Location start = player.getEyeLocation();
        Vector path = target.toVector().subtract(start.toVector());
        double distance = path.length();
        double step = Math.max(0.1, getDouble("sparkle-speed"));

        int steps = distance > 0.1 ? (int) Math.ceil(distance / step) : 0;
        Vector direction = distance > 0.1 ? path.multiply(1.0 / distance) : new Vector(0, 1, 0);

        new BukkitRunnable() {
            int passed;
            int marked;

            @Override
            public void run() {
                if (!player.isOnline() || !target.getWorld().equals(player.getWorld())) {
                    cancel();
                    return;
                }

                if (passed < steps) {
                    trail(player, start, direction, step, passed);
                    passed++;
                    return;
                }

                if (marked == 0) {
                    player.spawnParticle(Particle.FLASH, target, 1, SPARK_COLOR);
                    soundFor(player, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.6f);
                }
                if (marked >= MARK_TICKS) {
                    cancel();
                    return;
                }
                if (marked % 4 == 0) markRing(player, target, marked);
                marked++;
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    private void trail(Player player, Location start, Vector direction, double step, int passed) {
        for (int i = 0; i < 2; i++) {
            double travelled = step * (passed + i * 0.5);
            Location at = start.clone().add(
                    direction.getX() * travelled,
                    direction.getY() * travelled,
                    direction.getZ() * travelled);
            player.spawnParticle(Particle.WAX_ON, at, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private void markRing(Player player, Location target, int tick) {
        double radius = 0.55 + (tick % 20) * 0.01;
        double spin = tick * 0.2;
        for (int i = 0; i < 8; i++) {
            double angle = 2 * Math.PI * i / 8 + spin;
            player.spawnParticle(Particle.WAX_ON,
                    target.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius),
                    1, 0, 0, 0, 0);
        }
    }

    private ItemStack carrier(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (levelOf(main) > 0) return main;
        ItemStack off = player.getInventory().getItemInOffHand();
        if (levelOf(off) > 0) return off;
        ItemStack helmet = player.getInventory().getHelmet();
        if (levelOf(helmet) > 0) return helmet;
        return null;
    }
}