package me.enchantments.mechanics;

import me.enchantments.main.Enchantments;
import me.enchantments.utils.EnchantMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.BlockDisplay;
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

/** Нюхання контейнерів вовком - виявляє сундуки з певним предметом */
public final class WolfSniffMechanic extends Mechanic {

    private final double sniffRadius;
    private final int sniffGlowTicks;

    public WolfSniffMechanic(EnchantMeta meta) {
        super(meta);
        var params = meta.triggers().isEmpty() ? null : meta.triggers().get(0);
        sniffRadius = params != null ? params.getDouble("radius", 50) : 50;
        sniffGlowTicks = params != null ? params.getInt("glow_ticks", 200) : 200;
    }

    private ItemStack armor(Wolf wolf) {
        return wolf.getEquipment() == null ? null : wolf.getEquipment().getItem(EquipmentSlot.BODY);
    }

    private boolean guarded(Wolf wolf) {
        return wolf.isTamed() && levelOf(armor(wolf)) > 0;
    }

    @EventHandler
    public void onSniff(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Wolf wolf) || !guarded(wolf)) return;
        Player player = e.getPlayer();
        if (!wolf.isTamed() || !wolf.getOwner().equals(player)) return;

        ItemStack held = player.getInventory().getItemInMainHand();
        boolean emptyHand = held.getType() == Material.AIR;

        if (player.isSneaking() && !emptyHand) {
            e.setCancelled(true);
            sniff(player, wolf, held.getType());
        }
    }

    private void sniff(Player player, Wolf wolf, Material target) {
        World world = wolf.getWorld();
        world.playSound(wolf.getLocation(), "entity.sniffer.sniffing", 1.0f, 1.4f);
        Location center = player.getLocation();
        List<org.bukkit.entity.Entity> markers = new ArrayList<>();

        for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
            if (Math.abs((chunk.getX() << 4) + 8 - center.getBlockX()) > sniffRadius + 16) continue;
            if (Math.abs((chunk.getZ() << 4) + 8 - center.getBlockZ()) > sniffRadius + 16) continue;

            for (BlockState state : chunk.getTileEntities()) {
                if (!(state instanceof Container container) || state.getLocation().distance(center) > sniffRadius) continue;
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
            player.sendActionBar(Component.text("Вовк нічого не знайшов поблизу...", NamedTextColor.GRAY));
            return;
        }
        player.sendActionBar(Component.text("Вовк учув " + markers.size() + " скриню(ь) з цим предметом!", NamedTextColor.YELLOW));
        new BukkitRunnable() {
            @Override public void run() { markers.forEach(org.bukkit.entity.Entity::remove); }
        }.runTaskLater(Enchantments.getInstance(), sniffGlowTicks);
    }
}
