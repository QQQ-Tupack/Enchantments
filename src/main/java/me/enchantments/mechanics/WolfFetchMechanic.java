package me.enchantments.mechanics;

import me.enchantments.main.Enchantments;
import me.enchantments.utils.EnchantMeta;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Comparator;
import java.util.List;

public final class WolfFetchMechanic extends Mechanic {

    private final double fetchRadius;
    private final int itemAgeThreshold;
    private final List<Material> fetchExcluded;

    public WolfFetchMechanic(EnchantMeta meta) {
        super(meta);
        var params = meta.triggers().isEmpty() ? null : meta.triggers().get(0);
        fetchRadius = params != null ? params.getDouble("radius", 50) : 50;
        itemAgeThreshold = params != null ? params.getInt("item_age_threshold", 200) : 200;
        fetchExcluded = params != null ? params.getStringList("excluded_items").stream()
                .map(Material::matchMaterial).filter(m -> m != null).toList() : List.of();

        new WolfItemFetcher().runTaskTimer(Enchantments.getInstance(), 0L, 100L);
    }

    private ItemStack armor(Wolf wolf) {
        return wolf.getEquipment() == null ? null : wolf.getEquipment().getItem(EquipmentSlot.BODY);
    }

    private boolean guarded(Wolf wolf) {
        return wolf.isTamed() && levelOf(armor(wolf)) > 0;
    }

    private final class WolfItemFetcher extends BukkitRunnable {
        @Override
        public void run() {
            Bukkit.getOnlinePlayers().forEach(player ->
                    player.getWorld().getEntitiesByClass(Wolf.class).stream()
                            .filter(w -> w.isTamed() && player.equals(w.getOwner()) && guarded(w) && !w.isSitting() && !w.isAngry())
                            .forEach(wolf -> fetchNearest(player, wolf)));
        }

        private void fetchNearest(Player player, Wolf wolf) {
            wolf.getWorld().getNearbyEntities(wolf.getLocation(), fetchRadius, fetchRadius, fetchRadius).stream()
                    .filter(this::isValidItem)
                    .filter(entity -> entity.getLocation().distance(player.getLocation()) >= 5)
                    .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(wolf.getLocation())))
                    .ifPresent(entity -> {
                        wolf.teleport(entity.getLocation());
                        wolf.setAI(false);
                        new BukkitRunnable() {
                            @Override public void run() {
                                wolf.teleport(player.getLocation().add(0, 0, 1));
                                if (entity instanceof Arrow arrow) {
                                    player.getInventory().addItem(arrow.getItemStack());
                                    entity.remove();
                                } else {
                                    entity.teleport(player.getLocation());
                                }
                                wolf.setAI(true);
                            }
                        }.runTaskLater(Enchantments.getInstance(), 20L);
                    });
        }

        private boolean isValidItem(org.bukkit.entity.Entity entity) {
            if (entity.getTicksLived() < itemAgeThreshold) return false;
            if (entity instanceof Item item) return !fetchExcluded.contains(item.getItemStack().getType());
            return entity instanceof Arrow || entity instanceof ExperienceOrb;
        }
    }
}
