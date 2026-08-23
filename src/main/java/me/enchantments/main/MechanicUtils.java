package me.enchantments.main;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Спільні утиліти механік: анімація активації, партікли, звуки,
 * голод замість мани й ванільний кулдаун предмета замість власних лічильників.
 */
public abstract class MechanicUtils {

    protected JavaPlugin plugin() {
        return Mechanic.getPlugin();
    }

    // --- ЗАГАЛЬНЕ ОФОРМЛЕННЯ ---

    protected Particle activationParticle() { return Particle.ENCHANT; }
    protected Sound activationChime() { return Sound.BLOCK_AMETHYST_BLOCK_CHIME; }
    protected Sound activationCast() { return Sound.ENTITY_ILLUSIONER_CAST_SPELL; }
    protected Sound denySound() { return Sound.BLOCK_NOTE_BLOCK_BASS; }

    /** Кільце рун, що розходиться від ніг і піднімається. Три кадри через тік. */
    public void activation(Player player) {
        if (player != null) activation(player.getLocation());
    }

    public void activation(Location at) {
        if (at == null || at.getWorld() == null) return;

        Location origin = at.clone();
        soundAt(origin, activationChime(), 0.5f, 1.3f);
        soundAt(origin, activationCast(), 0.4f, 1.7f);

        new BukkitRunnable() {
            int frame;

            @Override
            public void run() {
                if (frame > 2) {
                    cancel();
                    return;
                }

                double radius = 0.55 + frame * 0.45;
                double height = 0.15 + frame * 0.3;
                int points = 10 + frame * 6;

                for (int i = 0; i < points; i++) {
                    double angle = 2 * Math.PI * i / points;
                    Location point = origin.clone().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
                    particles(point, activationParticle(), 1, 0, 0, 0, 0);
                }
                frame++;
            }
        }.runTaskTimer(plugin(), 0L, 1L);
    }

    /** Відмова: глухий удар і димок тільки для самого гравця. */
    public void deny(Player player) {
        if (player == null) return;
        soundFor(player, denySound(), 0.5f, 0.6f);
        particles(player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.5)),
                Particle.SMOKE, 5, 0.1, 0.1, 0.1, 0.01);
    }

    // --- ГОЛОД ЗАМІСТЬ МАНИ ---

    public boolean hasHunger(Player player, int cost) {
        return cost <= 0 || player.getGameMode() == GameMode.CREATIVE || player.getFoodLevel() >= cost;
    }

    /** Знімає голод; якщо не вистачає — програє відмову й повертає false. */
    public boolean spend(Player player, int cost) {
        if (cost <= 0 || player.getGameMode() == GameMode.CREATIVE) return true;
        if (player.getFoodLevel() < cost) {
            deny(player);
            return false;
        }
        player.setFoodLevel(player.getFoodLevel() - cost);
        return true;
    }

    // --- ВАНІЛЬНИЙ КУЛДАУН ПРЕДМЕТА ---

    public boolean ready(Player player, ItemStack item) {
        return item != null && !player.hasCooldown(item.getType());
    }

    public void cooldown(Player player, ItemStack item, int ticks) {
        if (item != null && ticks > 0) player.setCooldown(item.getType(), ticks);
    }

    // --- ПАРТІКЛИ ---

    public void particles(Location location, Particle particle, int count, double spread) {
        particles(location, particle, count, spread, spread, spread, 0, null);
    }

    public void particles(Location location, Particle particle, int count,
                          double spreadX, double spreadY, double spreadZ, double extra) {
        particles(location, particle, count, spreadX, spreadY, spreadZ, extra, null);
    }

    /** `data` — DustOptions, BlockData тощо; null для звичайних партіклів. */
    public void particles(Location location, Particle particle, int count,
                          double spreadX, double spreadY, double spreadZ, double extra, Object data) {
        if (location == null || location.getWorld() == null || particle == null) return;
        location.getWorld().spawnParticle(particle, location, count, spreadX, spreadY, spreadZ, extra, data);
    }

    public void particleLine(Location from, Location to, Particle particle, double step) {
        particleLine(from, to, particle, step, null);
    }

    public void particleLine(Location from, Location to, Particle particle, double step, Object data) {
        if (from == null || to == null || from.getWorld() == null) return;
        if (!from.getWorld().equals(to.getWorld())) return;
        if (step <= 0) step = 0.3;

        Vector direction = to.toVector().subtract(from.toVector());
        double length = direction.length();
        if (length <= 0) return;
        direction.normalize().multiply(step);

        Location point = from.clone();
        for (double travelled = 0; travelled <= length; travelled += step) {
            particles(point, particle, 1, 0, 0, 0, 0, data);
            point.add(direction);
        }
    }

    public void particleCircle(Location center, Particle particle, double radius, int points) {
        if (center == null || center.getWorld() == null || points <= 0) return;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            particles(center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius),
                    particle, 1, 0, 0, 0, 0, null);
        }
    }

    // --- ЗВУКИ ---

    public void soundAt(Location location, Sound sound, float volume, float pitch) {
        if (location == null || location.getWorld() == null || sound == null) return;
        location.getWorld().playSound(location, sound, SoundCategory.PLAYERS, volume, pitch);
    }

    public void soundFor(Player player, Sound sound, float volume, float pitch) {
        if (player == null || sound == null) return;
        player.playSound(player.getLocation(), sound, SoundCategory.PLAYERS, volume, pitch);
    }

    // --- ЗАПАСНИЙ ВИХІД ---

    /** Жодна механіка цього не викликає; лишено на випадок, коли без тексту не обійтись. */
    public void actionbar(Player player, String message) {
        if (player == null || message == null || message.isEmpty()) return;
        player.sendActionBar(MiniMessage.miniMessage().deserialize(message));
    }

    public void actionbar(Player player, Component component) {
        if (player == null || component == null) return;
        player.sendActionBar(component);
    }
}
