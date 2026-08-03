package me.enchantments.mechanics;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RadiantAuraMechanic extends Mechanic {

    private final Map<UUID, long[]> healWindow = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "radiant_aura";
    }

    @Override
    public String getName() {
        return "Сяйво душі";
    }

    @Override
    public String getDescription() {
        return "З убитого ворога вилітає іскра світла: трохи зцілює і ненадовго освітлює місце смерті";
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
            "heal-per-kill", 1.0,
            "cap-window-seconds", 15,
            "cap-amount", 3.0,
            "light-duration-seconds", 8
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
            "minecraft:chests/end_city_treasure",
            "minecraft:chests/stronghold_library",
            "minecraft:chests/ancient_city",
            "minecraft:chests/village/village_temple"
        );
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of("CLERIC", Map.of("villager-level", 5, "min-level", 1, "max-level", 1, "price", 38));
    }

    @Override
    public int getAnvilBaseCost() {
        return 12;
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getEntity() instanceof Monster monster)) return;

        Player killer = monster.getKiller();
        if (killer == null || levelOnPlayer(killer) == 0) return;

        lightUp(killer, monster.getLocation());

        double heal = getDouble("heal-per-kill");
        if (!withinCap(killer, heal)) return;

        Location death = monster.getLocation().add(0, 0.8, 0);
        for (int i = 1; i <= 4; i++) {
            final double progress = i / 4.0;
            Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
                if (!killer.isOnline()) return;
                Location step = death.clone().add(killer.getLocation().add(0, 1, 0)
                        .toVector().subtract(death.toVector()).multiply(progress));
                step.getWorld().spawnParticle(Particle.END_ROD, step, 4, 0.05, 0.05, 0.05, 0.005);
            }, i * 5L);
        }

        Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
            if (!killer.isOnline() || killer.isDead()) return;
            AttributeInstance attribute = killer.getAttribute(Attribute.MAX_HEALTH);
            double max = attribute != null ? attribute.getValue() : 20.0;
            killer.setHealth(Math.min(max, killer.getHealth() + heal));
            killer.getWorld().playSound(killer.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.7f);
        }, 20L);
    }

    private boolean withinCap(Player player, double heal) {
        long window = Math.max(1, getInt("cap-window-seconds")) * 1000L;
        double cap = getDouble("cap-amount");
        long now = System.currentTimeMillis();

        long[] state = healWindow.get(player.getUniqueId());
        if (state == null || now - state[0] > window) {
            healWindow.put(player.getUniqueId(), new long[]{now, (long) (heal * 100)});
            return true;
        }
        if (state[1] / 100.0 + heal > cap) return false;
        state[1] += (long) (heal * 100);
        return true;
    }

    /** Світло клієнтське — світ не змінюється, тож жодного сміття з блоків LIGHT не лишається. */
    private void lightUp(Player viewer, Location location) {
        Location at = location.getBlock().getLocation();
        if (!at.getBlock().getType().isAir()) return;

        BlockData original = at.getBlock().getBlockData();
        viewer.sendBlockChange(at, Material.LIGHT.createBlockData("[level=12]"));

        int seconds = Math.max(1, getInt("light-duration-seconds"));
        Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
            if (viewer.isOnline()) viewer.sendBlockChange(at, original);
        }, seconds * 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        healWindow.remove(e.getPlayer().getUniqueId());
    }
}
