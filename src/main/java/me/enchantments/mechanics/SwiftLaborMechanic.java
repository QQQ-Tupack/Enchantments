package me.enchantments.mechanics;

import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SwiftLaborMechanic extends Mechanic {

    private final Map<UUID, Integer> rhythm = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBreak = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "swift_labor";
    }

    @Override
    public String getName() {
        return "Робочий ритм";
    }

    @Override
    public String getDescription() {
        return "Безперервне копання розганяє ритм: чим довше працюєш, тим швидше йде справа";
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
            "max-stacks", 5,
            "decay-seconds", 3,
            "haste-i-at", 3,
            "haste-ii-at", 5
        );
    }

    @Override
    public double getEnchantingTableChance() {
        return 0.06;
    }

    @Override
    public int getWeight() {
        return 9;
    }

    @Override
    public double getChestLootChance() {
        return 0.06;
    }

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

    @Override
    public int getAnvilBaseCost() {
        return 7;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!isMechanicEnabled()) return;

        Player player = e.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        if (levelOnPlayer(player) == 0) return;
        if (e.getBlock().getType().getHardness() <= 0.0f) return; // трава й факели не рахуються

        long decay = Math.max(1, getInt("decay-seconds")) * 1000L;
        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();

        int held = now - lastBreak.getOrDefault(id, 0L) > decay ? 0 : rhythm.getOrDefault(id, 0);
        held = Math.min(Math.max(1, getInt("max-stacks")), held + 1);
        rhythm.put(id, held);
        lastBreak.put(id, now);

        int amplifier = -1;
        if (held >= getInt("haste-ii-at")) amplifier = 1;
        else if (held >= getInt("haste-i-at")) amplifier = 0;

        if (amplifier >= 0) {
            // тривалість трохи більша за decay, щоб ефект гас разом зі стаками
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, (int) (decay / 50) + 20, amplifier, true, false, true));
        }

        int max = Math.max(1, getInt("max-stacks"));
        player.sendActionBar(Component.text("§e⛏ Ритм: §6" + "▮".repeat(held) + "§8" + "▯".repeat(max - held)));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        rhythm.remove(e.getPlayer().getUniqueId());
        lastBreak.remove(e.getPlayer().getUniqueId());
    }
}
