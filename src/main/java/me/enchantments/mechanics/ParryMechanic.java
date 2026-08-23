package me.enchantments.mechanics;

import me.enchantments.main.Mechanic;

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

    private final Map<UUID, Map<UUID, Attack>> windows = new HashMap<>();
    private final Set<UUID> replaying = new HashSet<>();

    @Override
    public String getId() {
        return "parry";
    }

    @Override
    public String getName() {
        return "Парування";
    }

    @Override
    public String getDescription() {
        return "Дозволяє гравцю парирувати удари щитом та наносити шкоду атакуючому.";
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.SHIELD);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "window-ticks", 10,
            "shield-damage", 3,
            "counter-damage", 1.0,
            "counter-range", 3.0,
            "knockback-mult", 1.5,
            "knockback-min-y", 0.3,
            "fail-cooldown-ticks", 20,
            "replay-delay-step-ticks", 2
        );
    }

    private ItemStack shield(Player p) {
        if (p.getInventory().getItemInMainHand().getType() == Material.SHIELD) return p.getInventory().getItemInMainHand();
        if (p.getInventory().getItemInOffHand().getType() == Material.SHIELD) return p.getInventory().getItemInOffHand();
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getEntity() instanceof Player player) || e.isCancelled()) return;
        UUID uid = player.getUniqueId();
        if (replaying.contains(uid) || player.isBlocking()) return;

        ItemStack shield = shield(player);
        if (levelOf(shield) == 0 || !ready(player, shield)) return;

        UUID attackerId = e.getDamager() instanceof LivingEntity ? e.getDamager().getUniqueId() : null;
        if (attackerId == null) return;

        Map<UUID, Attack> window = windows.get(uid);
        if (window == null) {
            window = new HashMap<>();
            windows.put(uid, window);
            openWindow(player, shield);
        } else if (window.containsKey(attackerId)) {
            return;
        }
        e.setCancelled(true);
        window.put(attackerId, new Attack(e.getDamager(), e.getFinalDamage()));
    }

    private void openWindow(Player player, ItemStack shield) {
        UUID uid = player.getUniqueId();
        int windowTicks = getInt("window-ticks");
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!windows.containsKey(uid)) { cancel(); return; }
                if (!player.isOnline() || player.isDead()) { fail(player, shield); cancel(); return; }
                if (player.isBlocking()) { succeed(player, shield); cancel(); return; }
                if (++t >= windowTicks) { fail(player, shield); cancel(); }
            }
        }.runTaskTimer(getPlugin(), 1, 1);
    }

    private void succeed(Player player, ItemStack shield) {
        Map<UUID, Attack> window = windows.remove(player.getUniqueId());
        if (window == null) return;

        damageShield(shield, getInt("shield-damage"));
        double counterDamage = getDouble("counter-damage");
        double counterRange = getDouble("counter-range");
        double knockbackMult = getDouble("knockback-mult");
        double knockbackMinY = getDouble("knockback-min-y");

        activation(player);
        soundAt(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.5f, 0.8f);
        particles(player.getLocation().add(0, 1, 0), Particle.CRIT, 50, 0.5);

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

    private void fail(Player player, ItemStack shield) {
        Map<UUID, Attack> window = windows.remove(player.getUniqueId());
        if (window == null) return;

        deny(player);
        UUID uid = player.getUniqueId();
        if (shield != null) cooldown(player, shield, getInt("fail-cooldown-ticks"));

        int delay = 0;
        int replayStep = getInt("replay-delay-step-ticks");
        for (Attack a : window.values()) {
            if (!a.damager().isValid()) continue;
            int currentDelay = delay;
            Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
                if (!player.isOnline() || player.isDead() || !a.damager().isValid()) return;
                replaying.add(uid);
                try { player.damage(a.damage(), a.damager()); } finally { replaying.remove(uid); }
            }, currentDelay);
            delay += replayStep;
        }
    }

    private void damageShield(ItemStack shield, int amount) {
        if (shield == null || !(shield.getItemMeta() instanceof Damageable d)) return;
        int newDamage = d.getDamage() + amount;
        if (newDamage >= shield.getType().getMaxDurability()) shield.setAmount(0);
        else { d.setDamage(newDamage); shield.setItemMeta((ItemMeta) d); }
    }

    public void shutdown() {
        for (UUID uid : new ArrayList<>(windows.keySet())) {
            Player player = Bukkit.getPlayer(uid);
            if (player != null) fail(player, shield(player));
        }
        windows.clear();
        replaying.clear();
    }
}
