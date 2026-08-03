package me.enchantments.mechanics;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GaleDescentMechanic extends Mechanic {

    private final Map<UUID, Integer> charge = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCharge = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "gale_descent";
    }

    @Override
    public String getName() {
        return "Штормовий заряд";
    }

    @Override
    public String getDescription() {
        return "Під дощем накопичує статичний заряд, який розряджається в наступний удар";
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
            "max-stacks", 5,
            "damage-per-stack", 1.0,
            "charge-interval-seconds", 10
        );
    }

    @Override
    public double getEnchantingTableChance() {
        return 0.05;
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
        Bukkit.getScheduler().runTaskTimer(getPlugin(), () -> {
            if (!isMechanicEnabled()) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (levelOnPlayer(player) == 0) continue;
                tick(player);
            }
        }, 20L, 20L);
    }

    private void tick(Player player) {
        long interval = Math.max(1, getInt("charge-interval-seconds")) * 1000L;
        long now = System.currentTimeMillis();
        if (now - lastCharge.getOrDefault(player.getUniqueId(), 0L) < interval) return;

        World world = player.getWorld();
        if (!world.hasStorm()) return;
        if (world.getEnvironment() != World.Environment.NORMAL) return;
        if (world.getHighestBlockYAt(player.getLocation()) > player.getLocation().getBlockY()) return;

        int max = Math.max(1, getInt("max-stacks"));
        int held = Math.min(max, charge.getOrDefault(player.getUniqueId(), 0) + 1);
        charge.put(player.getUniqueId(), held);
        lastCharge.put(player.getUniqueId(), now);

        world.spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0, 1.2, 0), 6, 0.4, 0.5, 0.4, 0.02);
        player.sendActionBar(Component.text("§b⚡ Заряд: " + "▮".repeat(held) + "§8" + "▯".repeat(max - held)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getDamager() instanceof Player player)) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;
        if (levelOnPlayer(player) == 0) return;

        int held = charge.getOrDefault(player.getUniqueId(), 0);
        if (held <= 0) return;

        charge.remove(player.getUniqueId());
        e.setDamage(e.getDamage() + held * getDouble("damage-per-stack"));

        target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.25);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.4f, 1.8f);
        player.sendActionBar(Component.text("§b⚡ Розряд ×" + held));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        charge.remove(e.getPlayer().getUniqueId());
        lastCharge.remove(e.getPlayer().getUniqueId());
    }
}
