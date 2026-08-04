package me.enchantments.mechanics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;

/**
 * Зброя візера: тавро й детонація.
 *
 * Повний натяг випускає череп, який сам доводить до цілі. Кожне влучання лишає тавро;
 * на третьому тавра зриваються й вибухають, розносячи Іссушення на всіх поруч.
 *
 * Тавра рахуються на самій жертві, тому кілька гравців можуть добивати одну ціль разом,
 * а лічильник зникає разом із мобом — без жодного стану в плагіні.
 */
public final class WitherBarrageMechanic extends Mechanic {

    private static final NamespacedKey SKULL = new NamespacedKey("enchantments", "wither_skull");
    private static final NamespacedKey MARKS = new NamespacedKey("enchantments", "wither_marks");

    @Override
    public String getId() {
        return "wither_barrage";
    }

    @Override
    public String getName() {
        return "Тавро згасання";
    }

    @Override
    public String getDescription() {
        return "Випускає череп, що переслідує ціль; три влучання зривають тавра вибухом";
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.BOW, Material.CROSSBOW);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.MAINHAND);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "cooldown-ticks", 50,
            "skull-speed", 1.1,
            "homing-strength", 0.22,
            "homing-range", 22.0,
            "hit-damage", 5.0,
            "marks-to-detonate", 3,
            "detonation-damage", 11.0,
            "detonation-radius", 4.0,
            "wither-seconds", 6
        );
    }

    @Override public boolean isEnchantingTableAllowed() { return false; }
    @Override public boolean isChestLootAllowed() { return false; }
    @Override public boolean isVillagerTradeAllowed() { return false; }
    @Override public int getAnvilBaseCost() { return 28; }

    // ==========================================
    //   ПОСТРІЛ
    // ==========================================

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getEntity() instanceof Player player)) return;

        int level = levelOf(e.getBow());
        if (level == 0 || e.getBow() == null) return;
        if (e.getForce() < 0.95f) return;
        if (player.hasCooldown(e.getBow().getType())) return;

        e.setCancelled(true); // стрілу замінює череп
        player.setCooldown(e.getBow().getType(), Math.max(20, getInt("cooldown-ticks") - (level - 1) * 10));

        WitherSkull skull = player.launchProjectile(WitherSkull.class,
                player.getLocation().getDirection().multiply(getDouble("skull-speed")));
        skull.setShooter(player);
        skull.setCharged(level >= 3);
        skull.setIsIncendiary(false);
        skull.setYield(0);
        skull.getPersistentDataContainer().set(SKULL, PersistentDataType.INTEGER, level);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 1.1f);
        new Hunter(skull).runTaskTimer(getPlugin(), 2L, 1L);
    }

    /** Череп сам доводить до найближчої цілі попереду — але повертає повільно, тож від нього можна відійти. */
    private final class Hunter extends BukkitRunnable {

        private final WitherSkull skull;
        private int ticks;

        private Hunter(WitherSkull skull) {
            this.skull = skull;
        }

        @Override
        public void run() {
            if (!skull.isValid() || skull.isDead() || ++ticks > 120) {
                cancel();
                return;
            }

            LivingEntity prey = nearestPrey();
            if (prey != null) {
                Vector desired = prey.getLocation().add(0, 0.8, 0).toVector()
                        .subtract(skull.getLocation().toVector()).normalize();
                Vector steered = skull.getDirection().add(desired.multiply(getDouble("homing-strength"))).normalize();
                skull.setDirection(steered);
                skull.setVelocity(steered.multiply(getDouble("skull-speed")));
            }

            Location at = skull.getLocation();
            at.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, at, 4, 0.1, 0.1, 0.1, 0.01);
            at.getWorld().spawnParticle(Particle.SMOKE, at, 3, 0.15, 0.15, 0.15, 0.01);
            at.getWorld().spawnParticle(Particle.SOUL, at, 1, 0.2, 0.2, 0.2, 0);
        }

        private LivingEntity nearestPrey() {
            double range = getDouble("homing-range");
            LivingEntity best = null;
            double bestDistance = range;

            for (Entity nearby : skull.getNearbyEntities(range, range, range)) {
                if (!(nearby instanceof LivingEntity candidate)) continue;
                if (candidate.equals(skull.getShooter())) continue;
                if (skull.getDirection().dot(candidate.getLocation().toVector()
                        .subtract(skull.getLocation().toVector()).normalize()) < 0.35) continue; // тільки попереду

                double distance = candidate.getLocation().distance(skull.getLocation());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
            return best;
        }
    }

    // ==========================================
    //   ТАВРА Й ДЕТОНАЦІЯ
    // ==========================================

    @EventHandler(ignoreCancelled = true)
    public void onHit(ProjectileHitEvent e) {
        if (!isMechanicEnabled()) return;

        Projectile projectile = e.getEntity();
        Integer level = projectile.getPersistentDataContainer().get(SKULL, PersistentDataType.INTEGER);
        if (level == null) return;
        if (!(e.getHitEntity() instanceof LivingEntity target)) return;

        Player shooter = projectile.getShooter() instanceof Player player ? player : null;
        target.damage(getDouble("hit-damage") * (1 + (level - 1) * 0.15), shooter);

        int marks = target.getPersistentDataContainer()
                .getOrDefault(MARKS, PersistentDataType.INTEGER, 0) + 1;

        if (marks >= getInt("marks-to-detonate")) {
            target.getPersistentDataContainer().remove(MARKS);
            detonate(target, shooter, level);
        } else {
            target.getPersistentDataContainer().set(MARKS, PersistentDataType.INTEGER, marks);
            brand(target, marks);
        }
    }

    /** Тавра кружляють навколо жертви — скільки їх, стільки й черепів у кільці. */
    private void brand(LivingEntity target, int marks) {
        Location at = target.getLocation();
        int needed = Math.max(1, getInt("marks-to-detonate"));

        target.getWorld().playSound(at, Sound.ENTITY_WITHER_HURT, 0.6f, 1.5f + marks * 0.2f);
        target.getWorld().playSound(at, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.7f, 0.6f + marks * 0.35f);

        // останнє тавро перед детонацією звучить як зведений запал
        if (marks == needed - 1) {
            target.getWorld().playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 1.4f);
            target.getWorld().playSound(at, Sound.ENTITY_CREEPER_PRIMED, 0.5f, 1.6f);
        }
        for (int i = 0; i < marks; i++) {
            double angle = Math.PI * 2 * i / marks;
            target.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                    target.getLocation().add(Math.cos(angle) * 0.8, 1.2, Math.sin(angle) * 0.8), 6, 0.05, 0.05, 0.05, 0.01);
        }
        target.getWorld().spawnParticle(Particle.SMOKE, target.getLocation().add(0, 1.2, 0), 10, 0.3, 0.3, 0.3, 0.02);
    }

    private void detonate(LivingEntity epicenter, Player shooter, int level) {
        double radius = getDouble("detonation-radius") + (level - 1) * 0.5;
        Location at = epicenter.getLocation().add(0, 1, 0);

        at.getWorld().playSound(at, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.2f, 0.7f);
        at.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
        at.getWorld().spawnParticle(Particle.SOUL, at, 60, radius / 2, 1.0, radius / 2, 0.15);
        at.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, at, 40, radius / 2, 0.8, radius / 2, 0.08);

        for (Entity nearby : at.getWorld().getNearbyEntities(at, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity victim) || victim.equals(shooter)) continue;

            victim.damage(getDouble("detonation-damage") * (1 + (level - 1) * 0.2), shooter);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,
                    getInt("wither-seconds") * 20, level - 1, true, true, true));
        }
    }

    /** Черепи не риють ландшафт — увесь ефект у таврах. */
    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (!e.getEntity().getPersistentDataContainer().has(SKULL, PersistentDataType.INTEGER)) return;
        e.blockList().clear();
    }
}
