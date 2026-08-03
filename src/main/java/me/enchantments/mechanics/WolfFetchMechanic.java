package me.enchantments.mechanics;

import me.enchantments.main.Enchantments;
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

import java.util.*;

public final class WolfFetchMechanic extends Mechanic {

    @Override
    public String getId() {
        return "wolf_fetch";
    }

    @Override
    public String getName() {
        return "Хороший хлопчик";
    }

    @Override
    public String getDescription() {
        return "Вовк автоматично носить предмети власнику";
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
            "radius", 50.0,
            "item-age-threshold", 200
        );
    }

    @Override
    protected void onEnable() {
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
            double fetchRadius = getDouble("radius");
            int itemAgeThreshold = getInt("item-age-threshold");
            List<Material> fetchExcluded = getMaterials();

            wolf.getWorld().getNearbyEntities(wolf.getLocation(), fetchRadius, fetchRadius, fetchRadius).stream()
                    .filter(entity -> isValidItem(entity, itemAgeThreshold, fetchExcluded))
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

        private boolean isValidItem(org.bukkit.entity.Entity entity, int itemAgeThreshold, List<Material> fetchExcluded) {
            if (entity.getTicksLived() < itemAgeThreshold) return false;
            if (entity instanceof Item item) return !fetchExcluded.contains(item.getItemStack().getType());
            return entity instanceof Arrow || entity instanceof ExperienceOrb;
        }

        private List<Material> getMaterials() {
            String excluded = getString("excluded-items");
            if (excluded.isEmpty()) return List.of();
            return Arrays.stream(excluded.split(","))
                    .map(String::trim)
                    .map(Material::matchMaterial)
                    .filter(m -> m != null)
                    .toList();
        }
    }
}