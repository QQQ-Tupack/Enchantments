package me.enchantments.mechanics;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
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

public final class AxePullMechanic extends Mechanic {

    private final Map<UUID, Long> activePulls = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "axe_pull";
    }

    @Override
    public String getName() {
        return "Притягання предметів";
    }

    @Override
    public String getDescription() {
        return "Автоматично притягує предмети та досвід до гравця";
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    protected boolean hideEnchantmentGlintDefault() {
        return false;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE, 
                       Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.MAINHAND);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "collect-radius", 1.5
        );
    }

    @Override
    protected void onEnable() {
        new PullTask().runTaskTimer(Enchantments.getInstance(), 0L, 1L);
    }

    @EventHandler
    public void onThrow(PlayerInteractEvent e) {
        if (!isMechanicEnabled()) return;
        Player p = e.getPlayer();
        ItemStack axe = p.getInventory().getItemInMainHand();
        if (levelOf(axe) == 0) return;
        
        activePulls.put(p.getUniqueId(), System.currentTimeMillis());
    }

    private final class PullTask extends BukkitRunnable {
        @Override
        public void run() {
            double collectRadius = getDouble("collect-radius");
            Bukkit.getOnlinePlayers().forEach(player -> {
                UUID uuid = player.getUniqueId();
                if (!activePulls.containsKey(uuid)) return;
                
                player.getWorld().getNearbyEntities(player.getLocation(), collectRadius, collectRadius, collectRadius).stream()
                        .filter(ent -> ent instanceof ExperienceOrb || ent instanceof Item || ent instanceof Arrow)
                        .forEach(ent -> {
                            if (ent instanceof Item item && item.getPickupDelay() > 0) return;
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
