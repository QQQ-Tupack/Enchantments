package me.enchantments.mechanics;

import me.enchantments.main.Enchantments;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerItemConsumeEvent;

import java.util.List;
import java.util.Map;

public final class SaturationMechanic extends Mechanic {

    @Override
    public String getId() {
        return "saturation_bonus";
    }

    @Override
    public String getName() {
        return "Гарний апетит";
    }

    @Override
    public String getDescription() {
        return "Дає невеликий додатковий бонус ситості при споживанні їжі";
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET, Material.IRON_HELMET,
                       Material.GOLDEN_HELMET, Material.DIAMOND_HELMET, Material.NETHERITE_HELMET);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.HEAD);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "food-bonus", 1,
            "food-bonus-per-level", 1,
            "saturation-bonus", 0.5
        );
    }

    @EventHandler
    public void onEat(PlayerItemConsumeEvent e) {
        if (!isMechanicEnabled()) return;
        Player player = e.getPlayer();
        if (!e.getItem().getType().isEdible()) return;

        int level = levelOf(player.getInventory().getHelmet());
        if (level == 0) return;

        int foodBonus = getInt("food-bonus") + getInt("food-bonus-per-level") * (level - 1);
        double saturationBonus = getDouble("saturation-bonus");

        Bukkit.getScheduler().runTask(Enchantments.getInstance(), () -> {
            player.setFoodLevel(Math.min(20, player.getFoodLevel() + foodBonus));
            player.setSaturation((float) Math.min(player.getFoodLevel(), player.getSaturation() + saturationBonus));
        });
    }
}
