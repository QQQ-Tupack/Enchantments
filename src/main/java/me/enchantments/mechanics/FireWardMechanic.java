package me.enchantments.mechanics;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public final class FireWardMechanic extends Mechanic {

    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

    @Override
    public String getId() {
        return "fire_ward";
    }

    @Override
    public String getName() {
        return "Вартовий вогню";
    }

    @Override
    public String getDescription() {
        return "При отриманні шкоди від вогню тимчасово дає вогнестійкість";
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
        return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "duration-ticks", 200,
            "duration-ticks-per-level", 40,
            "cooldown-ticks", 1200,
            "cooldown-reduction-per-level-ticks", 200
        );
    }

    @EventHandler
    public void onFireDamage(EntityDamageEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getEntity() instanceof Player player)) return;

        EntityDamageEvent.DamageCause cause = e.getCause();
        if (cause != EntityDamageEvent.DamageCause.FIRE
                && cause != EntityDamageEvent.DamageCause.FIRE_TICK
                && cause != EntityDamageEvent.DamageCause.LAVA) return;

        int level = levelOf(player.getInventory().getChestplate());
        if (level == 0) return;

        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long readyAt = cooldownUntil.get(uid);
        if (readyAt != null && readyAt > now) return;

        int durationTicks = getInt("duration-ticks") + getInt("duration-ticks-per-level") * (level - 1);
        int cooldownTicks = Math.max(200,
                getInt("cooldown-ticks") - getInt("cooldown-reduction-per-level-ticks") * (level - 1));

        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, durationTicks, 0, false, true, true));
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 1f);
        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);

        cooldownUntil.put(uid, now + cooldownTicks * 50L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cooldownUntil.remove(e.getPlayer().getUniqueId());
    }
}
