package me.enchantments.mechanics;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import me.enchantments.main.Mechanic;

import java.util.List;
import java.util.Map;

public final class FortunesFavorMechanic extends Mechanic {

    private NamespacedKey progressKey;

    @Override public String getId() { return "fortunes_favor"; }
    @Override public String getName() { return "Ласка фортуни"; }
    @Override public String getDescription() { return "Накопичує щедрість за видобуту руду; на порозі наступна руда дає подвійний дроп"; }
    @Override public int getMaxLevel() { return 1; }
    @Override public List<Material> getSupportedItems() { return List.of(Material.TOTEM_OF_UNDYING); }
    @Override public List<EnchantSlot> getSlots() { return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND, EnchantSlot.HEAD); }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of("payout-threshold", 48);
    }

    @Override public double getEnchantingTableChance() { return 0.03; }
    @Override public int getWeight() { return 4; }
    @Override public double getChestLootChance() { return 0.04; }

    @Override
    public List<String> getLootChests() {
        return List.of(
            "minecraft:chests/abandoned_mineshaft",
            "minecraft:chests/buried_treasure",
            "minecraft:chests/ruined_portal",
            "minecraft:chests/village/village_toolsmith"
        );
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of(
            "MASON", Map.of("villager-level", 5, "min-level", 1, "max-level", 1, "price", 36),
            "TOOLSMITH", Map.of("villager-level", 4, "min-level", 1, "max-level", 1, "price", 30)
        );
    }

    @Override public int getAnvilBaseCost() { return 10; }

    @Override
    protected void onEnable() {
        progressKey = new NamespacedKey(getPlugin(), "fortunes_favor_progress");
    }

    @Override
    protected void onBlockBreak(BlockBreakEvent event, Player player, int level) {
        if (player.getGameMode() != GameMode.SURVIVAL) return;

        Block block = event.getBlock();
        if (!isOre(block.getType())) return;

        int threshold = Math.max(1, getInt("payout-threshold"));
        var pdc = player.getPersistentDataContainer();
        int progress = pdc.getOrDefault(progressKey, PersistentDataType.INTEGER, 0) + 1;

        if (progress >= threshold) {
            progress = 0;
            ItemStack tool = player.getInventory().getItemInMainHand();
            for (ItemStack drop : block.getDrops(tool, player)) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
            }
            particles(block.getLocation().add(0.5, 0.7, 0.5), Particle.WAX_OFF, 12, 0.3);
            soundAt(block.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.6f);
        }

        pdc.set(progressKey, PersistentDataType.INTEGER, progress);
    }

    private boolean isOre(Material material) {
        return material == Material.ANCIENT_DEBRIS || material.name().endsWith("_ORE");
    }
}
