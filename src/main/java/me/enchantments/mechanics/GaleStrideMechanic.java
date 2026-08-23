package me.enchantments.mechanics;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;

import me.enchantments.main.Mechanic;

import java.util.List;
import java.util.Map;

public final class GaleStrideMechanic extends Mechanic {

    private NamespacedKey ragingKey;

    @Override public String getId() { return "gale_stride"; }
    @Override public String getName() { return "Лють вікінга"; }
    @Override public String getDescription() { return "На межі загибелі твої удари стають сильнішими; лють вщухає, коли рани загояться"; }
    @Override public int getMaxLevel() { return 1; }
    @Override public List<Material> getSupportedItems() { return List.of(Material.TOTEM_OF_UNDYING); }
    @Override public List<EnchantSlot> getSlots() { return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND, EnchantSlot.HEAD); }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "enter-percent", 0.30,
            "exit-percent", 0.50,
            "damage-bonus", 0.20
        );
    }

    @Override public double getEnchantingTableChance() { return 0.04; }
    @Override public int getWeight() { return 5; }
    @Override public double getChestLootChance() { return 0.05; }

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

    @Override public int getAnvilBaseCost() { return 9; }

    @Override
    protected void onEnable() {
        ragingKey = new NamespacedKey(getPlugin(), "gale_stride_raging");
        Bukkit.getScheduler().runTaskTimer(getPlugin(), () -> {
            if (!isMechanicEnabled()) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (levelOnPlayer(player) > 0) tick(player);
            }
        }, 10L, 10L);
    }

    private void tick(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        double max = attribute != null ? attribute.getValue() : 20.0;
        double ratio = player.getHealth() / max;
        var pdc = player.getPersistentDataContainer();
        boolean was = pdc.getOrDefault(ragingKey, PersistentDataType.BOOLEAN, false);

        if (!was && ratio <= getDouble("enter-percent")) {
            pdc.set(ragingKey, PersistentDataType.BOOLEAN, true);
            soundAt(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.5f, 1.2f);
        } else if (was && ratio >= getDouble("exit-percent")) {
            pdc.set(ragingKey, PersistentDataType.BOOLEAN, false);
        }

        if (pdc.getOrDefault(ragingKey, PersistentDataType.BOOLEAN, false)) {
            particles(player.getLocation().add(0, 2.2, 0), Particle.ANGRY_VILLAGER, 1, 0.2);
        }
    }

    @Override
    protected void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity target, int level) {
        var pdc = player.getPersistentDataContainer();
        if (pdc.getOrDefault(ragingKey, PersistentDataType.BOOLEAN, false)) {
            event.setDamage(event.getDamage() * (1.0 + getDouble("damage-bonus")));
        }
    }
}
