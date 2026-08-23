package me.enchantments.mechanics;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityAirChangeEvent;

import me.enchantments.main.Mechanic;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class AbyssalBreathMechanic extends Mechanic {

    @Override
    public String getId() {
        return "abyssal_breath";
    }

    @Override
    public String getName() {
        return "Подих безодні";
    }

    @Override
    public String getDescription() {
        return "Кисень витрачається повільніше, а на нулі коштом голоду спрацьовує рятівний ковток";
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
        return Map.of(
            "air-drain-multiplier", 0.6,
            "gasp-air-ticks", 120,
            "hunger-cost", 4
        );
    }

    @Override public double getEnchantingTableChance() { return 0.05; }
    @Override public int getWeight() { return 7; }
    @Override public double getChestLootChance() { return 0.06; }
    @Override public int getAnvilBaseCost() { return 7; }

    @Override
    public List<String> getLootChests() {
        return List.of(
            "minecraft:chests/underwater_ruin_big",
            "minecraft:chests/underwater_ruin_small",
            "minecraft:chests/shipwreck_treasure",
            "minecraft:chests/buried_treasure"
        );
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of("FISHERMAN", Map.of("villager-level", 4, "min-level", 1, "max-level", 1, "price", 24));
    }

    @EventHandler(ignoreCancelled = true)
    public void onAir(EntityAirChangeEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getEntity() instanceof Player player)) return;
        if (levelOnPlayer(player) == 0) return;
        if (e.getAmount() >= player.getRemainingAir()) return;

        double multiplier = Math.max(0.05, Math.min(1.0, getDouble("air-drain-multiplier")));
        if (ThreadLocalRandom.current().nextDouble() > multiplier) {
            e.setCancelled(true);
            return;
        }
        if (e.getAmount() > 0) return;

        if (!hasHunger(player, getInt("hunger-cost"))) return;
        if (!spend(player, getInt("hunger-cost"))) return;

        e.setCancelled(true);
        player.setRemainingAir(Math.max(1, getInt("gasp-air-ticks")));
        activation(player);
        particles(player.getEyeLocation(), Particle.BUBBLE, 20, 0.3, 0.3, 0.3, 0.05);
        soundAt(player.getLocation(), Sound.ENTITY_PLAYER_BREATH, 0.7f, 1.0f);
    }
}
