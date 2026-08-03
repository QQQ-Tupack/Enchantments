package me.enchantments.mechanics;

import me.enchantments.main.Enchantments;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HorseCompanionMechanic extends Mechanic {

    private final Map<UUID, Long> boltCooldown = new ConcurrentHashMap<>();
    private int careTicks = 0;

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
        return "Кінь ходить за вами, не б'ється при падінні, пасеться біля вас і рве з місця, коли вам боляче";
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
            "follow-distance", 25.0,
            "fall-immunity", true,
            "graze-heal-seconds", 5,
            "bolt-duration", 4,
            "bolt-cooldown", 20,
            "mounted-damage-reduction", 0.30
        );
    }

    @Override
    protected void onEnable() {
        if (getBoolean("follow-enabled")) {
            new HorseFollower().runTaskTimer(Enchantments.getInstance(), 20L, 100L);
        }
        new HorseCare().runTaskTimer(Enchantments.getInstance(), 40L, 20L);
    }

    // ==========================================
    //   РІВНІ
    // ==========================================
    //  1 — покликати свистом, слідування, імунітет до падіння під вершником
    //  2 — кінь пасеться біля власника й не тоне
    //  3 — ривок при пораненні вершника та зменшення урону по коню під сідлом

    private int levelOn(AbstractHorse horse) {
        if (horse == null || !horse.isTamed() || horse.getOwner() == null || horse.getEquipment() == null) return 0;
        return levelOf(horse.getEquipment().getItem(EquipmentSlot.BODY));
    }

    private boolean ownedBy(AbstractHorse horse, Player player) {
        return horse.getOwner() != null
            && horse.getOwner().getUniqueId().equals(player.getUniqueId());
    }

    // ==========================================
    //   БІЙ ТА ПАДІННЯ
    // ==========================================

    @EventHandler(ignoreCancelled = true)
    public void onHorseHurt(EntityDamageEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getEntity() instanceof AbstractHorse horse)) return;

        int level = levelOn(horse);
        if (level == 0) return;

        // рівень 1: кінь більше не вбивається об землю під вершником
        if (getBoolean("fall-immunity") && e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            e.setCancelled(true);
            horse.setFallDistance(0);
            return;
        }

        // рівень 3: броня тримає удар, поки в сідлі хтось є
        if (level >= 3 && !horse.getPassengers().isEmpty()) {
            e.setDamage(e.getDamage() * (1.0 - getDouble("mounted-damage-reduction")));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRiderHurt(EntityDamageEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getEntity() instanceof Player player)) return;
        if (!(player.getVehicle() instanceof AbstractHorse horse)) return;
        if (levelOn(horse) < 3) return;

        long now = System.currentTimeMillis();
        if (boltCooldown.getOrDefault(horse.getUniqueId(), 0L) > now) return;
        boltCooldown.put(horse.getUniqueId(), now + Math.max(1, getInt("bolt-cooldown")) * 1000L);

        int duration = Math.max(1, getInt("bolt-duration")) * 20;
        horse.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 1, true, true, true));
        horse.getWorld().playSound(horse.getLocation(), Sound.ENTITY_HORSE_BREATHE, 0.8f, 1.4f);
        horse.getWorld().spawnParticle(Particle.CLOUD, horse.getLocation(), 12, 0.4, 0.1, 0.4, 0.05);
    }

    // ==========================================
    //   ДОГЛЯД: ВИПАС І ДИХАННЯ (РІВЕНЬ 2)
    // ==========================================

    private final class HorseCare extends BukkitRunnable {
        @Override
        public void run() {
            if (!isMechanicEnabled()) return;
            careTicks++;

            int grazeInterval = Math.max(1, getInt("graze-heal-seconds"));
            for (World world : Bukkit.getWorlds()) {
                for (AbstractHorse horse : world.getEntitiesByClass(AbstractHorse.class)) {
                    int level = levelOn(horse);
                    if (level < 2) continue;

                    // не тоне
                    if (horse.isInWater() && horse.getRemainingAir() < horse.getMaximumAir()) {
                        horse.setRemainingAir(horse.getMaximumAir());
                    }

                    // пасеться, поки без вершника і стоїть на траві
                    if (careTicks % grazeInterval != 0) continue;
                    if (!horse.getPassengers().isEmpty()) continue;

                    Material ground = horse.getLocation().clone().add(0, -1, 0).getBlock().getType();
                    if (ground != Material.GRASS_BLOCK && ground != Material.MOSS_BLOCK) continue;

                    AttributeInstance attribute = horse.getAttribute(Attribute.MAX_HEALTH);
                    double max = attribute != null ? attribute.getValue() : 20.0;
                    if (horse.getHealth() >= max) continue;

                    horse.setHealth(Math.min(max, horse.getHealth() + 1.0));
                    horse.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, horse.getLocation().add(0, 1, 0), 3, 0.4, 0.3, 0.4, 0.01);
                }
            }
        }
    }

    // ==========================================
    //   СЛІДУВАННЯ (РІВЕНЬ 1)
    // ==========================================

    private final class HorseFollower extends BukkitRunnable {
        @Override
        public void run() {
            if (!isMechanicEnabled()) return;

            double followDistance = getDouble("follow-distance");

            for (Player player : Bukkit.getOnlinePlayers()) {
                Location loc = player.getLocation();
                if (player.isInWater() || loc.getY() <= 10 || player.getVehicle() != null) continue;

                String block = loc.getBlock().getType().name();
                if (block.contains("LAVA") || block.contains("FIRE")) continue;

                // сканується лише світ гравця, а не всі світи на кожного гравця
                for (AbstractHorse horse : player.getWorld().getEntitiesByClass(AbstractHorse.class)) {
                    if (levelOn(horse) == 0 || !ownedBy(horse, player) || horse.isLeashed()) continue;
                    if (!horse.getPassengers().isEmpty()) continue;
                    if (horse.getLocation().distanceSquared(loc) <= followDistance * followDistance) continue;

                    Location safe = findSafeLocation(player);
                    if (safe != null) horse.teleport(safe);
                }
            }
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
            if (standable(base)) return base;
            for (int y = baseY - 1; y >= Math.max(10, baseY - 20); y--) {
                Location test = new Location(world, base.getX(), y, base.getZ());
                if (standable(test)) return test;
            }
            for (int y = baseY + 1; y <= Math.min(world.getMaxHeight() - 10, baseY + 20); y++) {
                Location test = new Location(world, base.getX(), y, base.getZ());
                if (standable(test)) return test;
            }
            return null;
        }
    }

    /** Відкрите небо обов'язкове: інакше кінь телепортувався б за гравцем у печери. */
    private boolean standable(Location loc) {
        World world = loc.getWorld();
        if (loc.getY() < 10 || loc.getY() > world.getMaxHeight() - 10) return false;

        Material ground = loc.clone().add(0, -1, 0).getBlock().getType();
        if (!ground.isSolid()) return false;

        String name = ground.name();
        if (name.contains("LAVA") || name.contains("FIRE") || name.contains("MAGMA") || name.contains("CACTUS")) return false;

        for (int i = 0; i <= 2; i++) {
            if (!loc.clone().add(0, i, 0).getBlock().getType().isAir()) return false;
        }
        return loc.getBlock().getLightFromSky() >= 15;
    }
}
