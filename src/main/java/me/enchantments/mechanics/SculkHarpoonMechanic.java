package me.enchantments.mechanics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;

/**
 * Гарпун глибин.
 *
 * Кинутий тризуб тягне: влучив у живе — притягує його до тебе, влучив у блок — притягує тебе.
 * Одна кнопка, два протилежні застосування, і обирає між ними не гравець, а те, куди він влучив.
 *
 * Зброя не про урон, а про дистанцію: витягти лучника з даху, зірвати втікача, скоротити прірву.
 * Тому урон лишається ванільним тризубним — сила тут у контролі.
 */
public final class SculkHarpoonMechanic extends Mechanic {

    private static final NamespacedKey HARPOON = new NamespacedKey("enchantments", "sculk_harpoon");
    private static final NamespacedKey GRAPPLING = new NamespacedKey("enchantments", "sculk_grappling");

    @Override
    public String getId() {
        return "sculk_harpoon";
    }

    @Override
    public String getName() {
        return "Гарпун глибин";
    }

    @Override
    public String getDescription() {
        return "Тризуб тягне: живу ціль — до тебе, блок — тебе до нього";
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
            "cooldown-ticks", 60,
            "pull-strength", 1.4,
            "grapple-strength", 1.5,
            "grapple-lift", 0.55,
            "stun-seconds", 2,
            "chain-fade-ticks", 8,
            "safe-landing-seconds", 4
        );
    }

    @Override public boolean isEnchantingTableAllowed() { return false; }
    @Override public boolean isChestLootAllowed() { return false; }
    @Override public boolean isVillagerTradeAllowed() { return false; }
    @Override public int getAnvilBaseCost() { return 30; }

    // ==========================================
    //   КИДОК
    // ==========================================

    @EventHandler
    public void onThrow(ProjectileLaunchEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getEntity() instanceof Trident trident)) return;
        if (!(trident.getShooter() instanceof Player player)) return;

        int level = levelOf(trident.getItemStack());
        if (level == 0) return;

        trident.getPersistentDataContainer().set(HARPOON, PersistentDataType.INTEGER, level);
        player.setCooldown(Material.TRIDENT, Math.max(20, getInt("cooldown-ticks") - (level - 1) * 15));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.7f, 1.4f);

        new Line(trident).runTaskTimer(getPlugin(), 1L, 1L);
    }

    /** Тризуб тягне за собою скалковий шнур, тож траєкторія читається в польоті. */
    private static final class Line extends BukkitRunnable {

        private final Trident trident;

        private Line(Trident trident) {
            this.trident = trident;
        }

        @Override
        public void run() {
            if (!trident.isValid() || trident.isDead() || trident.isInBlock()) {
                cancel();
                return;
            }
            Location at = trident.getLocation();
            at.getWorld().spawnParticle(Particle.SCULK_SOUL, at, 2, 0.05, 0.05, 0.05, 0);
            at.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, at, 1, 0.1, 0.1, 0.1, 0);
        }
    }

    // ==========================================
    //   ВЛУЧАННЯ
    // ==========================================

    @EventHandler
    public void onHit(ProjectileHitEvent e) {
        if (!isMechanicEnabled()) return;

        Integer level = e.getEntity().getPersistentDataContainer().get(HARPOON, PersistentDataType.INTEGER);
        if (level == null) return;
        if (!(e.getEntity().getShooter() instanceof Player player)) return;

        if (e.getHitEntity() instanceof LivingEntity target && !target.equals(player)) {
            reel(player, target, level);
        } else if (e.getHitBlock() != null) {
            grapple(player, e.getEntity().getLocation(), level);
        }
    }

    /** Ціль летить до гравця — і на кілька секунд глухне, щоб не встигла відповісти в польоті. */
    private void reel(Player player, LivingEntity target, int level) {
        Vector pull = player.getLocation().toVector()
                .subtract(target.getLocation().toVector()).normalize()
                .multiply(getDouble("pull-strength") * (1 + (level - 1) * 0.15));

        target.setVelocity(pull.setY(Math.max(0.35, pull.getY())));
        target.setFallDistance(0);

        int stun = getInt("stun-seconds") * 20;
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, stun, 0, true, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, stun, level, true, true, true));

        chain(player.getEyeLocation(), target.getLocation().add(0, 1, 0));
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.5f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1f, 0.6f);
    }

    /** Влучив у камінь — гарпун тягне тебе, і приземлення після цього не б'є. */
    private void grapple(Player player, Location anchor, int level) {
        Vector pull = anchor.toVector().subtract(player.getLocation().toVector()).normalize()
                .multiply(getDouble("grapple-strength") * (1 + (level - 1) * 0.1));

        player.setVelocity(pull.setY(pull.getY() + getDouble("grapple-lift")));
        player.setFallDistance(0);
        player.getPersistentDataContainer().set(GRAPPLING, PersistentDataType.LONG,
                System.currentTimeMillis() + getInt("safe-landing-seconds") * 1000L);

        chain(player.getEyeLocation(), anchor);
        player.getWorld().playSound(anchor, Sound.BLOCK_CHAIN_PLACE, 1f, 0.7f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.8f, 1.3f);
    }

    /** Ланцюг між двома точками — його видно рівно стільки, скільки триває ривок. */
    private void chain(Location from, Location to) {
        Vector step = to.toVector().subtract(from.toVector());
        int points = (int) Math.min(48, step.length() * 3);
        if (points <= 0) return;

        step.multiply(1.0 / points);
        for (int i = 0; i <= points; i++) {
            Location point = from.clone().add(step.clone().multiply(i));
            point.getWorld().spawnParticle(Particle.SCULK_SOUL, point, 1, 0, 0, 0, 0);
            if (i % 4 == 0) point.getWorld().spawnParticle(Particle.SONIC_BOOM, point, 1);
        }
    }

    /** Гак не має вбивати власника об скелю, на яку сам його й закинув. */
    @EventHandler(ignoreCancelled = true)
    public void onLanding(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(e.getEntity() instanceof Player player)) return;

        Long until = player.getPersistentDataContainer().get(GRAPPLING, PersistentDataType.LONG);
        if (until == null) return;

        player.getPersistentDataContainer().remove(GRAPPLING);
        if (until < System.currentTimeMillis()) return;

        e.setCancelled(true);
        player.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, player.getLocation(), 12, 0.3, 0.1, 0.3, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_SCULK_BREAK, 0.6f, 0.8f);
    }
}
