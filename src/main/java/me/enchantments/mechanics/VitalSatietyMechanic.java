package me.enchantments.mechanics;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VitalSatietyMechanic extends Mechanic {

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "vital_satiety";
    }

    @Override
    public String getName() {
        return "Ситість до кісток";
    }

    @Override
    public String getDescription() {
        return "Якщо їжа заповнює голод до кінця — коротка регенерація";
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
            "regen-duration", 6,
            "cooldown", 90
        );
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
            "minecraft:chests/village/village_butcher",
            "minecraft:chests/village/village_temple",
            "minecraft:chests/village/village_plains_house",
            "minecraft:chests/igloo_chest"
        );
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of(
            "BUTCHER", Map.of("villager-level", 3, "min-level", 1, "max-level", 1, "price", 18),
            "FARMER", Map.of("villager-level", 4, "min-level", 1, "max-level", 1, "price", 22)
        );
    }

    @Override
    public int getAnvilBaseCost() {
        return 6;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        if (!isMechanicEnabled()) return;

        Player player = e.getPlayer();
        if (levelOnPlayer(player) == 0) return;
        if (cooldowns.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis()) return;
        if (player.getFoodLevel() >= 20) return; // це не той "добиваючий" шматок

        Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
            if (player.getFoodLevel() < 20) return;

            int seconds = Math.max(1, getInt("regen-duration"));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, seconds * 20, 0, true, true, true));
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + Math.max(1, getInt("cooldown")) * 1000L);

            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.5f, 1.2f);
            player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1.4, 0), 5, 0.3, 0.3, 0.3, 0.01);
            player.sendActionBar(Component.text("§c❤ Ситість до кісток"));
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cooldowns.remove(e.getPlayer().getUniqueId());
    }
}
