package me.enchantments.mechanics;

import me.enchantments.main.Enchantments;
import me.enchantments.utils.EnchantMeta;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AxeCleaveMechanic extends Mechanic {

    private final double cleaveRadius;
    private final double cleaveDamage;
    private final Map<UUID, Long> activeCleavePulls = new ConcurrentHashMap<>();

    public AxeCleaveMechanic(EnchantMeta meta) {
        super(meta);
        var params = meta.triggers().isEmpty() ? null : meta.triggers().get(0);
        cleaveRadius = params != null ? params.getDouble("cleave_radius", 2.0) : 2.0;
        cleaveDamage = params != null ? params.getDouble("cleave_damage", 3.0) : 3.0;

        // Запусти task для постійної перевірки шкоди
        new CleavePulseTask().runTaskTimer(Enchantments.getInstance(), 0L, 2L);
    }

    @EventHandler
    public void onThrow(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack axe = p.getInventory().getItemInMainHand();
        if (levelOf(axe) == 0) return;

        activeCleavePulls.put(p.getUniqueId(), System.currentTimeMillis());
    }

    private final class CleavePulseTask extends BukkitRunnable {
        @Override
        public void run() {
            Bukkit.getOnlinePlayers().forEach(player -> {
                UUID uuid = player.getUniqueId();
                if (!activeCleavePulls.containsKey(uuid)) return;

                player.getWorld().getNearbyEntities(player.getEyeLocation(), cleaveRadius, cleaveRadius, cleaveRadius).stream()
                        .filter(ent -> ent instanceof LivingEntity && ent != player)
                        .map(ent -> (LivingEntity) ent)
                        .forEach(ent -> ent.damage(cleaveDamage, player));

                if (System.currentTimeMillis() - activeCleavePulls.get(uuid) > 2500) {
                    activeCleavePulls.remove(uuid);
                }
            });
        }
    }
}
