package me.enchantments.mechanics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Пекельна зброя: комета й метеоритний дощ.
 *
 * ПКМ пускає кулю, яка при влучанні розривається на рій дрібних вогнів — кластерний удар,
 * що накриває площу більшу за саму кулю.
 * Присідання з ПКМ призначає точку, куди з неба падає злива метеоритів.
 *
 * Жоден із варіантів не чіпає блоків: yield нульовий, підпалу ландшафту немає,
 * а вибухи черепів і куль зачищають свій blockList.
 */
public final class InfernalBlastMechanic extends Mechanic {

    private static final NamespacedKey BLAST = new NamespacedKey("enchantments", "infernal_blast");
    private static final NamespacedKey SHARD = new NamespacedKey("enchantments", "infernal_shard");

    @Override
    public String getId() {
        return "infernal_blast";
    }

    @Override
    public String getName() {
        return "Пекельний спалах";
    }

    @Override
    public String getDescription() {
        return "Комета, що розривається роєм вогнів; присідання накликає метеоритний дощ";
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.BLAZE_ROD);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.MAINHAND);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "cooldown-ticks", 45,
            "rain-cooldown-ticks", 220,
            "speed", 1.3,
            "damage", 7.0,
            "radius", 3.2,
            "burn-seconds", 4,
            "cluster-shards", 6,
            "rain-meteors", 7,
            "rain-spread", 5.0
        );
    }

    @Override public boolean isEnchantingTableAllowed() { return false; }
    @Override public boolean isChestLootAllowed() { return false; }
    @Override public boolean isVillagerTradeAllowed() { return false; }
    @Override public int getAnvilBaseCost() { return 25; }

    // ignoreCancelled тут ставити не можна: при ПКМ по повітрю Bukkit виставляє
    // useItemInHand у DENY, і подія приходить уже "скасованою" — саме тому здібності
    // спрацьовували лише коли дивишся на блок
    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent e) {
        if (!isMechanicEnabled()) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack rod = e.getItem();
        int level = levelOf(rod);
        if (level == 0) return;

        Player player = e.getPlayer();
        if (player.hasCooldown(rod.getType())) return;
        e.setCancelled(true);

        if (player.isSneaking()) {
            rain(player, rod, level);
        } else {
            comet(player, rod, level);
        }
    }

    // ==========================================
    //   КОМЕТА
    // ==========================================

    private void comet(Player player, ItemStack rod, int level) {
        player.setCooldown(rod.getType(), Math.max(15, getInt("cooldown-ticks") - (level - 1) * 10));

        Fireball fireball = player.launchProjectile(Fireball.class,
                player.getLocation().getDirection().multiply(getDouble("speed")));
        prime(fireball, level, false);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.9f);
        new Comet(fireball).runTaskTimer(getPlugin(), 1L, 1L);
    }

    // ==========================================
    //   МЕТЕОРИТНИЙ ДОЩ
    // ==========================================

    private void rain(Player player, ItemStack rod, int level) {
        RayTraceResult trace = player.rayTraceBlocks(40);
        Location target = trace != null && trace.getHitPosition() != null
                ? trace.getHitPosition().toLocation(player.getWorld())
                : player.getLocation().add(player.getLocation().getDirection().multiply(20));

        player.setCooldown(rod.getType(), Math.max(100, getInt("rain-cooldown-ticks") - (level - 1) * 40));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GHAST_WARN, 1.3f, 0.6f);

        new Meteors(player, target, level).runTaskTimer(getPlugin(), 10L, 5L);
    }

    /** Метеорити падають по черзі, тож зона обстрілу читається ще до першого влучання. */
    private final class Meteors extends BukkitRunnable {

        private final Player caster;
        private final Location target;
        private final int level;
        private int launched;

        private Meteors(Player caster, Location target, int level) {
            this.caster = caster;
            this.target = target;
            this.level = level;
        }

        @Override
        public void run() {
            int total = getInt("rain-meteors") + (level - 1) * 2;
            if (!caster.isOnline() || ++launched > total) {
                cancel();
                return;
            }

            double spread = getDouble("rain-spread");
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Location drop = target.clone().add(
                    random.nextDouble(-spread, spread), 26, random.nextDouble(-spread, spread));

            // мітка на землі, куди зараз прилетить
            target.getWorld().spawnParticle(Particle.FLAME,
                    new Location(target.getWorld(), drop.getX(), target.getY() + 0.2, drop.getZ()),
                    12, 0.4, 0.05, 0.4, 0.01);

            Fireball meteor = drop.getWorld().spawn(drop, Fireball.class);
            meteor.setShooter(caster);
            meteor.setDirection(new Vector(0, -1, 0));
            meteor.setVelocity(new Vector(0, -1.4, 0));
            prime(meteor, level, true);

            drop.getWorld().playSound(drop, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.5f);
            new Comet(meteor).runTaskTimer(getPlugin(), 1L, 1L);
        }
    }

    // ==========================================
    //   ВЛУЧАННЯ
    // ==========================================

    private void prime(Fireball fireball, int level, boolean shard) {
        fireball.setYield(0);
        fireball.setIsIncendiary(false);
        fireball.getPersistentDataContainer().set(BLAST, PersistentDataType.INTEGER, level);
        if (shard) fireball.getPersistentDataContainer().set(SHARD, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(ProjectileHitEvent e) {
        if (!isMechanicEnabled()) return;

        Projectile projectile = e.getEntity();
        Integer level = projectile.getPersistentDataContainer().get(BLAST, PersistentDataType.INTEGER);
        if (level == null) return;

        Location center = projectile.getLocation();
        Player shooter = projectile.getShooter() instanceof Player player ? player : null;
        boolean shard = projectile.getPersistentDataContainer().has(SHARD, PersistentDataType.BYTE);
        double radius = (shard ? getDouble("radius") * 0.6 : getDouble("radius")) + (level - 1) * 0.4;

        flash(center, radius);
        burn(center, radius, shooter, level, shard);

        if (!shard) scatter(center, shooter, level);
    }

    /** Кластерний розрив: куля розсипається роєм вогнів, які добивають площу навколо. */
    private void scatter(Location center, Player shooter, int level) {
        int shards = getInt("cluster-shards") + (level - 1) * 2;
        for (int i = 0; i < shards; i++) {
            double angle = Math.PI * 2 * i / shards;
            Vector direction = new Vector(Math.cos(angle) * 0.5, 0.45, Math.sin(angle) * 0.5);

            SmallFireball spark = center.getWorld().spawn(center.clone().add(0, 0.5, 0), SmallFireball.class);
            spark.setShooter(shooter);
            spark.setIsIncendiary(false);
            spark.setYield(0);
            spark.setDirection(direction);
            spark.setVelocity(direction);
            spark.getPersistentDataContainer().set(BLAST, PersistentDataType.INTEGER, level);
            spark.getPersistentDataContainer().set(SHARD, PersistentDataType.BYTE, (byte) 1);
        }
        center.getWorld().playSound(center, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 0.8f);
    }

    private void burn(Location center, double radius, Player shooter, int level, boolean shard) {
        double damage = getDouble("damage") * (shard ? 0.45 : 1.0) * (1 + (level - 1) * 0.2);

        for (Entity nearby : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity target) || target.equals(shooter)) continue;

            double falloff = 1.0 - nearby.getLocation().distance(center) / (radius * 2);
            target.damage(damage * Math.max(0.3, falloff), shooter);
            target.setFireTicks(getInt("burn-seconds") * 20);
        }
    }

    private void flash(Location center, double radius) {
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
        center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
        center.getWorld().spawnParticle(Particle.FLAME, center, 50, radius / 2, radius / 2, radius / 2, 0.14);
        center.getWorld().spawnParticle(Particle.LAVA, center, 16, radius / 2, 0.2, radius / 2, 0);

        int points = (int) (radius * 10);
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            center.getWorld().spawnParticle(Particle.SMALL_FLAME,
                    center.clone().add(Math.cos(angle) * radius, 0.2, Math.sin(angle) * radius), 2, 0.05, 0.05, 0.05, 0.02);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (!e.getEntity().getPersistentDataContainer().has(BLAST, PersistentDataType.INTEGER)) return;
        e.blockList().clear();
    }

    /** Хвіст комети — снаряд має читатись як щось важче за фаєрбол гаста. */
    private static final class Comet extends BukkitRunnable {

        private final Projectile projectile;

        private Comet(Projectile projectile) {
            this.projectile = projectile;
        }

        @Override
        public void run() {
            if (!projectile.isValid() || projectile.isDead()) {
                cancel();
                return;
            }
            Location at = projectile.getLocation();
            at.getWorld().spawnParticle(Particle.FLAME, at, 8, 0.15, 0.15, 0.15, 0.02);
            at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, 3, 0.1, 0.1, 0.1, 0.01);
            at.getWorld().spawnParticle(Particle.LAVA, at, 1, 0, 0, 0, 0);
        }
    }
}
