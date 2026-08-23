package me.enchantments.mechanics;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import me.enchantments.main.Mechanic;

import java.util.List;
import java.util.Map;

public final class AxeCleaveMechanic extends Mechanic {

    @Override
    public String getId() {
        return "axe_cleave";
    }

    @Override
    public String getName() {
        return "Розтинання";
    }

    @Override
    public String getDescription() {
        return "Наносить урон усім ворогам навколо при активації";
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
        return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "cleave-radius", 2.0,
            "cleave-damage", 3.0,
            "duration-ticks", 40,
            "cooldown-ticks", 100,
            "hunger-cost", 3
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
        new Cleave(player, level).runTaskTimer(getPlugin(), 0L, 2L);
    }

    private final class Cleave extends BukkitRunnable {

        private final Player player;
        private final int level;
        private int ticks;

        private Cleave(Player player, int level) {
            this.player = player;
            this.level = level;
        }

        @Override
        public void run() {
            if (!player.isOnline() || player.isDead() || ticks >= getInt("duration-ticks")) {
                cancel();
                return;
            }
            ticks += 2;

            double radius = getDouble("cleave-radius") + (level - 1) * 0.5;
            double damage = getDouble("cleave-damage");

            for (Entity nearby : player.getWorld().getNearbyEntities(player.getEyeLocation(), radius, radius, radius)) {
                if (nearby instanceof LivingEntity target && !target.equals(player)) target.damage(damage, player);
            }

            particleCircle(player.getLocation().add(0, 0.9, 0), Particle.CRIT, radius, 14);
            soundAt(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.4f, 1.3f);
        }
    }
}
