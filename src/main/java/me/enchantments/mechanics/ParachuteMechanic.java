package me.enchantments.mechanics;

import me.enchantments.main.Enchantments;
import me.enchantments.utils.EnchantMeta;
import org.bukkit.GameMode;
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

    private final int durationTicks, cooldownTicks;
    private final double fallDistanceTrigger, descendSpeed;

    private final Map<UUID, List<Chicken>> active = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

    public ParachuteMechanic(EnchantMeta meta) {
        super(meta);
        var params = meta.triggers().isEmpty() ? null : meta.triggers().get(0);
        durationTicks = params != null ? params.getInt("duration_ticks", 100) : 100;
        cooldownTicks = params != null ? params.getInt("cooldown_ticks", 200) : 200;
        fallDistanceTrigger = params != null ? params.getDouble("fall_distance_trigger", 3.0) : 3.0;
        descendSpeed = params != null ? params.getDouble("descend_speed", 0.3) : 0.3;
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (p.isFlying() || p.getGameMode() != GameMode.SURVIVAL || p.isOnGround()) return;
        if (p.getLocation().subtract(0, 1, 0).getBlock().getType().isSolid()) return;
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
