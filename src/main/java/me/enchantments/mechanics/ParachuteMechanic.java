package me.enchantments.mechanics;

import me.enchantments.main.Enchantments;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ParachuteMechanic extends Mechanic {

    private final Map<UUID, List<Chicken>> active = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

    @Override
    public String getId() {
        return "parachute";
    }

    @Override
    public String getName() {
        return "Парашут";
    }

    @Override
    public String getDescription() {
        return "Дозволяє керованого опускатися на парашуті";
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
        return List.of(Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE, Material.IRON_CHESTPLATE, Material.COPPER_CHESTPLATE,
                       Material.GOLDEN_CHESTPLATE, Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.CHEST);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "duration-ticks", 100,
            "cooldown-ticks", 200,
            "fall-distance-trigger", 3.0,
            "descend-speed", 0.3
        );
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!isMechanicEnabled()) return;
        Player p = e.getPlayer();
        if (p.isFlying() || p.getGameMode() != GameMode.SURVIVAL || p.isOnGround()) return;
        if (p.getLocation().subtract(0, 1, 0).getBlock().getType().isSolid()) return;
        
        double fallDistanceTrigger = getDouble("fall-distance-trigger");
        if (p.getFallDistance() <= fallDistanceTrigger) return;
        if (levelOf(p.getInventory().getChestplate()) == 0) return;
        open(p);
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.FALL && p.hasMetadata("parachute")) {
            close(p);
            e.setCancelled(true);
        }
    }

    private void open(Player p) {
        UUID uid = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long readyAt = cooldownUntil.get(uid);
        if (readyAt != null && readyAt > now) return;

        int durationTicks = getInt("duration-ticks");
        int cooldownTicks = getInt("cooldown-ticks");
        double descendSpeed = getDouble("descend-speed");

        p.setMetadata("parachute", new FixedMetadataValue(Enchantments.getInstance(), true));
        List<Chicken> chickens = Stream.of(0, 1, -1).map(x -> spawn(p, x)).collect(Collectors.toList());
        active.put(uid, chickens);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PARROT_FLY, 1f, 1f);

        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t > durationTicks || p.isGliding() || !p.isSneaking() || p.isOnGround()
                        || p.getLocation().subtract(0, 1, 0).getBlock().getType().isSolid() || !active.containsKey(uid)) {
                    close(p);
                    cooldownUntil.put(uid, System.currentTimeMillis() + cooldownTicks * 50L);
                    cancel();
                    return;
                }
                Vector v = p.getVelocity();
                p.setVelocity(new Vector(v.getX(), -descendSpeed, v.getZ()));
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 1);
                p.setFallDistance(0);
                t++;
            }
        }.runTaskTimer(Enchantments.getInstance(), 0L, 1L);
    }

    private void close(Player p) {
        active.getOrDefault(p.getUniqueId(), List.of()).forEach(c -> { c.setLeashHolder(null); c.remove(); });
        active.remove(p.getUniqueId());
        if (p.hasMetadata("parachute")) p.removeMetadata("parachute", Enchantments.getInstance());
    }

    private Chicken spawn(Player p, int offsetX) {
        Chicken c = p.getWorld().spawn(p.getLocation().add(offsetX, 2, 0), Chicken.class);
        c.setInvulnerable(true);
        c.setLeashHolder(p);
        return c;
    }
}
