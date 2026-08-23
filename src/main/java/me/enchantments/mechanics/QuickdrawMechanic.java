package me.enchantments.mechanics;

import me.enchantments.main.Mechanic;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Влучання стрілою з великої дистанції завдає більше шкоди.
 * Постріл видно в польоті: димний слід до порогу дистанції, світна нитка — після.
 */
public class QuickdrawMechanic extends Mechanic {

    /** uuid стріли -> пройдена дистанція (тільки для «заряджених» пострілів). */
    private final Map<UUID, Double> tracked = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "quickdraw";
    }

    @Override
    public String getName() {
        return "Постріл Здалеку";
    }

    @Override
    public String getDescription() {
        return "Влучання стрілою з великої дистанції завдає більше шкоди.";
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.TOTEM_OF_UNDYING);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND, EnchantSlot.HEAD);
    }

    @Override
    public boolean isEnchantingTableAllowed() {
        return true;
    }

    @Override
    public double getEnchantingTableChance() {
        return 0.10;
    }

    @Override
    public boolean isChestLootAllowed() {
        return true;
    }

    @Override
    public double getChestLootChance() {
        return 0.05;
    }

    @Override
    public List<String> getLootChests() {
        return List.of("minecraft:chests/pillager_outpost", "minecraft:chests/stronghold_corridor");
    }

    @Override
    public boolean isVillagerTradeAllowed() {
        return true;
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of("FLETCHER", Map.of(
                "level", 3,
                "min-emeralds", 12,
                "max-emeralds", 24
        ));
    }

    @Override
    public int getAnvilBaseCost() {
        return 8;
    }

    @Override
    public int getWeight() {
        return 10;
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
                "min-distance", 20.0,
                "damage-bonus-per-level", 0.25
        );
    }

    @Override
    protected void onDisable() {
        tracked.clear();
    }

    /** Трасування пострілу: видно і саму стрілу, і момент, коли вона «зарядилась». */
    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!isMechanicEnabled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        if (levelOnPlayer(player) <= 0) return;

        final Location origin = player.getEyeLocation();
        final double min = getDouble("min-distance");

        new BukkitRunnable() {
            private Location last = arrow.getLocation();
            private boolean armed = false;
            private int ticks = 0;

            @Override
            public void run() {
                if (!arrow.isValid() || arrow.isInBlock() || arrow.isOnGround() || ++ticks > 200) {
                    tracked.remove(arrow.getUniqueId());
                    cancel();
                    return;
                }

                Location now = arrow.getLocation();
                if (!now.getWorld().equals(origin.getWorld())) {
                    tracked.remove(arrow.getUniqueId());
                    cancel();
                    return;
                }

                double travelled = now.distance(origin);

                if (!armed && travelled >= min) {
                    armed = true;
                    particles(now, Particle.FLASH, 1, 0.0);
                    particleCircle(now, Particle.END_ROD, 0.6, 12);
                    soundAt(now, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 2.0f);
                    soundFor(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 2.0f);
                }

                if (armed) {
                    tracked.put(arrow.getUniqueId(), travelled);
                    particleLine(last, now, Particle.END_ROD, 0.25);
                } else {
                    particles(now, Particle.SMOKE, 1, 0.0);
                }

                last = now;
            }
        }.runTaskTimer(getPlugin(), 1L, 1L);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!isMechanicEnabled()) return;
        if (!(event.getDamager() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player player)) return;
        if (event.getEntity().equals(player)) return;

        Double travelled = tracked.remove(arrow.getUniqueId());
        if (travelled == null) return;

        int level = levelOnPlayer(player);
        if (level <= 0) return;

        event.setDamage(event.getDamage() * (1.0 + getDouble("damage-bonus-per-level") * level));

        double min = getDouble("min-distance");
        double far = Math.min(1.0, (travelled - min) / min);

        Location hit = event.getEntity().getLocation().add(0, 1, 0);
        particles(hit, Particle.CRIT, 30 + (int) (40 * far), 0.5);
        particles(hit, Particle.FLASH, 1, 0.0);
        particleCircle(hit, Particle.END_ROD, 1.0 + far, 24);
        soundAt(hit, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.9f);
        soundFor(player, Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, (float) (1.4 + 0.4 * far));
    }
}