package me.enchantments.mechanics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public final class VoidRescueMechanic extends Mechanic {

    @Override
    public String getId() {
        return "void_rescue";
    }

    @Override
    public String getName() {
        return "Порятунок з безодні";
    }

    @Override
    public String getDescription() {
        return "Рятує гравця від падіння у безодню, телепортуючи на найближчий клаптик землі, витрачаючи тотем";
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
        return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "search-radius", 128,
            "search-step", 8
        );
    }

    private ItemStack totem(Player p) {
        if (p.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING) return p.getInventory().getItemInMainHand();
        if (p.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING) return p.getInventory().getItemInOffHand();
        return null;
    }

    @EventHandler
    public void onVoidFall(EntityDamageEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getEntity() instanceof Player player) || e.getCause() != EntityDamageEvent.DamageCause.VOID) return;

        ItemStack totem = totem(player);
        if (levelOf(totem) == 0) return;

        e.setCancelled(true);
        consume(totem);

        int searchRadius = getInt("search-radius");
        int searchStep = getInt("search-step");
        Location safe = findNearestLand(player, searchRadius, searchStep);

        player.teleport(safe);
        player.setFallDistance(0);
        player.getWorld().playSound(safe, Sound.ITEM_TOTEM_USE, 1f, 1f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, safe, 30, 0.5, 1, 0.5, 0.2);
    }

    private void consume(ItemStack item) {
        if (item.getAmount() <= 1) item.setAmount(0);
        else item.setAmount(item.getAmount() - 1);
    }

    private Location findNearestLand(Player player, int searchRadius, int step) {
        World world = player.getWorld();
        int centerX = player.getLocation().getBlockX();
        int centerZ = player.getLocation().getBlockZ();

        for (int r = 0; r <= searchRadius; r += step) {
            for (int dx = -r; dx <= r; dx += step) {
                for (int dz = -r; dz <= r; dz += step) {
                    if (r != 0 && Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    Location found = groundAt(world, centerX + dx, centerZ + dz);
                    if (found != null) return found;
                }
            }
        }
        return world.getSpawnLocation();
    }

    private Location groundAt(World world, int x, int z) {
        Block ground = world.getHighestBlockAt(x, z);
        if (ground.getY() <= world.getMinHeight()) return null;
        String name = ground.getType().name();
        if (name.contains("LAVA") || name.contains("FIRE") || name.contains("MAGMA")) return null;
        return ground.getLocation().add(0.5, 1, 0.5);
    }
}
