package me.enchantments.mechanics;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Гейзер.
 *
 * Присідання у воді збирає під ногами тиск: бульбашки густішають, звук наростає.
 * Відпустив присідання — стовп окропу вистрілює вгору, викидаючи гравця й усіх поруч
 * у повітря. Приземлення після власного гейзера не шкодить.
 *
 * Id лишився 'ocean_grace', щоб не переробляти капелюх в Oraxen. Якщо міняти —
 * треба правити і предмет, і видалити старий mechanics/ocean_grace.yml.
 */
public final class OceanGraceMechanic extends Mechanic {

    private final Map<UUID, BukkitRunnable> charging = new ConcurrentHashMap<>();
    private final Map<UUID, Long> readyAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> softLanding = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "ocean_grace";
    }

    @Override
    public String getName() {
        return "Гейзер";
    }

    @Override
    public String getDescription() {
        return "Присядь у воді — тиск набереться; відпустиш, і стовп окропу викине тебе в небо";
    }

    @Override
    public int getMaxLevel() {
        return 1;
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
    public Map<String, Object> getDefaults() {
        return Map.of(
            "charge-ticks", 20,
            "cooldown-ticks", 160,
            "radius", 3.5,
            "player-power", 1.55,
            "mob-power", 1.15,
            "plume-height", 12,
            "soft-landing-seconds", 8
        );
    }

    @Override
    public double getEnchantingTableChance() {
        return 0.06;
    }

    @Override
    public int getWeight() {
        return 8;
    }

    @Override
    public double getChestLootChance() {
        return 0.06;
    }

    @Override
    public List<String> getLootChests() {
        return List.of(
            "minecraft:chests/underwater_ruin_small",
            "minecraft:chests/shipwreck_supply",
            "minecraft:chests/shipwreck_map",
            "minecraft:chests/village/village_fisher"
        );
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of("FISHERMAN", Map.of("villager-level", 3, "min-level", 1, "max-level", 1, "price", 20));
    }

    @Override
    public int getAnvilBaseCost() {
        return 6;
    }

    // ==========================================
    //   ЗАРЯДКА
    // ==========================================

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!isMechanicEnabled()) return;

        Player player = e.getPlayer();
        UUID id = player.getUniqueId();

        if (!e.isSneaking()) {
            BukkitRunnable task = charging.remove(id);
            if (task != null) {
                task.cancel();
                erupt(player);
            }
            return;
        }

        if (levelOnPlayer(player) == 0) return;
        if (!player.isInWater()) return;
        if (charging.containsKey(id)) return;

        long now = player.getWorld().getFullTime();
        Long ready = readyAt.get(id);
        if (ready != null && now < ready) return;

        Charge charge = new Charge(player);
        charging.put(id, charge);
        charge.runTaskTimer(getPlugin(), 0L, 2L);
    }

    /** Тиск наростає, поки гравець сидить у воді; варто виринути чи встати — усе марно. */
    private final class Charge extends BukkitRunnable {

        private final Player player;
        private int ticks;

        private Charge(Player player) {
            this.player = player;
        }

        @Override
        public void run() {
            if (!player.isOnline() || player.isDead() || !player.isInWater() || levelOnPlayer(player) == 0) {
                charging.remove(player.getUniqueId());
                cancel();
                return;
            }

            ticks += 2;
            Location at = player.getLocation();
            World world = at.getWorld();

            double density = Math.min(1.0, (double) ticks / Math.max(2, getInt("charge-ticks")));
            world.spawnParticle(Particle.BUBBLE_COLUMN_UP, at, (int) (4 + density * 10), 0.35, 0.1, 0.35, 0.02);
            world.spawnParticle(Particle.BUBBLE_POP, at, (int) (2 + density * 6), 0.4, 0.2, 0.4, 0);

            if (ticks % 6 == 0) {
                world.playSound(at, Sound.BLOCK_LAVA_EXTINGUISH, 0.35f, 0.6f + (float) density * 0.9f);
            }

            if (ticks >= getInt("charge-ticks")) {
                player.sendActionBar(Component.text("§b✦ Тиск набрано"));
                world.playSound(at, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 0.8f, 1.6f);
                cancel(); // далі просто чекаємо, поки гравець встане
            }
        }
    }

    // ==========================================
    //   ВИВЕРЖЕННЯ
    // ==========================================

    private void erupt(Player player) {
        UUID id = player.getUniqueId();
        if (!player.isInWater() || levelOnPlayer(player) == 0) return;

        Location base = player.getLocation();
        World world = base.getWorld();

        readyAt.put(id, world.getFullTime() + getInt("cooldown-ticks"));
        softLanding.put(id, System.currentTimeMillis() + Math.max(1, getInt("soft-landing-seconds")) * 1000L);

        // емітер сам малює основу, пухкання й плюмаж — рівно як у природного гейзера
        world.spawnParticle(Particle.GEYSER, base, 1);
        world.spawnParticle(Particle.GEYSER_BASE, base, 25, 0.6, 0.1, 0.6, 0.05);

        int height = Math.max(2, getInt("plume-height"));
        for (int y = 0; y < height; y++) {
            Location step = base.clone().add(0, y, 0);
            world.spawnParticle(Particle.GEYSER_PLUME, step, 3, 0.25, 0.2, 0.25, 0.03);
            if (y % 3 == 0) {
                world.spawnParticle(Particle.GEYSER_POOF, step, 4, 0.5, 0.1, 0.5, 0.02);
            }
        }

        world.playSound(base, Sound.ENTITY_GENERIC_SPLASH, 1.4f, 0.7f);
        world.playSound(base, Sound.BLOCK_LAVA_EXTINGUISH, 1.2f, 0.5f);
        world.playSound(base, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, 1.0f, 0.8f);

        double radius = getDouble("radius");
        double mobPower = getDouble("mob-power");

        for (Entity nearby : world.getNearbyEntities(base, radius, radius * 1.5, radius)) {
            if (!(nearby instanceof LivingEntity victim) || victim.equals(player)) continue;
            Vector push = victim.getVelocity().clone();
            victim.setVelocity(push.setY(Math.max(push.getY(), mobPower)));
        }

        Vector own = player.getVelocity().clone();
        player.setVelocity(own.setY(getDouble("player-power")));
    }

    // ==========================================
    //   ПРИЗЕМЛЕННЯ ТА ПРИБИРАННЯ
    // ==========================================

    @EventHandler(ignoreCancelled = true)
    public void onLand(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(e.getEntity() instanceof Player player)) return;

        Long until = softLanding.get(player.getUniqueId());
        if (until == null) return;

        if (System.currentTimeMillis() > until) {
            softLanding.remove(player.getUniqueId());
            return;
        }

        softLanding.remove(player.getUniqueId());
        e.setCancelled(true);
        player.getWorld().spawnParticle(Particle.SPLASH, player.getLocation(), 20, 0.4, 0.1, 0.4, 0.05);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 0.7f, 1.2f);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        BukkitRunnable task = charging.remove(id);
        if (task != null) task.cancel();
        readyAt.remove(id);
        softLanding.remove(id);
    }
}