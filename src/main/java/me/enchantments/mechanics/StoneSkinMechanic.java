package me.enchantments.mechanics;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StoneSkinMechanic extends Mechanic {

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "stone_skin";
    }

    @Override
    public String getName() {
        return "Кам'яна шкіра";
    }

    @Override
    public String getDescription() {
        return "Важкий удар відлунює по камені: частина урону гасне, і шкіра ненадовго кам'яніє";
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
            "trigger-percent", 0.25,
            "damage-reduction", 0.30,
            "resistance-seconds", 3,
            "cooldown", 45
        );
    }

    @Override
    public double getEnchantingTableChance() {
        return 0.04;
    }

    @Override
    public int getWeight() {
        return 6;
    }

    @Override
    public double getChestLootChance() {
        return 0.05;
    }

    @Override
    public List<String> getLootChests() {
        return List.of(
            "minecraft:chests/village/village_armorer",
            "minecraft:chests/trial_chambers/supply",
            "minecraft:chests/bastion_hoglin_stable",
            "minecraft:chests/stronghold_crossing"
        );
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of("ARMORER", Map.of("villager-level", 4, "min-level", 1, "max-level", 1, "price", 30));
    }

    @Override
    public int getAnvilBaseCost() {
        return 10;
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeavyHit(EntityDamageEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getEntity() instanceof Player player)) return;
        if (levelOnPlayer(player) == 0) return;
        if (cooldowns.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis()) return;

        double incoming = e.getFinalDamage();
        if (incoming <= 0) return;
        if (incoming < player.getHealth() * getDouble("trigger-percent")) return; // дрібний чіп-урон не рахується

        e.setDamage(e.getDamage() * (1.0 - getDouble("damage-reduction")));

        int seconds = Math.max(1, getInt("resistance-seconds"));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, seconds * 20, 1, true, true, true));
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + Math.max(1, getInt("cooldown")) * 1000L);

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_STONE_PLACE, 0.8f, 0.6f);
        player.getWorld().spawnParticle(Particle.BLOCK, player.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4,
            Material.STONE.createBlockData());
        player.sendActionBar(Component.text("§7▣ Кам'яна шкіра витримала"));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cooldowns.remove(e.getPlayer().getUniqueId());
    }
}
