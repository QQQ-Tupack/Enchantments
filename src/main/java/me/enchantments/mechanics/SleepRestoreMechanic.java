package me.enchantments.mechanics;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerBedEnterEvent;

import java.util.List;
import java.util.Map;

public final class SleepRestoreMechanic extends Mechanic {

    @Override
    public String getId() {
        return "sleep_restore";
    }

    @Override
    public String getName() {
        return "Солодких снів";
    }

    @Override
    public String getDescription() {
        return "Повністю відновлює здоров'я та ситість, коли гравець лягає спати";
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
        return Map.of("restore-saturation", true);
    }

    @Override
    public double getEnchantingTableChance() {
        return 0.07;
    }

    @Override
    public int getWeight() {
        return 10;
    }

    @Override
    public double getChestLootChance() {
        return 0.06;
    }

    @Override
    public List<String> getLootChests() {
        return List.of(
            "minecraft:chests/village/village_plains_house",
            "minecraft:chests/village/village_snowy_house",
            "minecraft:chests/village/village_temple",
            "minecraft:chests/igloo_chest"
        );
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of(
            "SHEPHERD", Map.of("villager-level", 4, "min-level", 1, "max-level", 1, "price", 22),
            "CLERIC", Map.of("villager-level", 3, "min-level", 1, "max-level", 1, "price", 26)
        );
    }

    @Override
    public int getAnvilBaseCost() {
        return 6;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSleep(PlayerBedEnterEvent e) {
        if (!isMechanicEnabled()) return;
        if (e.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;

        Player player = e.getPlayer();
        if (levelOnPlayer(player) == 0) return;

        // наступного тіка: якщо вхід у ліжко скасував інший плагін після нас — нічого не даємо
        Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
            if (!player.isOnline() || !player.isSleeping()) return;

            AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
            player.setHealth(attribute != null ? attribute.getValue() : 20.0);
            player.setFoodLevel(20);
            if (getBoolean("restore-saturation")) player.setSaturation(20f);

            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
            player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 8, 0.4, 0.4, 0.4, 0.02);
        }, 1L);
    }
}
