package me.enchantments.mechanics;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.List;
import java.util.Map;

public class ExampleMechanic extends Mechanic {

    @Override
    public String getId() {
        return "example_enchant";
    }

    @Override
    public String getName() {
        return "Приклад Зачарування";
    }

    @Override
    public String getDescription() {
        return "Повністю завершений приклад з окремими перемикачами enabled для кожного джерела отримання.";
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
                       Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
                       Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
                       Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.MAINHAND);
    }

    // --- ДЖЕРЕЛА ТА ШАНСИ ---

    @Override
    public boolean isEnchantingTableAllowed() {
        return true;
    }

    @Override
    public double getEnchantingTableChance() {
        return 0.12;
    }

    @Override
    public boolean isChestLootAllowed() {
        return true;
    }

    @Override
    public double getChestLootChance() {
        return 0.05;
    }

    @Override
    public List<String> getLootChests() {
        return List.of(
            "minecraft:chests/end_city_treasure",
            "minecraft:chests/ancient_city"
        );
    }

    @Override
    public boolean isVillagerTradeAllowed() {
        return true;
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of(
            "LIBRARIAN", Map.of(
                "level", 2,
                "min-emeralds", 10,
                "max-emeralds", 24
            )
        );
    }

    // --- ВАРТІСТЬ ТА НАЛАШТУВАННЯ ---

    @Override
    public int getAnvilBaseCost() {
        return 8;
    }

    @Override
    public int getAnvilCostPerLevel() {
        return 4;
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "cooldown-seconds", 5,
            "power-multiplier", 1.5
        );
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!isMechanicEnabled()) return;

        Player player = event.getPlayer();
        int cooldown = getInt("cooldown-seconds");
        double power = getDouble("power-multiplier");

        // Логіка механіки...
    }
}