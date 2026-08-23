package me.enchantments.mechanics;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import me.enchantments.main.Mechanic;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HaloWardMechanic extends Mechanic {

    private BukkitTask task;
    // де зараз висить фейковий світловий блок кожного власника
    private final Map<UUID, Location> lightPos = new HashMap<>();

    @Override public String getId() { return "halo_ward"; }
    @Override public String getName() { return "Німб"; }
    @Override public String getDescription() { return "Власник світиться, освітлюючи все навколо себе"; }
    @Override public int getMaxLevel() { return 3; }
    @Override public List<Material> getSupportedItems() { return List.of(Material.TOTEM_OF_UNDYING); }
    @Override public List<EnchantSlot> getSlots() { return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND, EnchantSlot.HEAD); }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "light-level", 10,
            "light-per-level", 2,
            "viewer-distance", 48.0,
            "check-interval-ticks", 5
        );
    }

    @Override public double getEnchantingTableChance() { return 0.08; }
    @Override public double getChestLootChance() { return 0.05; }

    @Override
    public List<String> getLootChests() {
        return List.of("minecraft:chests/end_city_treasure", "minecraft:chests/stronghold_library");
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of("CLERIC", Map.of("level", 4, "min-emeralds", 18, "max-emeralds", 32));
    }

    @Override public int getAnvilBaseCost() { return 8; }
    @Override public int getAnvilCostPerLevel() { return 4; }
    @Override public int getWeight() { return 8; }

    @Override
    protected void onEnable() {
        int interval = getInt("check-interval-ticks");
        task = Bukkit.getScheduler().runTaskTimer(getPlugin(), this::tick, interval, interval);
    }

    @Override
    protected void onDisable() {
        if (task != null) { task.cancel(); task = null; }
        for (Location loc : lightPos.values()) revert(loc);
        lightPos.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    private void tick() {
        if (!isMechanicEnabled()) return;
        int base = getInt("light-level");
        int perLevel = getInt("light-per-level");
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            int level = levelOnPlayer(player);
            if (level <= 0) { clear(id); continue; }

            Block block = pickBlock(player);
            if (block == null) { clear(id); continue; }

            Location loc = block.getLocation();
            Location old = lightPos.get(id);
            if (old != null && old.equals(loc)) continue; // не рухався — не перемальовуємо

            if (old != null) revert(old);
            lightPos.put(id, loc);

            Light data = (Light) Material.LIGHT.createBlockData();
            data.setLevel(Math.min(data.getMaximumLevel(), base + perLevel * (level - 1)));
            data.setWaterlogged(block.getType() == Material.WATER);
            send(loc, data);
        }
    }

    // світло вішаємо в блок голови, якщо він вільний, інакше — у блок ніг
    private Block pickBlock(Player player) {
        Block eye = player.getEyeLocation().getBlock();
        if (isFree(eye)) return eye;
        Block feet = player.getLocation().getBlock();
        return isFree(feet) ? feet : null;
    }

    private boolean isFree(Block block) {
        return block.getType().isAir() || block.getType() == Material.WATER;
    }

    private void clear(UUID id) {
        Location old = lightPos.remove(id);
        if (old != null) revert(old);
    }

    // повертаємо клієнтам справжній блок
    private void revert(Location loc) {
        send(loc, loc.getBlock().getBlockData());
    }

    private void send(Location loc, BlockData data) {
        double distance = getDouble("viewer-distance");
        double squared = distance * distance;
        for (Player viewer : loc.getWorld().getPlayers()) {
            if (viewer.getLocation().distanceSquared(loc) > squared) continue;
            viewer.sendBlockChange(loc, data);
        }
    }
}