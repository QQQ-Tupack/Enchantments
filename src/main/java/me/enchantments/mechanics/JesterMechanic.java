package me.enchantments.mechanics;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.enchantments.main.Mechanic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class JesterMechanic extends Mechanic {

    private static final Color[] CONFETTI = {
            Color.fromRGB(255, 85, 85),
            Color.fromRGB(255, 255, 85),
            Color.fromRGB(85, 255, 255),
            Color.fromRGB(255, 85, 255),
            Color.fromRGB(85, 255, 85)
    };

    private static final int CONFUSE_TICKS = 60;

    @Override public String getId() { return "jester"; }
    @Override public String getName() { return "Балаган"; }
    @Override public String getDescription() { return "Подвійне присідання підриває хлопавку: усіх навколо розкидає, а мобам збиває ціль"; }
    @Override public int getMaxLevel() { return 3; }
    @Override public List<Material> getSupportedItems() { return List.of(Material.TOTEM_OF_UNDYING); }
    @Override public List<EnchantSlot> getSlots() { return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND, EnchantSlot.HEAD); }
    @Override public boolean isEnchantingTableAllowed() { return false; }
    @Override public double getChestLootChance() { return 0.05; }
    @Override public List<String> getLootChests() { return List.of("minecraft:chests/village/village_toolsmith"); }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of("LEATHERWORKER", Map.of("level", 3, "min-emeralds", 12, "max-emeralds", 26));
    }

    @Override public int getAnvilBaseCost() { return 6; }
    @Override public int getAnvilCostPerLevel() { return 3; }
    @Override public int getWeight() { return 8; }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "cooldown-seconds", 30,
            "base-radius", 5.0,
            "radius-per-level", 1.5,
            "push-power", 1.6,
            "base-damage", 3.0,
            "damage-per-level", 1.5
        );
    }

    @Override
    protected void onDoubleSneak(Player player, int level) {
        ItemStack item = carrier(player);
        if (item == null || !ready(player, item)) return;
        cooldown(player, item, getInt("cooldown-seconds") * 20);
        burst(player, level);
    }

    /** Івента при подвійному шифті нема, тож носія шукаємо самі — руки, потім шолом. */
    private ItemStack carrier(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (levelOf(main) > 0) return main;
        ItemStack off = player.getInventory().getItemInOffHand();
        if (levelOf(off) > 0) return off;
        ItemStack helmet = player.getInventory().getHelmet();
        if (levelOf(helmet) > 0) return helmet;
        return null;
    }

    private void burst(Player player, int level) {
        double radius = getDouble("base-radius") + getDouble("radius-per-level") * (level - 1);
        double push = getDouble("push-power");
        double damage = getDouble("base-damage") + getDouble("damage-per-level") * (level - 1);
        Location center = player.getLocation();
        List<Mob> scattered = new ArrayList<>();

        for (Entity entity : player.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            // Не лише Mob: інакше гравці повністю випадали з-під ефекту.
            if (!(entity instanceof LivingEntity living) || living.equals(player)) continue;
            // getNearbyEntities дає коробку: по кутах діставало б у 1.7 раза далі за радіус.
            if (living.getLocation().distanceSquared(center) > radius * radius) continue;
            // Власних улюбленців не розкидаємо.
            if (living instanceof Tameable tameable && player.equals(tameable.getOwner())) continue;
            // Інших гравців чіпаємо лише там, де ввімкнено PvP.
            if (living instanceof Player && !living.getWorld().getPVP()) continue;

            // Y обнуляємо ДО нормалізації: інакше моб просто над головою не отримував би поштовху вбік.
            Vector direction = living.getLocation().toVector().subtract(center.toVector()).setY(0);
            if (direction.lengthSquared() < 0.01) {
                direction = center.getDirection().setY(0);
                if (direction.lengthSquared() < 0.01) direction = new Vector(0, 0, 1);
            }

            // Шкода накидає власний ванільний відкид, тому наш вектор ставимо ПІСЛЯ неї — інакше він губиться.
            if (damage > 0) living.damage(damage, player);
            living.setVelocity(direction.normalize().multiply(push).setY(0.55));

            if (living instanceof Mob mob) {
                mob.setTarget(null);
                scattered.add(mob);
            }
        }

        effects(center, radius);
        if (!scattered.isEmpty()) confuse(scattered);
    }

    /** Один setTarget(null) моб перекриває вже наступного тіка — тримаємо ціль порожньою три секунди. */
    private void confuse(List<Mob> scattered) {
        new BukkitRunnable() {
            int elapsed;

            @Override
            public void run() {
                if (elapsed >= CONFUSE_TICKS) {
                    cancel();
                    return;
                }
                for (Mob mob : scattered) {
                    if (mob.isValid()) mob.setTarget(null);
                }
                elapsed += 5;
            }
        }.runTaskTimer(getPlugin(), 5L, 5L);
    }

    private void effects(Location center, double radius) {
        soundAt(center, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.2f);
        soundAt(center, Sound.BLOCK_NOTE_BLOCK_BANJO, 1.0f, 1.0f);
        soundAt(center, Sound.BLOCK_NOTE_BLOCK_BANJO, 1.0f, 1.5f);

        Location burst = center.clone().add(0, 1.2, 0);
        for (Color color : CONFETTI) {
            particles(burst, Particle.DUST, 30, radius / 2, 0.6, radius / 2, 0.0,
                    new Particle.DustOptions(color, 1.2f));
        }
        particles(burst, Particle.FIREWORK, 40, 0.5, 0.5, 0.5,
                0.1 + ThreadLocalRandom.current().nextDouble(0.1));
    }
}