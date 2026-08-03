package me.enchantments.mechanics;

import me.enchantments.main.Enchantments;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AxeCleaveMechanic extends Mechanic {

    private final Map<UUID, Long> activeCleavePulls = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "axe_cleave";
    }

    @Override
    public String getName() {
        return "Розтинання";
    }

    @Override
    public String getDescription() {
        return "Наносить урон усім ворогам навколо при активації";
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
        return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "cleave-radius", 2.0,
            "cleave-damage", 3.0
        );
    }

    @Override
    protected void onEnable() {
        new CleavePulseTask().runTaskTimer(Enchantments.getInstance(), 0L, 2L);
    }

    @EventHandler
    public void onThrow(PlayerInteractEvent e) {
        if (!isMechanicEnabled()) return;
        Player p = e.getPlayer();
        ItemStack axe = p.getInventory().getItemInMainHand();
        if (levelOf(axe) == 0) return;

        activeCleavePulls.put(p.getUniqueId(), System.currentTimeMillis());
    }

    private final class CleavePulseTask extends BukkitRunnable {
        @Override
        public void run() {
            double cleaveRadius = getDouble("cleave-radius");
            double cleaveDamage = getDouble("cleave-damage");
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
