package me.enchantments.mechanics;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import me.enchantments.main.Mechanic;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class UmbralSightMechanic extends Mechanic {

    private static final int FLICKER_CEILING = 190;
    private static final int TICK_INTERVAL = 10;
    private static final int PREVIEW_TICKS = 40;
    private static final int STEADY_TICKS = 400;

    private static final class Sight {
        int value;
        int preview;
        boolean active;
    }

    private final Map<UUID, Sight> eyes = new ConcurrentHashMap<>();

    @Override public String getId() { return "umbral_sight"; }
    @Override public String getName() { return "Тінезір"; }
    @Override public String getDescription() { return "Побувши в темряві, очі звикають і починають бачити; на світлі звикання швидко втрачається"; }
    @Override public int getMaxLevel() { return 1; }
    @Override public List<Material> getSupportedItems() { return List.of(Material.TOTEM_OF_UNDYING); }
    @Override public List<EnchantSlot> getSlots() { return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND, EnchantSlot.HEAD); }
    @Override public double getEnchantingTableChance() { return 0.04; }
    @Override public int getWeight() { return 5; }

    @Override
    public List<String> getLootChests() {
        return List.of(
            "minecraft:chests/ancient_city",
            "minecraft:chests/stronghold_corridor",
            "minecraft:chests/simple_dungeon",
            "minecraft:chests/abandoned_mineshaft"
        );
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of(
            "CARTOGRAPHER", Map.of("villager-level", 4, "min-level", 1, "max-level", 1, "price", 28),
            "LIBRARIAN", Map.of("villager-level", 5, "min-level", 1, "max-level", 1, "price", 32)
        );
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of("max-light-level", 4, "adaptation-ticks", 80, "fade-ticks", 60);
    }

    @Override
    protected void onEnable() {
        Bukkit.getScheduler().runTaskTimer(getPlugin(), () -> {
            if (!isMechanicEnabled()) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (levelOnPlayer(player) == 0) {
                    release(player);
                    continue;
                }
                tick(player);
            }
        }, TICK_INTERVAL, TICK_INTERVAL);
    }

    private void tick(Player player) {
        int maxLight = getInt("max-light-level");
        int riseTicks = Math.max(20, getInt("adaptation-ticks"));
        int fadeTicks = Math.min(FLICKER_CEILING, Math.max(20, getInt("fade-ticks")));
        int holdFloor = riseTicks / 2;

        Sight sight = eyes.computeIfAbsent(player.getUniqueId(), id -> new Sight());
        int light = player.getEyeLocation().getBlock().getLightLevel();

        sight.value = light <= maxLight
                ? Math.min(riseTicks, sight.value + TICK_INTERVAL)
                : Math.max(0, sight.value - Math.max(1, (riseTicks * TICK_INTERVAL) / fadeTicks));

        if (!sight.active) {
            if (sight.value < riseTicks) {
                sight.preview = 0;
                return;
            }
            if (sight.preview == 0) {
                sight.preview = PREVIEW_TICKS / TICK_INTERVAL;
                apply(player, PREVIEW_TICKS + TICK_INTERVAL);
                return;
            }
            if (--sight.preview > 0) return;

            sight.active = true;
            apply(player, STEADY_TICKS);
            return;
        }

        if (sight.value >= holdFloor) {
            apply(player, STEADY_TICKS);
            return;
        }

        sight.active = false;
        sight.preview = 0;
        fade(player, fadeTicks);
    }

    private void apply(Player player, int ticks) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, ticks, 0, true, false, true));
    }


    private void fade(Player player, int ticks) {
        PotionEffect current = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        if (current == null || current.getDuration() <= ticks) return;
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        apply(player, ticks);
    }

    private void release(Player player) {
        Sight sight = eyes.remove(player.getUniqueId());
        if (sight == null || !sight.active) return;
        fade(player, Math.min(FLICKER_CEILING, Math.max(20, getInt("fade-ticks"))));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        eyes.remove(e.getPlayer().getUniqueId());
    }
}