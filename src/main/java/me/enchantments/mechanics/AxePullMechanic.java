package me.enchantments.mechanics;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.enchantments.main.Mechanic;

import java.util.List;
import java.util.Map;

public final class AxePullMechanic extends Mechanic {

    @Override
    public String getId() {
        return "axe_pull";
    }

    @Override
    public String getName() {
        return "Притягання предметів";
    }

    @Override
    public String getDescription() {
        return "Автоматично притягує предмети та досвід до гравця";
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
                       Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.MAINHAND);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "collect-radius", 1.5,
            "duration-ticks", 40,
            "cooldown-ticks", 80,
            "hunger-cost", 1
        );
    }

    @Override
    protected void onRightClick(PlayerInteractEvent e, Player player, int level) {
        ItemStack axe = e.getItem();
        if (levelOf(axe) == 0) return;
        if (!ready(player, axe)) {
            deny(player);
            return;
        }
        if (!spend(player, getInt("hunger-cost"))) return;

        cooldown(player, axe, getInt("cooldown-ticks"));
        activation(player);
        new Pull(player, level).runTaskTimer(getPlugin(), 0L, 1L);
    }

    private final class Pull extends BukkitRunnable {

        private final Player player;
        private final int level;
        private int ticks;

        private Pull(Player player, int level) {
            this.player = player;
            this.level = level;
        }

        @Override
        public void run() {
            if (!player.isOnline() || player.isDead() || ticks++ >= getInt("duration-ticks")) {
                cancel();
                return;
            }

            double radius = getDouble("collect-radius") + (level - 1) * 1.5;

            for (Entity nearby : player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius)) {
                if (!(nearby instanceof ExperienceOrb || nearby instanceof Item || nearby instanceof Arrow)) continue;
                if (nearby instanceof Item item && item.getPickupDelay() > 0) continue;

                Vector pull = player.getEyeLocation().toVector().subtract(nearby.getLocation().toVector());
                if (pull.lengthSquared() > 1e-6) nearby.setVelocity(pull.normalize().multiply(0.8));
                if (ticks % 4 == 0) particles(nearby.getLocation(), Particle.ENCHANT, 1, 0, 0, 0, 0);
            }
        }
    }
}
