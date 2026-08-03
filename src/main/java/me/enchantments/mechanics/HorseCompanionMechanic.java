package me.enchantments.mechanics;

import me.enchantments.main.Enchantments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public final class HorseCompanionMechanic extends Mechanic implements CommandExecutor {

    @Override
    public String getId() {
        return "horse_companion";
    }

    @Override
    public String getName() {
        return "Кінь компаньйон";
    }

    @Override
    public String getDescription() {
        return "Кінь ходитиме за вами";
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    protected boolean hideEnchantmentGlintDefault() {
        return false;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.LEATHER_HORSE_ARMOR, Material.IRON_HORSE_ARMOR, 
                       Material.GOLDEN_HORSE_ARMOR, Material.DIAMOND_HORSE_ARMOR);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.BODY);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "follow-enabled", true,
            "follow-distance", 25.0
        );
    }

    @Override
    protected void onEnable() {
        boolean followEnabled = getBoolean("follow-enabled");
        if (followEnabled) {
            new HorseFollower().runTaskTimer(Enchantments.getInstance(), 0L, 100L);
        }
    }

    private boolean guarded(AbstractHorse horse) {
        if (!horse.isTamed() || horse.getOwner() == null || horse.getEquipment() == null) return false;
        return levelOf(horse.getEquipment().getItem(org.bukkit.inventory.EquipmentSlot.BODY)) > 0;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        boolean teleported = player.getWorld().getEntitiesByClass(AbstractHorse.class).stream()
                .filter(h -> guarded(h) && player.equals(h.getOwner()) && !h.isLeashed())
                .peek(h -> {
                    player.getWorld().playSound(player.getLocation(), "entity.player.levelup", 0.5f, 2.0f);
                    h.teleport(player.getLocation().add(1, 0, 0));
                    h.getWorld().spawnParticle(Particle.CLOUD, h.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.1);
                })
                .count() > 0;
        player.sendMessage(teleported
                ? Component.text("Коні прискакали на свист!", NamedTextColor.GREEN)
                : Component.text("Немає прикликаних коней із зачарованою бронею.", NamedTextColor.RED));
        return true;
    }

    private final class HorseFollower extends BukkitRunnable {
        @Override
        public void run() {
            double followDistance = getDouble("follow-distance");
            Bukkit.getOnlinePlayers().forEach(player -> {
                Location loc = player.getLocation();
                if (player.isInWater() || loc.getY() <= 10 || player.getVehicle() != null) return;
                String block = loc.getBlock().getType().name();
                if (block.contains("LAVA") || block.contains("FIRE")) return;

                Bukkit.getWorlds().forEach(world -> world.getEntitiesByClass(AbstractHorse.class).stream()
                        .filter(h -> guarded(h) && player.equals(h.getOwner()) && !h.isLeashed())
                        .filter(h -> h.getWorld() == player.getWorld() && h.getLocation().distance(loc) > followDistance)
                        .forEach(h -> {
                            Location safe = findSafeLocation(player);
                            if (safe != null) h.teleport(safe);
                        }));
            });
        }

        private Location findSafeLocation(Player player) {
            Location pLoc = player.getLocation();
            Vector[] directions = {
                    pLoc.getDirection().setY(0).normalize().multiply(-12),
                    pLoc.getDirection().multiply(-8),
                    pLoc.getDirection().clone().rotateAroundY(Math.PI / 2).multiply(10),
                    pLoc.getDirection().clone().rotateAroundY(-Math.PI / 2).multiply(10),
                    pLoc.getDirection().multiply(-15)
            };
            for (Vector dir : directions) {
                Location safe = findSafeYLevel(pLoc.clone().add(dir));
                if (safe != null) return safe;
            }
            return null;
        }

        private Location findSafeYLevel(Location base) {
            World world = base.getWorld();
            int baseY = base.getBlockY();
            if (isSafe(base)) return base;
            for (int y = baseY - 1; y >= Math.max(10, baseY - 20); y--) {
                Location test = new Location(world, base.getX(), y, base.getZ());
                if (isSafe(test)) return test;
            }
            for (int y = baseY + 1; y <= Math.min(world.getMaxHeight() - 10, baseY + 20); y++) {
                Location test = new Location(world, base.getX(), y, base.getZ());
                if (isSafe(test)) return test;
            }
            return null;
        }

        private boolean isSafe(Location loc) {
            if (loc.getY() < 10 || loc.getY() > loc.getWorld().getMaxHeight() - 10) return false;
            Material ground = loc.clone().add(0, -1, 0).getBlock().getType();
            if (!ground.isSolid()) return false;
            String name = ground.name();
            if (name.contains("LAVA") || name.contains("FIRE") || name.contains("MAGMA") || name.contains("CACTUS")) return false;
            for (int i = 0; i <= 2; i++) if (!loc.clone().add(0, i, 0).getBlock().getType().isAir()) return false;
            return loc.getBlock().getLightFromSky() >= 15;
        }
    }
}
