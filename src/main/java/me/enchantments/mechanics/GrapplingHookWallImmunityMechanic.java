package me.enchantments.mechanics;

import me.enchantments.utils.EnchantMeta;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class GrapplingHookWallImmunityMechanic extends Mechanic {

    private final int wallImmunityTicks;
    private final Set<UUID> wallImmune = new HashSet<>();

    public GrapplingHookWallImmunityMechanic(EnchantMeta meta) {
        super(meta);
        var params = meta.triggers().isEmpty() ? null : meta.triggers().get(0);
        wallImmunityTicks = params != null ? params.getInt("wall_immunity_ticks", 60) : 60;
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

        UUID uid = p.getUniqueId();
        wallImmune.add(uid);
        Bukkit.getScheduler().runTaskLater(me.enchantments.main.Enchantments.getInstance(),
                () -> wallImmune.remove(uid), (long) wallImmunityTicks);
    }
}
