package me.enchantments.mechanics;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import me.enchantments.main.Mechanic;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Кільватер.
 * Плавання розганяє гравця, а виринання на швидкості підкидає його з води як дельфіна.
 */
public class SlipstreamMechanic extends Mechanic {

    private final Set<UUID> swimming = new HashSet<>();

    @Override
    public String getId() {
        return "slipstream";
    }

    @Override
    public String getName() {
        return "Кільватер";
    }

    @Override
    public String getDescription() {
        return "Розганяє власника під час плавання і підкидає його вгору на виринанні.";
    }

    @Override
    public int getMaxLevel() {
        return 3;
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
    public boolean isEnchantingTableAllowed() {
        return true;
    }

    @Override
    public double getEnchantingTableChance() {
        return 0.12;
    }

    @Override
    public boolean isChestLootAllowed() {
        return true;
    }

    @Override
    public double getChestLootChance() {
        return 0.06;
    }

    @Override
    public List<String> getLootChests() {
        return List.of("minecraft:chests/underwater_ruin_big", "minecraft:chests/shipwreck_treasure");
    }

    @Override
    public boolean isVillagerTradeAllowed() {
        return true;
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of("FISHERMAN", Map.of(
                "level", 3,
                "min-emeralds", 10,
                "max-emeralds", 22
        ));
    }

    @Override
    public int getAnvilBaseCost() {
        return 6;
    }

    @Override
    public int getAnvilCostPerLevel() {
        return 3;
    }

    @Override
    public int getWeight() {
        return 10;
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
                "push-per-level", 0.015,
                "max-speed", 0.65,
                "jump-power", 0.42
        );
    }

    @Override
    protected void onDisable() {
        swimming.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        swimming.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!isMechanicEnabled()) return;
        Player player = event.getPlayer();

        boolean wasSwimming = swimming.contains(player.getUniqueId());

        int level = levelOnPlayer(player);
        if (level <= 0) {
            if (wasSwimming) swimming.remove(player.getUniqueId());
            return;
        }

        if (player.isSwimming() && player.isInWater()) {
            swimming.add(player.getUniqueId());

            Vector velocity = player.getVelocity();
            if (velocity.length() < getDouble("max-speed")) {
                Vector push = player.getLocation().getDirection().normalize()
                        .multiply(getDouble("push-per-level") * level);
                player.setVelocity(velocity.add(push));
            }

            particles(player.getLocation(), Particle.BUBBLE, 6, 0.3);
            if (player.getTicksLived() % 20 == 0) {
                soundAt(player.getLocation(), Sound.ENTITY_DOLPHIN_SWIM, 0.4f, 1.2f);
            }
            return;
        }

        // Виринання: був у воді, тепер уже ні, і рух угору — підкидаємо.
        if (wasSwimming) {
            swimming.remove(player.getUniqueId());
            if (!player.isInWater() && player.getVelocity().getY() > -0.05) {
                player.setVelocity(player.getVelocity().add(new Vector(0, getDouble("jump-power"), 0)));
                soundAt(player.getLocation(), Sound.ENTITY_DOLPHIN_JUMP, 1.0f, 1.0f);
                particles(player.getLocation(), Particle.DOLPHIN, 25, 0.4);
                particles(player.getLocation(), Particle.SPLASH, 30, 0.5);
            }
        }
    }
}
