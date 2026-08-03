package me.enchantments.mechanics;

import me.enchantments.main.Enchantments;
import me.enchantments.utils.EnchantMeta;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public final class ParryMechanic extends Mechanic {

    private record Attack(Entity damager, double damage) {}

    private final int windowTicks, shieldDamage, failCooldownTicks, replayDelayStepTicks;
    private final double counterDamage, counterRange, knockbackMult, knockbackMinY;

    private final Map<UUID, Map<UUID, Attack>> windows = new HashMap<>();
    private final Set<UUID> replaying = new HashSet<>();
    private final Set<UUID> cooldown = new HashSet<>();

    public ParryMechanic(EnchantMeta meta) {
        super(meta);
        var params = meta.triggers().isEmpty() ? null : meta.triggers().get(0);
        windowTicks = params != null ? params.getInt("window_ticks", 10) : 10;
        shieldDamage = params != null ? params.getInt("shield_damage", 3) : 3;
        counterDamage = params != null ? params.getDouble("counter_damage", 1.0) : 1.0;
        counterRange = params != null ? params.getDouble("counter_range", 3.0) : 3.0;
        knockbackMult = params != null ? params.getDouble("knockback_mult", 1.5) : 1.5;
        knockbackMinY = params != null ? params.getDouble("knockback_min_y", 0.3) : 0.3;
        failCooldownTicks = params != null ? params.getInt("fail_cooldown_ticks", 20) : 20;
        replayDelayStepTicks = params != null ? params.getInt("replay_delay_step_ticks", 2) : 2;
    }

    private ItemStack shield(Player p) {
        if (p.getInventory().getItemInMainHand().getType() == Material.SHIELD) return p.getInventory().getItemInMainHand();
        if (p.getInventory().getItemInOffHand().getType() == Material.SHIELD) return p.getInventory().getItemInOffHand();
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player player) || e.isCancelled()) return;
        UUID uid = player.getUniqueId();
        if (replaying.contains(uid) || cooldown.contains(uid) || player.isBlocking()) return;
        if (levelOf(shield(player)) == 0) return;

        UUID attackerId = e.getDamager() instanceof LivingEntity ? e.getDamager().getUniqueId() : null;
        if (attackerId == null) return;

        Map<UUID, Attack> window = windows.get(uid);
        if (window == null) {
            window = new HashMap<>();
            windows.put(uid, window);
            openWindow(player);
        } else if (window.containsKey(attackerId)) {
            return;
        }
        e.setCancelled(true);
        window.put(attackerId, new Attack(e.getDamager(), e.getFinalDamage()));
    }

    private void openWindow(Player player) {
        UUID uid = player.getUniqueId();
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!windows.containsKey(uid)) { cancel(); return; }
                if (!player.isOnline() || player.isDead()) { fail(player); cancel(); return; }
                if (player.isBlocking()) { succeed(player); cancel(); return; }
                if (++t >= windowTicks) { fail(player); cancel(); }
            }
        }.runTaskTimer(Enchantments.getInstance(), 1, 1);
    }

    private void succeed(Player player) {
        Map<UUID, Attack> window = windows.remove(player.getUniqueId());
        if (window == null) return;

        damageShield(player, shieldDamage);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.5f, 0.8f);
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);

        for (Attack a : window.values()) {
            if (!(a.damager() instanceof LivingEntity attacker) || !attacker.isValid()) continue;
            attacker.damage(counterDamage);
            if (player.getLocation().distance(attacker.getLocation()) < counterRange) {
                Vector kb = attacker.getLocation().subtract(player.getLocation()).toVector().normalize().multiply(knockbackMult);
                kb.setY(Math.max(kb.getY(), knockbackMinY));
                attacker.setVelocity(kb);
            }
        }
    }

    private void fail(Player player) {
        Map<UUID, Attack> window = windows.remove(player.getUniqueId());
        if (window == null) return;

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f);
        UUID uid = player.getUniqueId();
        cooldown.add(uid);

        int delay = 0;
        for (Attack a : window.values()) {
            if (!a.damager().isValid()) continue;
            int currentDelay = delay;
            Bukkit.getScheduler().runTaskLater(Enchantments.getInstance(), () -> {
                if (!player.isOnline() || player.isDead() || !a.damager().isValid()) return;
                replaying.add(uid);
                try { player.damage(a.damage(), a.damager()); } finally { replaying.remove(uid); }
            }, currentDelay);
            delay += replayDelayStepTicks;
        }
        Bukkit.getScheduler().runTaskLater(Enchantments.getInstance(), () -> cooldown.remove(uid), failCooldownTicks);
    }

    private void damageShield(Player player, int amount) {
        ItemStack shield = shield(player);
        if (shield == null || !(shield.getItemMeta() instanceof Damageable d)) return;
        int newDamage = d.getDamage() + amount;
        if (newDamage >= shield.getType().getMaxDurability()) shield.setAmount(0);
        else { d.setDamage(newDamage); shield.setItemMeta((ItemMeta) d); }
    }

    public void shutdown() {
        for (UUID uid : new ArrayList<>(windows.keySet())) {
            Player player = Bukkit.getPlayer(uid);
            if (player != null) fail(player);
        }
        windows.clear();
        replaying.clear();
        cooldown.clear();
    }
}
