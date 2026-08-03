package me.enchantments.mechanics;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class AbyssalBreathMechanic extends Mechanic {

    private final Set<UUID> gaspSpent = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> drySeconds = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "abyssal_breath";
    }

    @Override
    public String getName() {
        return "Подих безодні";
    }

    @Override
    public String getDescription() {
        return "Кисень витрачається повільніше, а на нулі спрацьовує один рятівний ковток повітря";
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
            "air-drain-multiplier", 0.6,
            "gasp-air-ticks", 120,
            "recharge-seconds", 10
        );
    }

    @Override
    public double getEnchantingTableChance() {
        return 0.05;
    }

    @Override
    public int getWeight() {
        return 7;
    }

    @Override
    public double getChestLootChance() {
        return 0.06;
    }

    @Override
    public List<String> getLootChests() {
        return List.of(
            "minecraft:chests/underwater_ruin_big",
            "minecraft:chests/underwater_ruin_small",
            "minecraft:chests/shipwreck_treasure",
            "minecraft:chests/buried_treasure"
        );
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of("FISHERMAN", Map.of("villager-level", 4, "min-level", 1, "max-level", 1, "price", 24));
    }

    @Override
    public int getAnvilBaseCost() {
        return 7;
    }

    @Override
    protected void onEnable() {
        Bukkit.getScheduler().runTaskTimer(getPlugin(), () -> {
            if (!isMechanicEnabled()) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID id = player.getUniqueId();
                if (player.isInWater()) {
                    drySeconds.remove(id);
                    continue;
                }
                if (!gaspSpent.contains(id)) continue;

                int dry = drySeconds.merge(id, 1, Integer::sum);
                if (dry >= Math.max(1, getInt("recharge-seconds"))) {
                    gaspSpent.remove(id);
                    drySeconds.remove(id);
                    player.sendActionBar(Component.text("§b≈ Подих відновлено"));
                }
            }
        }, 20L, 20L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAir(EntityAirChangeEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getEntity() instanceof Player player)) return;
        if (levelOnPlayer(player) == 0) return;
        if (e.getAmount() >= player.getRemainingAir()) return; // кисень відновлюється — не втручаємось

        // 1) уповільнення витрати: частину тіків витрати просто пропускаємо
        double multiplier = Math.max(0.05, Math.min(1.0, getDouble("air-drain-multiplier")));
        if (ThreadLocalRandom.current().nextDouble() > multiplier) {
            e.setCancelled(true);
            return;
        }

        // 2) один рятівний ковток на нулі
        if (e.getAmount() <= 0 && gaspSpent.add(player.getUniqueId())) {
            e.setCancelled(true);
            player.setRemainingAir(Math.max(1, getInt("gasp-air-ticks")));
            player.getWorld().spawnParticle(Particle.BUBBLE, player.getEyeLocation(), 20, 0.3, 0.3, 0.3, 0.05);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_BREATH, 0.7f, 1.0f);
            player.sendActionBar(Component.text("§b≈ Останній ковток — спливай!"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        gaspSpent.remove(e.getPlayer().getUniqueId());
        drySeconds.remove(e.getPlayer().getUniqueId());
    }
}
