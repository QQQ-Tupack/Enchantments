package me.enchantments.mechanics;

import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class WolfCompanionMechanic extends Mechanic {

    @Override
    public String getId() {
        return "wolf_companion";
    }

    @Override
    public String getName() {
        return "Вірна дружба";
    }

    @Override
    public String getDescription() {
        return "Вовк отримує захист від випадкових атак власника";
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.WOLF_ARMOR);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.BODY);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "protect-from-owner-damage", true,
            "protect-from-owner-projectiles", true,
            "protect-suffocation", true,
            "protect-named-target", true,
            "command", "pet"
        );
    }

    private ItemStack armor(Wolf wolf) {
        return wolf.getEquipment() == null ? null : wolf.getEquipment().getItem(EquipmentSlot.BODY);
    }

    private boolean guarded(Wolf wolf) {
        return wolf.isTamed() && levelOf(armor(wolf)) > 0;
    }

    @EventHandler
    public void onWolfAttack(EntityDamageByEntityEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getEntity() instanceof Wolf wolf) || !(e.getDamager() instanceof Player player)) return;
        boolean protectFromOwnerDamage = getBoolean("protect-from-owner-damage");
        if (protectFromOwnerDamage && guarded(wolf) && player.equals(wolf.getOwner())) e.setCancelled(true);
    }

    @EventHandler
    public void onWolfTarget(EntityTargetEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getEntity() instanceof Wolf wolf) || !guarded(wolf)) return;
        boolean protectNamedTarget = getBoolean("protect-named-target");
        if (!protectNamedTarget) return;
        if (e.getTarget() != null && e.getTarget().customName() != null) e.setCancelled(true);
    }

    @EventHandler
    public void onWolfDamage(EntityDamageEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getEntity() instanceof Wolf wolf) || !guarded(wolf)) return;

        boolean protectFromOwnerProjectiles = getBoolean("protect-from-owner-projectiles");
        boolean protectSuffocation = getBoolean("protect-suffocation");

        if (protectFromOwnerProjectiles && e.getCause() == EntityDamageEvent.DamageCause.PROJECTILE
                && e instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Projectile proj
                && proj.getShooter() instanceof Player shooter && shooter.equals(wolf.getOwner())) {
            e.setCancelled(true);
        }
        if (protectSuffocation && e.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) e.setCancelled(true);
    }

    @EventHandler
    public void onChangeWorld(PlayerChangedWorldEvent e) {
        Player player = e.getPlayer();
        e.getFrom().getEntitiesByClass(Wolf.class).stream()
                .filter(w -> w.isTamed() && player.equals(w.getOwner()) && guarded(w) && !w.isSitting())
                .forEach(w -> w.teleport(player.getLocation()));
    }
}
