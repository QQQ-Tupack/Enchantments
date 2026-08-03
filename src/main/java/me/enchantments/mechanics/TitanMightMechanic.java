package me.enchantments.mechanics;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TitanMightMechanic extends Mechanic {

    private static final long DECAY_MILLIS = 5_000L;

    private final Map<UUID, UUID> lastTarget = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pressure = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHit = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "titan_might";
    }

    @Override
    public String getName() {
        return "Міць титана";
    }

    @Override
    public String getDescription() {
        return "Кожен наступний повністю заряджений удар по тій самій цілі б'є сильніше";
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
            "damage-per-stack", 0.10,
            "max-stacks", 3,
            "charge-threshold", 0.9
        );
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
        return 0.05;
    }

    @Override
    public List<String> getLootChests() {
        return List.of(
            "minecraft:chests/bastion_other",
            "minecraft:chests/nether_bridge",
            "minecraft:chests/trial_chambers/reward",
            "minecraft:chests/village/village_weaponsmith"
        );
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of("WEAPONSMITH", Map.of("villager-level", 5, "min-level", 1, "max-level", 1, "price", 36));
    }

    @Override
    public int getAnvilBaseCost() {
        return 12;
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getDamager() instanceof Player player)) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;
        if (levelOnPlayer(player) == 0) return;

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        boolean charged = player.getAttackCooldown() >= (float) getDouble("charge-threshold");
        boolean sameTarget = target.getUniqueId().equals(lastTarget.get(id));
        boolean inTime = now - lastHit.getOrDefault(id, 0L) <= DECAY_MILLIS;

        lastTarget.put(id, target.getUniqueId());
        lastHit.put(id, now);

        if (!charged || !sameTarget || !inTime) {
            pressure.remove(id);
            return;
        }

        int held = pressure.getOrDefault(id, 0);
        if (held > 0) {
            e.setDamage(e.getDamage() * (1.0 + held * getDouble("damage-per-stack")));
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 10 * held, 0.3, 0.3, 0.3, 0.2);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.5f, 1.0f - held * 0.1f);
        }

        int max = Math.max(1, getInt("max-stacks"));
        int next = Math.min(max, held + 1);
        pressure.put(id, next);
        player.sendActionBar(Component.text("§c⚔ Натиск: §4" + "▮".repeat(next) + "§8" + "▯".repeat(max - next)));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        lastTarget.remove(id);
        pressure.remove(id);
        lastHit.remove(id);
    }
}
