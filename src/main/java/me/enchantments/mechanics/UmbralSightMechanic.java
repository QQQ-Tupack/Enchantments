package me.enchantments.mechanics;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class UmbralSightMechanic extends Mechanic {

    private final Map<UUID, Integer> adaptation = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "umbral_sight";
    }

    @Override
    public String getName() {
        return "Тінезір";
    }

    @Override
    public String getDescription() {
        return "Очі поступово адаптуються до темряви й так само поступово відвикають на світлі";
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
            "max-light-level", 4,
            "adaptation-ticks", 80,
            "fade-ticks", 60
        );
    }

    @Override
    public double getEnchantingTableChance() {
        return 0.04;
    }

    @Override
    public int getWeight() {
        return 5;
    }

    @Override
    public double getChestLootChance() {
        return 0.05;
    }

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
    public int getAnvilBaseCost() {
        return 8;
    }

    @Override
    protected void onEnable() {
        Bukkit.getScheduler().runTaskTimer(getPlugin(), () -> {
            if (!isMechanicEnabled()) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (levelOnPlayer(player) == 0) continue;
                tick(player);
            }
        }, 10L, 10L);
    }

    private void tick(Player player) {
        int maxLight = getInt("max-light-level");
        int riseTicks = Math.max(20, getInt("adaptation-ticks"));
        int fadeTicks = Math.max(20, getInt("fade-ticks"));

        int light = player.getEyeLocation().getBlock().getLightLevel();
        int value = adaptation.getOrDefault(player.getUniqueId(), 0);

        if (light <= maxLight) {
            value = Math.min(riseTicks, value + 10);
        } else {
            value = Math.max(0, value - Math.max(1, (riseTicks * 10) / fadeTicks));
        }
        adaptation.put(player.getUniqueId(), value);

        if (value >= riseTicks) {
            // тривалість > 200 тіків, тож ванільного миготіння на межі не буде
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0, true, false, true));
        } else if (value == 0 && player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        adaptation.remove(e.getPlayer().getUniqueId());
    }
}
