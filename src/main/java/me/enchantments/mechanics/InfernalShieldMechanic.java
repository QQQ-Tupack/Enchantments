package me.enchantments.mechanics;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InfernalShieldMechanic extends Mechanic {

    private final Set<UUID> spent = ConcurrentHashMap.newKeySet();

    @Override
    public String getId() {
        return "infernal_shield";
    }

    @Override
    public String getName() {
        return "Пекельний оберіг";
    }

    @Override
    public String getDescription() {
        return "Гасить полум'я і дає вогнетривкість; заряд відновлюється у воді або після сну";
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
        return Map.of("resistance-duration", 8);
    }

    @Override
    public double getEnchantingTableChance() {
        return 0.03;
    }

    @Override
    public int getWeight() {
        return 4;
    }

    @Override
    public double getChestLootChance() {
        return 0.06;
    }

    @Override
    public List<String> getLootChests() {
        return List.of(
            "minecraft:chests/nether_bridge",
            "minecraft:chests/bastion_treasure",
            "minecraft:chests/bastion_other",
            "minecraft:chests/ruined_portal"
        );
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of("ARMORER", Map.of("villager-level", 5, "min-level", 1, "max-level", 1, "price", 34));
    }

    @Override
    public int getAnvilBaseCost() {
        return 10;
    }

    @Override
    protected void onEnable() {
        Bukkit.getScheduler().runTaskTimer(getPlugin(), () -> {
            if (!isMechanicEnabled()) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (levelOnPlayer(player) == 0) continue;
                if (!player.isInWater()) continue;
                if (spent.remove(player.getUniqueId())) {
                    player.getWorld().playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.4f);
                    player.sendActionBar(Component.text("§6✦ Оберіг знову теплий"));
                }
            }
        }, 20L, 20L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(EntityDamageEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getEntity() instanceof Player player)) return;
        if (!isFire(e.getCause())) return;
        if (levelOnPlayer(player) == 0) return;
        if (!spent.add(player.getUniqueId())) return;

        e.setCancelled(true);
        player.setFireTicks(0);

        int seconds = Math.max(1, getInt("resistance-duration"));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, seconds * 20, 0, true, true, true));

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.5f);
        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1, 0), 24, 0.5, 0.8, 0.5, 0.03);
        player.sendActionBar(Component.text("§cОберіг витрачено — знайди воду"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onSleep(PlayerBedEnterEvent e) {
        if (!isMechanicEnabled()) return;
        if (e.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;
        if (levelOnPlayer(e.getPlayer()) == 0) return;
        spent.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        spent.remove(e.getPlayer().getUniqueId());
    }

    private boolean isFire(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.FIRE
            || cause == EntityDamageEvent.DamageCause.FIRE_TICK
            || cause == EntityDamageEvent.DamageCause.LAVA
            || cause == EntityDamageEvent.DamageCause.HOT_FLOOR;
    }
}
