package me.enchantments.mechanics;

import me.enchantments.utils.EnchantMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
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

public final class WolfCompanionMechanic extends Mechanic implements CommandExecutor {

    private final boolean protectFromOwnerDamage, protectFromOwnerProjectiles, protectSuffocation, protectNamedTarget;
    private final String command;

    public WolfCompanionMechanic(EnchantMeta meta) {
        super(meta);
        var params = meta.triggers().isEmpty() ? null : meta.triggers().get(0);
        protectFromOwnerDamage = params != null ? params.getBoolean("protect_from_owner_damage", true) : true;
        protectFromOwnerProjectiles = params != null ? params.getBoolean("protect_from_owner_projectiles", true) : true;
        protectSuffocation = params != null ? params.getBoolean("protect_suffocation", true) : true;
        protectNamedTarget = params != null ? params.getBoolean("protect_named_target", true) : true;
        command = params != null ? params.getString("command", "pet") : "pet";
    }

    @Override
    public String commandName() { return command; }

    private ItemStack armor(Wolf wolf) {
        return wolf.getEquipment() == null ? null : wolf.getEquipment().getItem(EquipmentSlot.BODY);
    }

    private boolean guarded(Wolf wolf) {
        return wolf.isTamed() && levelOf(armor(wolf)) > 0;
    }

    @EventHandler
    public void onWolfAttack(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Wolf wolf) || !(e.getDamager() instanceof Player player)) return;
        if (protectFromOwnerDamage && guarded(wolf) && player.equals(wolf.getOwner())) e.setCancelled(true);
    }

    @EventHandler
    public void onWolfTarget(EntityTargetEvent e) {
        if (!(e.getEntity() instanceof Wolf wolf) || !guarded(wolf) || !protectNamedTarget) return;
        if (e.getTarget() != null && e.getTarget().customName() != null) e.setCancelled(true);
    }

    @EventHandler
    public void onWolfDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Wolf wolf) || !guarded(wolf)) return;

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

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        boolean teleported = player.getWorld().getEntitiesByClass(Wolf.class).stream()
                .filter(w -> w.isTamed() && player.equals(w.getOwner()) && guarded(w) && !w.isSitting())
                .peek(w -> w.teleport(player.getLocation()))
                .count() > 0;
        player.sendMessage(teleported
                ? Component.text("Вовки прикликані!", NamedTextColor.GREEN)
                : Component.text("Немає прикликаних вовків із зачарованою бронею.", NamedTextColor.RED));
        return true;
    }
}
