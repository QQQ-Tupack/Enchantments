package me.enchantments.mechanics;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OceanGraceMechanic extends Mechanic {

    private final Set<UUID> wasSwimming = ConcurrentHashMap.newKeySet();

    @Override
    public String getId() {
        return "ocean_grace";
    }

    @Override
    public String getName() {
        return "Морська ласка";
    }

    @Override
    public String getDescription() {
        return "Плавання стає легким, поки є сили; на виході з води хвиля дає короткий поштовх";
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
            "saturation-per-second", 0.15,
            "exit-speed-seconds", 3
        );
    }

    @Override
    public double getEnchantingTableChance() {
        return 0.06;
    }

    @Override
    public int getWeight() {
        return 8;
    }

    @Override
    public double getChestLootChance() {
        return 0.06;
    }

    @Override
    public List<String> getLootChests() {
        return List.of(
            "minecraft:chests/underwater_ruin_small",
            "minecraft:chests/shipwreck_supply",
            "minecraft:chests/shipwreck_map",
            "minecraft:chests/village/village_fisher"
        );
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of("FISHERMAN", Map.of("villager-level", 3, "min-level", 1, "max-level", 1, "price", 20));
    }

    @Override
    public int getAnvilBaseCost() {
        return 6;
    }

    @Override
    protected void onEnable() {
        Bukkit.getScheduler().runTaskTimer(getPlugin(), () -> {
            if (!isMechanicEnabled()) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (levelOnPlayer(player) == 0) continue;
                tick(player);
            }
        }, 20L, 20L);
    }

    private void tick(Player player) {
        UUID id = player.getUniqueId();

        if (player.isSwimming() && player.isInWater()) {
            float cost = (float) getDouble("saturation-per-second");
            if (player.getSaturation() >= cost) {
                player.setSaturation(player.getSaturation() - cost);
                player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 60, 0, true, false, true));
            } else if (player.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE)) {
                player.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
                player.sendActionBar(Component.text("§7Сили на плавання скінчились"));
            }
            wasSwimming.add(id);
            return;
        }

        if (wasSwimming.remove(id) && !player.isInWater()) {
            int seconds = Math.max(1, getInt("exit-speed-seconds"));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, 0, true, false, true));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_DOLPHIN_SPLASH, 0.4f, 1.3f);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        wasSwimming.remove(e.getPlayer().getUniqueId());
    }
}
