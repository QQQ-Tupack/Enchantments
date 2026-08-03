package me.enchantments.mechanics;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerItemConsumeEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SaturationBonusMechanic extends Mechanic {

    /** Лише проста їжа: бонус сильний на старті й нікчемний у лейті, де всі їдять золоту моркву. */
    private static final Set<Material> SIMPLE_FOOD = Set.of(
        Material.BREAD, Material.POTATO, Material.BAKED_POTATO, Material.CARROT,
        Material.BEETROOT, Material.BEETROOT_SOUP, Material.MUSHROOM_STEW,
        Material.RABBIT_STEW, Material.SUSPICIOUS_STEW, Material.APPLE,
        Material.SWEET_BERRIES, Material.GLOW_BERRIES, Material.MELON_SLICE,
        Material.DRIED_KELP, Material.COOKIE, Material.CHORUS_FRUIT,
        Material.BEEF, Material.PORKCHOP, Material.CHICKEN, Material.MUTTON,
        Material.RABBIT, Material.COD, Material.SALMON
    );

    @Override
    public String getId() {
        return "saturation_bonus";
    }

    @Override
    public String getName() {
        return "Селянська трапеза";
    }

    @Override
    public String getDescription() {
        return "Проста їжа насичує помітно довше; вишукані страви бонусу не дають";
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.TOTEM_OF_UNDYING);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND, EnchantSlot.HEAD);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of("saturation-multiplier", 1.35);
    }

    @Override
    public double getEnchantingTableChance() {
        return 0.08;
    }

    @Override
    public int getWeight() {
        return 12;
    }

    @Override
    public double getChestLootChance() {
        return 0.07;
    }

    @Override
    public List<String> getLootChests() {
        return List.of(
            "minecraft:chests/village/village_plains_house",
            "minecraft:chests/village/village_desert_house",
            "minecraft:chests/village/village_savanna_house",
            "minecraft:chests/village/village_taiga_house"
        );
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of("FARMER", Map.of("villager-level", 2, "min-level", 1, "max-level", 1, "price", 14));
    }

    @Override
    public int getAnvilBaseCost() {
        return 4;
    }

    @Override
    public int getAnvilCostPerLevel() {
        return 2;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        if (!isMechanicEnabled()) return;

        Player player = e.getPlayer();
        if (levelOnPlayer(player) == 0) return;
        if (!SIMPLE_FOOD.contains(e.getItem().getType())) return;

        float before = player.getSaturation();
        double multiplier = getDouble("saturation-multiplier");

        Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
            float gained = player.getSaturation() - before;
            if (gained <= 0) return;

            float bonus = (float) (gained * (multiplier - 1.0));
            player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + bonus));
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.2, 0), 4, 0.3, 0.3, 0.3, 0.01);
        }, 1L);
    }
}
