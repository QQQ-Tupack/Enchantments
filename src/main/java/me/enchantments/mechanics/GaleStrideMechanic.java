package me.enchantments.mechanics;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GaleStrideMechanic extends Mechanic {

    private final Set<UUID> raging = ConcurrentHashMap.newKeySet();

    @Override
    public String getId() {
        return "gale_stride";
    }

    @Override
    public String getName() {
        return "Лють вікінга";
    }

    @Override
    public String getDescription() {
        return "На межі загибелі твої удари стають сильнішими; лють вщухає, коли рани загояться";
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
            "enter-percent", 0.30,
            "exit-percent", 0.50,
            "damage-bonus", 0.20
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
            "minecraft:chests/village/village_weaponsmith",
            "minecraft:chests/pillager_outpost",
            "minecraft:chests/shipwreck_supply",
            "minecraft:chests/bastion_bridge"
        );
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of(
            "WEAPONSMITH", Map.of("villager-level", 4, "min-level", 1, "max-level", 1, "price", 30),
            "BUTCHER", Map.of("villager-level", 5, "min-level", 1, "max-level", 1, "price", 26)
        );
    }

    @Override
    public int getAnvilBaseCost() {
        return 9;
    }

    @Override
    protected void onEnable() {
        Bukkit.getScheduler().runTaskTimer(getPlugin(), () -> {
            if (!isMechanicEnabled()) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (levelOnPlayer(player) == 0) {
                    raging.remove(player.getUniqueId());
                    continue;
                }
                tick(player);
            }
        }, 10L, 10L);
    }

    private void tick(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        double max = attribute != null ? attribute.getValue() : 20.0;
        double ratio = player.getHealth() / max;
        boolean was = raging.contains(player.getUniqueId());

        if (!was && ratio <= getDouble("enter-percent")) {
            raging.add(player.getUniqueId());
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.5f, 1.2f);
            player.sendActionBar(Component.text("§4⚔ Лють прокинулась"));
        } else if (was && ratio >= getDouble("exit-percent")) {
            raging.remove(player.getUniqueId());
            player.sendActionBar(Component.text("§7Лють вщухла"));
        }

        if (raging.contains(player.getUniqueId())) {
            player.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, player.getLocation().add(0, 2.2, 0), 1, 0.2, 0.1, 0.2, 0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getDamager() instanceof Player player)) return;
        if (!raging.contains(player.getUniqueId())) return;
        if (levelOnPlayer(player) == 0) return;

        e.setDamage(e.getDamage() * (1.0 + getDouble("damage-bonus")));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        raging.remove(e.getPlayer().getUniqueId());
    }
}
