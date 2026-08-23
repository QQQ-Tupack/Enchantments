package me.enchantments.mechanics;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;

import me.enchantments.main.Mechanic;

import java.util.List;
import java.util.Map;

/** Тризуб із цим зачаруванням не кидається взагалі — ПКМ тримає промінь вартового. */
public final class AbyssalGazeMechanic extends Mechanic {

    @Override
    public String getId() {
        return "abyssal_gaze";
    }

    @Override
    public String getName() {
        return "Погляд безодні";
    }

    @Override
    public String getDescription() {
        return "Затиснута ПКМ тримає промінь вартового, що прошиває лінію й насилає прокляття";
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.TRIDENT);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.MAINHAND);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "range", 24.0,
            "charge-ticks", 30,
            "decay-per-tick", 2,
            "damage", 8.0,
            "pierce-radius", 0.9,
            "pierce-damage-share", 0.6,
            "water-multiplier", 1.35,
            "curse-seconds", 8,
            "cooldown-ticks", 25,
            "hunger-cost", 2
        );
    }

    @Override public boolean isEnchantingTableAllowed() { return false; }
    @Override public boolean isChestLootAllowed() { return false; }
    @Override public boolean isVillagerTradeAllowed() { return false; }
    @Override public int getAnvilBaseCost() { return 27; }

    @Override
    protected void onRightClick(PlayerInteractEvent e, Player player, int level) {
        ItemStack trident = e.getItem();
        int held = levelOf(trident);
        if (held == 0 || player.isHandRaised()) return;
        if (!ready(player, trident)) {
            deny(player);
            return;
        }

        soundAt(player.getLocation(), Sound.ENTITY_GUARDIAN_AMBIENT, 0.8f, 0.7f);
        new Gaze(player, held).runTaskTimer(getPlugin(), 2L, 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onThrow(PlayerLaunchProjectileEvent e) {
        if (!(e.getProjectile() instanceof Trident)) return;
        if (levelOf(e.getItemStack()) == 0) return;

        e.setShouldConsume(false);
        e.setCancelled(true);
    }

    private final class Gaze extends BukkitRunnable {

        private final Player player;
        private final int level;

        private LivingEntity locked;
        private int charge;

        private Gaze(Player player, int level) {
            this.player = player;
            this.level = level;
        }

        @Override
        public void run() {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (!player.isOnline() || player.isDead() || levelOf(held) == 0 || !player.isHandRaised()) {
                stop(held);
                return;
            }

            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 1, true, false, false));

            LivingEntity target = trace();
            if (target == null) {
                charge = Math.max(0, charge - getInt("decay-per-tick"));
                idle();
                return;
            }
            if (!target.equals(locked)) {
                locked = target;
                charge = 0;
                soundAt(player.getLocation(), Sound.ENTITY_GUARDIAN_ATTACK, 0.9f, 1.2f);
            }

            int needed = Math.max(10, getInt("charge-ticks") - (level - 1) * 5);
            charge++;
            draw(target, (double) charge / needed);

            if (charge < needed) return;

            charge = 0;
            if (spend(player, getInt("hunger-cost"))) fire(target);
            else stop(held);
        }

        private LivingEntity trace() {
            Location eye = player.getEyeLocation();
            Vector direction = eye.getDirection();
            double range = getDouble("range");

            RayTraceResult wall = player.getWorld().rayTraceBlocks(
                    eye, direction, range, FluidCollisionMode.NEVER, true);
            double limit = wall != null ? wall.getHitPosition().distance(eye.toVector()) : range;

            RayTraceResult hit = player.getWorld().rayTraceEntities(eye, direction, limit, 0.8,
                    entity -> entity instanceof LivingEntity && !entity.equals(player));

            return hit != null && hit.getHitEntity() instanceof LivingEntity found ? found : null;
        }

        private void idle() {
            Location hand = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(0.6));
            particles(hand, Particle.BUBBLE_POP, 2, 0.1, 0.1, 0.1, 0.01);
        }

        private void draw(LivingEntity target, double progress) {
            Location from = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(0.5));
            Location to = target.getEyeLocation();

            Color color = progress < 0.75 ? Color.fromRGB(40, 200, 190) : Color.fromRGB(215, 55, 90);
            particleLine(from, to, Particle.DUST, 0.4,
                    new Particle.DustOptions(color, (float) (0.7 + progress * 0.6)));

            if (progress >= 0.75) particles(to, Particle.BUBBLE_POP, 3, 0.3, 0.3, 0.3, 0.02);
        }

        private void fire(LivingEntity target) {
            Location eye = player.getEyeLocation();
            Vector direction = target.getEyeLocation().toVector().subtract(eye.toVector());
            double length = direction.length();
            direction.normalize();

            double damage = getDouble("damage") * (1 + (level - 1) * 0.25)
                    * (player.isInWater() ? getDouble("water-multiplier") : 1.0);
            double reach = getDouble("pierce-radius") + (level - 1) * 0.2;
            int curse = (getInt("curse-seconds") + (level - 1) * 2) * 20;

            for (Entity nearby : player.getWorld().getNearbyEntities(eye, length, length, length)) {
                if (!(nearby instanceof LivingEntity victim) || victim.equals(player)) continue;

                Vector to = victim.getLocation().add(0, victim.getHeight() / 2, 0).toVector().subtract(eye.toVector());
                double along = to.dot(direction);
                if (along < 0 || along > length + 0.5) continue;
                if (to.clone().subtract(direction.clone().multiply(along)).length() > reach) continue;

                victim.damage(damage * (victim.equals(target) ? 1.0 : getDouble("pierce-damage-share")), player);
                victim.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, curse, 2, true, true, true));

                if (victim instanceof Player drowning) drowning.setRemainingAir(0);
                particles(victim.getEyeLocation(), Particle.BUBBLE_POP, 20, 0.3, 0.4, 0.3, 0.08);
            }

            soundAt(eye, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.1f, 1.4f);
            soundAt(eye, Sound.ENTITY_GUARDIAN_ATTACK, 1.2f, 0.7f);
            particles(target.getEyeLocation(), Particle.SPLASH, 40, 0.4, 0.4, 0.4, 0.2);
            particleLine(eye, target.getEyeLocation(), Particle.DUST, 0.35,
                    new Particle.DustOptions(Color.fromRGB(255, 235, 200), 1.4f));
        }

        private void stop(ItemStack held) {
            cancel();
            cooldown(player, held, Math.max(10, getInt("cooldown-ticks") - (level - 1) * 5));
        }
    }
}
