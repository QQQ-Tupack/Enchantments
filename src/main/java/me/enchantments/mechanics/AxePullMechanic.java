package me.enchantments.mechanics;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.enchantments.main.Enchantments;
import me.enchantments.utils.EnchantMeta;

public final class AxePullMechanic extends Mechanic {

    private final double collectRadius;
    private final Map<UUID, Long> activePulls = new ConcurrentHashMap<>();

    public AxePullMechanic(EnchantMeta meta) {
        super(meta);
        var params = meta.triggers().isEmpty() ? null : meta.triggers().get(0);
        collectRadius = params != null ? params.getDouble("collect_radius", 1.5) : 1.5;

        new PullTask().runTaskTimer(Enchantments.getInstance(), 0L, 1L);
    }

    @EventHandler
    public void onThrow(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack axe = p.getInventory().getItemInMainHand();
        if (levelOf(axe) == 0) return;
        
        activePulls.put(p.getUniqueId(), System.currentTimeMillis());
    }

    private final class PullTask extends BukkitRunnable {
        @Override
        public void run() {
            Bukkit.getOnlinePlayers().forEach(player -> {
                UUID uuid = player.getUniqueId();
                if (!activePulls.containsKey(uuid)) return;
                
                player.getWorld().getNearbyEntities(player.getLocation(), collectRadius, collectRadius, collectRadius).stream()
                        .filter(ent -> ent instanceof ExperienceOrb || ent instanceof Item || ent instanceof Arrow)
                        .forEach(ent -> {
                            if (ent instanceof Item item && item.getPickupDelay() > 0) return; // Пропусти недавно кинуті предмети
                            Vector v = player.getEyeLocation().toVector().subtract(ent.getLocation().toVector());
                            if (v.lengthSquared() > 1e-6) ent.setVelocity(v.normalize().multiply(0.8));
                        });
                
                if (System.currentTimeMillis() - activePulls.get(uuid) > 2000) {
                    activePulls.remove(uuid);
                }
            });
        }
    }
}
