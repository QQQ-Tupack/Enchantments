package me.enchantments.mechanics;

import me.enchantments.main.Enchantments;
import me.enchantments.utils.EnchantMeta;
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

public final class HorseCompanionMechanic extends Mechanic implements CommandExecutor {

    private final boolean followEnabled;
    private final double followDistance;
    private final String command;

    public HorseCompanionMechanic(EnchantMeta meta) {
        super(meta);
        var params = meta.triggers().isEmpty() ? null : meta.triggers().get(0);
        followEnabled = params != null ? params.getBoolean("follow.enabled", true) : true;
        followDistance = params != null ? params.getDouble("follow.distance", 25.0) : 25.0;
        command = params != null ? params.getString("command", "whistle") : "whistle";

        if (followEnabled) new HorseFollower().runTaskTimer(Enchantments.getInstance(), 0L, 100L);
    }

    @Override
    public String commandName() { return command; }

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
