package me.enchantments.mechanics;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;

import me.enchantments.main.Mechanic;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GaleDescentMechanic extends Mechanic {

    private NamespacedKey chargeKey;
    private final Map<UUID, Long> lastCharge = new ConcurrentHashMap<>();

    @Override public String getId() { return "gale_descent"; }
    @Override public String getName() { return "Штормовий заряд"; }
    @Override public String getDescription() { return "Під дощем накопичує статичний заряд, який розряджається в наступний удар"; }
    @Override public int getMaxLevel() { return 1; }
    @Override public List<Material> getSupportedItems() { return List.of(Material.TOTEM_OF_UNDYING); }
    @Override public List<EnchantSlot> getSlots() { return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND, EnchantSlot.HEAD); }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "max-stacks", 5,
            "damage-per-stack", 1.0,
            "charge-interval-seconds", 10
        );
    }

    @Override public double getEnchantingTableChance() { return 0.05; }
    @Override public int getWeight() { return 6; }
    @Override public double getChestLootChance() { return 0.05; }

    @Override
    public List<String> getLootChests() {
        return List.of(
            "minecraft:chests/pillager_outpost",
            "minecraft:chests/woodland_mansion",
            "minecraft:chests/trial_chambers/reward",
            "minecraft:chests/village/village_fletcher"
        );
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of("FLETCHER", Map.of("villager-level", 4, "min-level", 1, "max-level", 1, "price", 26));
    }

    @Override
    protected void onEnable() {
        chargeKey = new NamespacedKey(getPlugin(), "gale_descent_charge");
        Bukkit.getScheduler().runTaskTimer(getPlugin(), () -> {
            if (!isMechanicEnabled()) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (levelOnPlayer(player) > 0) tick(player);
            }
        }, 20L, 20L);
    }

    private void tick(Player player) {
        long interval = Math.max(1, getInt("charge-interval-seconds")) * 1000L;
        long now = System.currentTimeMillis();
        if (now - lastCharge.getOrDefault(player.getUniqueId(), 0L) < interval) return;

        World world = player.getWorld();
        if (!world.hasStorm() || world.getEnvironment() != World.Environment.NORMAL) return;
        if (world.getHighestBlockYAt(player.getLocation()) > player.getLocation().getBlockY()) return;

        var pdc = player.getPersistentDataContainer();
        int max = Math.max(1, getInt("max-stacks"));
        int held = Math.min(max, pdc.getOrDefault(chargeKey, PersistentDataType.INTEGER, 0) + 1);
        pdc.set(chargeKey, PersistentDataType.INTEGER, held);
        lastCharge.put(player.getUniqueId(), now);

        particles(player.getLocation().add(0, 1.2, 0), Particle.ELECTRIC_SPARK, 6, 0.4);
    }

    @Override
    protected void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity target, int level) {
        var pdc = player.getPersistentDataContainer();
        int held = pdc.getOrDefault(chargeKey, PersistentDataType.INTEGER, 0);
        if (held <= 0) return;

        pdc.set(chargeKey, PersistentDataType.INTEGER, 0);
        event.setDamage(event.getDamage() + held * getDouble("damage-per-stack"));

        particles(target.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 30, 0.4);
        soundAt(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.4f, 1.8f);
    }
}
