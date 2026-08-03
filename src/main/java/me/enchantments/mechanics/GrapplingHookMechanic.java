package me.enchantments.mechanics;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.enchantments.main.Enchantments;
import me.enchantments.utils.EnchantMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class GrapplingHookMechanic extends Mechanic {

    private final double pullSpeed, launchMultiplier, minEntityDistance, wallImmunityTicks;
    private final int durabilityCost;

    private final Set<UUID> pulling = new HashSet<>();
    private final Set<UUID> wallImmune = new HashSet<>();

    public GrapplingHookMechanic(EnchantMeta meta) {
        super(meta);
        var params = meta.triggers().isEmpty() ? null : meta.triggers().get(0);
        pullSpeed = params != null ? params.getDouble("pull_speed", 1.5) : 1.5;
        launchMultiplier = params != null ? params.getDouble("launch_multiplier", 2.5) : 2.5;
        minEntityDistance = params != null ? params.getDouble("min_entity_distance", 3.0) : 3.0;
        wallImmunityTicks = params != null ? params.getInt("wall_immunity_ticks", 60) : 60;
        durabilityCost = params != null ? params.getInt("durability_cost", 1) : 1;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.FLY_INTO_WALL
                && wallImmune.contains(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (e.getHand() == null) return;
        Player p = e.getPlayer();
        ItemStack rod = p.getInventory().getItem(e.getHand());
        if (levelOf(rod) == 0) return;

        FishHook hook = e.getHook();
        hook.setVelocity(hook.getVelocity().multiply(launchMultiplier));
        p.setFallDistance(0);

        Bukkit.getScheduler().runTaskTimer(Enchantments.getInstance(), task -> {
            if (hook.isDead() || !hook.isValid() || hook.getLocation().distance(p.getLocation()) >= 20) {
                if (!hook.isDead()) hook.remove();
                task.cancel();
            } else if (e.getState() == PlayerFishEvent.State.CAUGHT_ENTITY && e.getCaught() instanceof LivingEntity target) {
                if (target.getLocation().distance(p.getLocation()) < minEntityDistance) {
                    p.sendMessage(Component.text("Ціль занадто близько!", NamedTextColor.RED));
                } else {
                    pullTowards(p, target.getLocation());
                    p.getInventory().setItem(e.getHand(), damage(p, rod, durabilityCost));
                }
                hook.remove();
                task.cancel();
            } else if (hook.isOnGround() || nearSolidBlock(hook.getLocation())) {
                pullTowards(p, hook.getLocation());
                p.getInventory().setItem(e.getHand(), damage(p, rod, durabilityCost));
                hook.remove();
                task.cancel();
            }
        }, 0L, 1L);
    }

    private boolean nearSolidBlock(Location l) {
        return !l.clone().add(1, 0, 0).getBlock().isPassable() || !l.clone().add(-1, 0, 0).getBlock().isPassable()
                || !l.clone().add(0, 1, 0).getBlock().isPassable() || !l.clone().add(0, -1, 0).getBlock().isPassable()
                || !l.clone().add(0, 0, 1).getBlock().isPassable() || !l.clone().add(0, 0, -1).getBlock().isPassable();
    }

    private void pullTowards(Player p, Location target) {
        UUID uid = p.getUniqueId();
        Location from = p.getLocation();
        Vector dir = target.toVector().subtract(from.toVector());
        double distance = dir.length();
        double time = Math.max(distance / pullSpeed, 1);
        dir.normalize();
        Vector velocity = dir.multiply(distance / time);
        velocity.setY((target.getY() - from.getY()) / time + 0.04 * time);

        p.getWorld().spawnParticle(Particle.CRIT, from, 5);
        p.getWorld().playSound(from, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.5f);
        p.setVelocity(velocity);

        pulling.add(uid);
        wallImmune.add(uid);
        Bukkit.getScheduler().runTaskLater(Enchantments.getInstance(), () -> wallImmune.remove(uid), (long) wallImmunityTicks);

        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isValid() || !pulling.contains(uid)) { cancel(); return; }
                if (isOnGround(p)) { pulling.remove(uid); cancel(); }
            }
        }.runTaskTimer(Enchantments.getInstance(), 2L, 1L);
    }

    private boolean isOnGround(Player p) {
        return !p.getLocation().subtract(0, 0.05, 0).getBlock().isPassable();
    }

    private ItemStack damage(Player p, ItemStack item, int amount) {
        if (item == null || !(item.getItemMeta() instanceof Damageable d)) return item;
        int maxDurability = d.hasMaxDamage() ? d.getMaxDamage() : item.getType().getMaxDurability();
        int newDamage = d.getDamage() + amount;
        if (maxDurability > 0 && newDamage >= maxDurability) {
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            return null;
        }
        d.setDamage(newDamage);
        item.setItemMeta(d);
        return item;
    }
}
