package me.enchantments.mechanics;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import me.enchantments.main.Mechanic;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SwiftLaborMechanic extends Mechanic {

    private final Map<UUID, Integer> rhythm = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBreak = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> tier = new ConcurrentHashMap<>();

    @Override public String getId() { return "swift_labor"; }
    @Override public String getName() { return "Робочий ритм"; }
    @Override public String getDescription() { return "Безперервне копання розганяє ритм: чим довше працюєш, тим швидше йде справа"; }
    @Override public int getMaxLevel() { return 1; }
    @Override public List<Material> getSupportedItems() { return List.of(Material.TOTEM_OF_UNDYING); }
    @Override public List<EnchantSlot> getSlots() { return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND, EnchantSlot.HEAD); }

    @Override public double getEnchantingTableChance() { return 0.06; }
    @Override public int getWeight() { return 9; }
    @Override public double getChestLootChance() { return 0.06; }

    @Override
    public List<String> getLootChests() {
        return List.of(
            "minecraft:chests/abandoned_mineshaft",
            "minecraft:chests/village/village_toolsmith",
            "minecraft:chests/village/village_mason",
            "minecraft:chests/ancient_city"
        );
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of(
            "TOOLSMITH", Map.of("villager-level", 3, "min-level", 1, "max-level", 1, "price", 22),
            "MASON", Map.of("villager-level", 3, "min-level", 1, "max-level", 1, "price", 20)
        );
    }

    @Override public int getAnvilBaseCost() { return 7; }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "max-stacks", 5,
            "decay-seconds", 3,
            "haste-i-at", 3,
            "haste-ii-at", 5
        );
    }

    @Override
    protected void onBlockBreak(BlockBreakEvent event, Player player, int level) {
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        if (event.getBlock().getType().getHardness() <= 0.0f) return;

        long decay = Math.max(1, getInt("decay-seconds")) * 1000L;
        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();

        int held = now - lastBreak.getOrDefault(id, 0L) > decay ? 0 : rhythm.getOrDefault(id, 0);
        held = Math.min(Math.max(1, getInt("max-stacks")), held + 1);
        rhythm.put(id, held);
        lastBreak.put(id, now);

        int amplifier = held >= getInt("haste-ii-at") ? 1 : held >= getInt("haste-i-at") ? 0 : -1;
        if (amplifier < 0) {
            tier.remove(id);
            return;
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, (int) (decay / 50) + 20, amplifier, true, false, true));

        Integer previous = tier.put(id, amplifier);
        if (previous == null || previous != amplifier) {
            soundFor(player, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.4f, 1.1f + amplifier * 0.3f);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        rhythm.remove(id);
        lastBreak.remove(id);
        tier.remove(id);
    }
}
