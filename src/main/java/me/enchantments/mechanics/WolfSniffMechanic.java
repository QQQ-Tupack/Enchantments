package me.enchantments.mechanics;

import me.enchantments.main.Mechanic;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class WolfSniffMechanic extends Mechanic {

    @Override public String getId() { return "wolf_sniff"; }
    @Override public String getName() { return "Гострий нюх"; }
    @Override public String getDescription() { return "Дозволяє вовку знаходити контейнери з певним предметом"; }
    @Override public int getMaxLevel() { return 3; }
    @Override public List<Material> getSupportedItems() { return List.of(Material.WOLF_ARMOR); }
    @Override public List<EnchantSlot> getSlots() { return List.of(EnchantSlot.BODY); }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of("radius", 50.0, "glow-ticks", 200);
    }

    private ItemStack armor(Wolf wolf) {
        return wolf.getEquipment() == null ? null : wolf.getEquipment().getItem(EquipmentSlot.BODY);
    }

    private boolean guarded(Wolf wolf) {
        return wolf.isTamed() && levelOf(armor(wolf)) > 0;
    }

    @EventHandler
    public void onSniff(PlayerInteractEntityEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getRightClicked() instanceof Wolf wolf) || !guarded(wolf)) return;

        Player player = e.getPlayer();
        if (!wolf.isTamed() || !wolf.getOwner().equals(player)) return;

        ItemStack held = player.getInventory().getItemInMainHand();
        if (!player.isSneaking() || held.getType() == Material.AIR) return;

        e.setCancelled(true);
        sniff(player, wolf, held.getType());
    }

    private void sniff(Player player, Wolf wolf, Material target) {
        double radius = getDouble("radius");
        int glowTicks = getInt("glow-ticks");
        World world = wolf.getWorld();
        Location center = player.getLocation();

        world.playSound(wolf.getLocation(), "entity.sniffer.sniffing", 1.0f, 1.4f);

        List<Entity> markers = new ArrayList<>();
        for (Chunk chunk : world.getLoadedChunks()) {
            if (Math.abs((chunk.getX() << 4) + 8 - center.getBlockX()) > radius + 16) continue;
            if (Math.abs((chunk.getZ() << 4) + 8 - center.getBlockZ()) > radius + 16) continue;

            for (BlockState state : chunk.getTileEntities()) {
                if (!(state instanceof Container container) || state.getLocation().distance(center) > radius) continue;
                boolean hasItem = Arrays.stream(container.getInventory().getContents())
                        .anyMatch(stack -> stack != null && stack.getType() == target);
                if (!hasItem) continue;

                BlockDisplay display = (BlockDisplay) world.spawnEntity(state.getLocation(), EntityType.BLOCK_DISPLAY);
                display.setBlock(state.getBlockData());
                display.setGlowing(true);
                display.setInvulnerable(true);
                markers.add(display);
            }
        }

        if (markers.isEmpty()) {
            soundFor(player, Sound.ENTITY_WOLF_WHINE, 0.6f, 1.1f);
            return;
        }

        soundFor(player, Sound.ENTITY_WOLF_AMBIENT, 0.8f, 1.3f);
        particles(wolf.getLocation().add(0, 0.6, 0), Particle.HAPPY_VILLAGER, 12, 0.4, 0.3, 0.4, 0);

        new BukkitRunnable() {
            @Override public void run() { markers.forEach(Entity::remove); }
        }.runTaskLater(getPlugin(), glowTicks);
    }
}
