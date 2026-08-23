package me.enchantments.mechanics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.enchantments.main.Mechanic;

import java.util.List;
import java.util.Map;

public final class WitherBarrageMechanic extends Mechanic {

    private static final NamespacedKey SKULL = new NamespacedKey("enchantments", "wither_skull");
    private static final NamespacedKey MARKS = new NamespacedKey("enchantments", "wither_marks");

    @Override public String getId() { return "wither_barrage"; }
    @Override public String getName() { return "Тавро згасання"; }
    @Override public String getDescription() { return "ПКМ випускає череп, що переслідує ціль; три влучання зривають тавра вибухом"; }
    @Override public int getMaxLevel() { return 3; }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(
            Material.NETHERITE_SWORD,
            Material.DIAMOND_SWORD,
            Material.IRON_SWORD,
            Material.GOLDEN_SWORD,
            Material.STONE_SWORD,
            Material.WOODEN_SWORD
        );
    }

    @Override public List<EnchantSlot> getSlots() { return List.of(EnchantSlot.MAINHAND); }

    @Override public boolean isEnchantingTableAllowed() { return false; }
    @Override public boolean isChestLootAllowed() { return false; }
    @Override public boolean isVillagerTradeAllowed() { return false; }
    @Override public int getAnvilBaseCost() { return 28; }
    @Override protected boolean grindstoneRemovableDefault() { return false; }

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

    @Override
    protected void onRightClick(PlayerInteractEvent e, Player player, int level) {
        ItemStack weapon = e.getItem();
        if (weapon == null) return;

        Block clicked = e.getClickedBlock();
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && clicked != null
                && clicked.getType().isInteractable() && !player.isSneaking()) return;

        if (!ready(player, weapon)) return;
        e.setCancelled(true);
        cooldown(player, weapon, Math.max(20, getInt("cooldown-ticks") - (level - 1) * 10));

        WitherSkull skull = player.launchProjectile(WitherSkull.class,
                player.getLocation().getDirection().multiply(getDouble("skull-speed")));
        skull.setShooter(player);
        skull.setCharged(level >= 3);
        skull.setIsIncendiary(false);
        skull.setYield(0);
        skull.getPersistentDataContainer().set(SKULL, PersistentDataType.INTEGER, level);

        soundAt(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 1.1f);
        new Hunter(skull).runTaskTimer(getPlugin(), 2L, 1L);
    }

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
            particles(at, Particle.SOUL_FIRE_FLAME, 4, 0.1, 0.1, 0.1, 0.01);
            particles(at, Particle.SMOKE, 3, 0.15, 0.15, 0.15, 0.01);
            particles(at, Particle.SOUL, 1, 0.2, 0.2, 0.2, 0);
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

    private void brand(LivingEntity target, int marks) {
        Location at = target.getLocation();
        int needed = Math.max(1, getInt("marks-to-detonate"));

        soundAt(at, Sound.ENTITY_WITHER_HURT, 0.6f, 1.5f + marks * 0.2f);
        soundAt(at, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.7f, 0.6f + marks * 0.35f);

        if (marks == needed - 1) {
            soundAt(at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 1.4f);
            soundAt(at, Sound.ENTITY_CREEPER_PRIMED, 0.5f, 1.6f);
        }
        for (int i = 0; i < marks; i++) {
            double angle = Math.PI * 2 * i / marks;
            particles(target.getLocation().add(Math.cos(angle) * 0.8, 1.2, Math.sin(angle) * 0.8),
                    Particle.SOUL_FIRE_FLAME, 6, 0.05, 0.05, 0.05, 0.01);
        }
        particles(target.getLocation().add(0, 1.2, 0), Particle.SMOKE, 10, 0.3, 0.3, 0.3, 0.02);
    }

    private void detonate(LivingEntity epicenter, Player shooter, int level) {
        double radius = getDouble("detonation-radius") + (level - 1) * 0.5;
        Location at = epicenter.getLocation().add(0, 1, 0);

        soundAt(at, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.2f, 0.7f);
        particles(at, Particle.EXPLOSION_EMITTER, 1, 0, 0, 0, 0);
        particles(at, Particle.SOUL, 60, radius / 2, 1.0, radius / 2, 0.15);
        particles(at, Particle.SOUL_FIRE_FLAME, 40, radius / 2, 0.8, radius / 2, 0.08);

        for (Entity nearby : at.getWorld().getNearbyEntities(at, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity victim) || victim.equals(shooter)) continue;

            victim.damage(getDouble("detonation-damage") * (1 + (level - 1) * 0.2), shooter);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,
                    getInt("wither-seconds") * 20, level - 1, true, true, true));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (!e.getEntity().getPersistentDataContainer().has(SKULL, PersistentDataType.INTEGER)) return;
        e.blockList().clear();
    }
}
