package me.enchantments.mechanics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.enchantments.main.Mechanic;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class EarthshatterMechanic extends Mechanic {

    private static final Set<Material> SCORCHABLE = Set.of(
        Material.GRASS_BLOCK, Material.PODZOL, Material.MYCELIUM, Material.DIRT_PATH, Material.MOSS_BLOCK
    );

    @Override
    public String getId() {
        return "earthshatter";
    }

    @Override
    public String getName() {
        return "Розкол землі";
    }

    @Override
    public String getDescription() {
        return "Стрибок, після якого приземлення жене вперед палаючий розлом";
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
                       Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE,
                       Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE,
                       Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.MAINHAND);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "cooldown-ticks", 120,
            "leap-height", 1.15,
            "fissure-length", 10,
            "base-damage", 8.0,
            "damage-per-fall-block", 0.7,
            "max-damage", 26.0,
            "burn-seconds", 5,
            "crack-fade-ticks", 100,
            "hunger-cost", 5
        );
    }

    @Override public boolean isEnchantingTableAllowed() { return false; }
    @Override public boolean isChestLootAllowed() { return false; }
    @Override public boolean isVillagerTradeAllowed() { return false; }
    @Override public int getAnvilBaseCost() { return 25; }
    @Override protected boolean grindstoneRemovableDefault() { return false; }

    @Override
    protected void onRightClick(PlayerInteractEvent e, Player player, int level) {
        ItemStack axe = e.getItem();
        int held = levelOf(axe);
        if (held == 0 || !player.isOnGround()) return;
        if (!ready(player, axe)) {
            deny(player);
            return;
        }
        if (!spend(player, getInt("hunger-cost"))) return;

        e.setCancelled(true);
        cooldown(player, axe, Math.max(20, getInt("cooldown-ticks") - (held - 1) * 20));
        activation(player);

        player.setVelocity(new Vector(0, getDouble("leap-height"), 0));
        soundAt(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.7f, 1.7f);
        particles(player.getLocation(), Particle.CLOUD, 25, 0.4, 0.1, 0.4, 0.08);

        new Landing(player, held).runTaskTimer(getPlugin(), 8L, 1L);
    }

    private final class Landing extends BukkitRunnable {

        private final Player player;
        private final int level;
        private float peakFall;
        private int ticks;

        private Landing(Player player, int level) {
            this.player = player;
            this.level = level;
        }

        @Override
        public void run() {
            if (!player.isOnline() || player.isDead() || ++ticks > 200) {
                cancel();
                return;
            }

            peakFall = Math.max(peakFall, player.getFallDistance());
            particles(player.getLocation(), Particle.ASH, 2, 0.2, 0.2, 0.2, 0.01);
            if (!player.isOnGround()) return;

            cancel();
            player.setFallDistance(0);
            soundAt(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
            particles(player.getLocation(), Particle.EXPLOSION_EMITTER, 1, 0);

            new Fissure(player, level, peakFall).runTaskTimer(getPlugin(), 1L, 1L);
        }
    }

    private final class Fissure extends BukkitRunnable {

        private final Player caster;
        private final int level;
        private final float fall;
        private final Vector direction;
        private final Set<UUID> hit = new HashSet<>();
        private int step;

        private Fissure(Player caster, int level, float fall) {
            this.caster = caster;
            this.level = level;
            this.fall = fall;
            this.direction = caster.getLocation().getDirection().setY(0).normalize();
        }

        @Override
        public void run() {
            int length = getInt("fissure-length") + (level - 1) * 2;
            if (++step > length) {
                cancel();
                return;
            }

            Location center = caster.getLocation().add(direction.clone().multiply(step));
            int width = step < length / 2 ? 1 : 2;

            for (int side = -width; side <= width; side++) {
                Vector offset = new Vector(-direction.getZ(), 0, direction.getX()).multiply(side);
                crack(center.clone().add(offset));
            }
            strike(center, width + 1.0);
        }

        private void crack(Location at) {
            Block surface = surfaceAt(at);
            if (surface == null) return;

            Block above = surface.getRelative(0, 1, 0);
            if (!above.getType().isAir() && !above.getType().isSolid()) above.setType(Material.AIR);

            if (SCORCHABLE.contains(surface.getType())) surface.setType(Material.DIRT);
            showCrack(surface);

            Location top = surface.getLocation().add(0.5, 1.05, 0.5);
            particles(top, Particle.BLOCK, 14, 0.4, 0.15, 0.4, 0, surface.getBlockData());
            particles(top, Particle.FLAME, 10, 0.3, 0.25, 0.3, 0.04);
            particles(top, Particle.LAVA, 2, 0.3, 0.1, 0.3, 0);
            particles(top, Particle.LARGE_SMOKE, 4, 0.3, 0.3, 0.3, 0.02);
            soundAt(top, Sound.BLOCK_STONE_BREAK, 0.6f, 0.5f);
        }

        private Block surfaceAt(Location at) {
            Block block = at.getBlock();
            for (int dy = 2; dy >= -3; dy--) {
                Block candidate = block.getRelative(0, dy, 0);
                if (candidate.getType().isSolid() && !candidate.getRelative(0, 1, 0).getType().isSolid()) {
                    return candidate;
                }
            }
            return null;
        }

        private void showCrack(Block block) {
            var original = block.getBlockData();
            var glow = Material.MAGMA_BLOCK.createBlockData();
            for (Player viewer : block.getWorld().getPlayers()) viewer.sendBlockChange(block.getLocation(), glow);

            getPlugin().getServer().getScheduler().runTaskLater(getPlugin(), () -> {
                for (Player viewer : block.getWorld().getPlayers()) {
                    viewer.sendBlockChange(block.getLocation(), original);
                }
            }, getInt("crack-fade-ticks"));
        }

        private void strike(Location center, double radius) {
            double damage = Math.min(getDouble("max-damage"),
                    getDouble("base-damage") + fall * getDouble("damage-per-fall-block")) * (1 + (level - 1) * 0.15);

            for (Entity nearby : center.getWorld().getNearbyEntities(center, radius, 2.5, radius)) {
                if (!(nearby instanceof LivingEntity target) || target.equals(caster)) continue;
                if (!hit.add(target.getUniqueId())) continue;

                target.damage(damage, caster);
                target.setFireTicks(getInt("burn-seconds") * 20);
                target.setVelocity(direction.clone().multiply(0.4).setY(0.55));
            }
        }
    }
}
