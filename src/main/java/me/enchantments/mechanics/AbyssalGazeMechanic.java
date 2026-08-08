package me.enchantments.mechanics;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Погляд безодні. Тризуб не кидається — поки затиснута ПКМ, він тримає промінь вартового.
 *
 * Промінь чіпляється за того, на кого дивишся, і наливається кольором: бірюзовий поки
 * наводиться, багряний за мить до розряду. Відвів приціл — заряд стікає назад.
 * Розряд прошиває все, що стоїть на лінії, і лишає прокляття старійшини.
 *
 * У воді безодня своя: під водою промінь б'є помітно важче.
 *
 * Кидок скасовується, поки промінь активний, тож тризуб не витрачається й не зношується.
 * Riptide на цей тризуб вішати не можна — воно спрацьовує на відпусканні, повз наш обробник.
 */
public final class AbyssalGazeMechanic extends Mechanic {

    /** Хто зараз тримає промінь — потрібно, щоб перехопити кидок на відпусканні. */
    private static final Set<UUID> AIMING = new HashSet<>();

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
            "cooldown-ticks", 25
        );
    }

    @Override public boolean isEnchantingTableAllowed() { return false; }
    @Override public boolean isChestLootAllowed() { return false; }
    @Override public boolean isVillagerTradeAllowed() { return false; }
    @Override public int getAnvilBaseCost() { return 27; }

    // ==========================================
    //   УТРИМАННЯ КНОПКИ
    // ==========================================

    // подію НЕ скасовуємо: саме ванільна анімація натягу тризуба тримає isHandRaised(),
    // по якому ми й розуміємо, що кнопка досі затиснута
    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent e) {
        if (!isMechanicEnabled()) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack trident = e.getItem();
        int level = levelOf(trident);
        if (level == 0) return;

        Player player = e.getPlayer();
        if (player.hasCooldown(trident.getType())) return;
        if (!AIMING.add(player.getUniqueId())) return;

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GUARDIAN_AMBIENT, 0.8f, 0.7f);
        new Gaze(player, level).runTaskTimer(getPlugin(), 2L, 1L);
    }

    /** Відпустив кнопку — тризуб мав би полетіти. Перехоплюємо: предмет лишається в руці цілим. */
    @EventHandler(ignoreCancelled = true)
    public void onThrow(PlayerLaunchProjectileEvent e) {
        if (!(e.getProjectile() instanceof Trident)) return;
        if (!AIMING.contains(e.getPlayer().getUniqueId())) return;

        e.setShouldConsume(false);
        e.setCancelled(true);
    }

    // ==========================================
    //   ПРОМІНЬ
    // ==========================================

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
                stop();
                return;
            }

            // промінь тримає на місці: поки ціляєшся, ти не бігаєш
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 1, true, false, false));

            LivingEntity target = trace();
            if (target == null) {
                charge = Math.max(0, charge - getInt("decay-per-tick"));
                idle();
                return;
            }
            if (!target.equals(locked)) { // перевів приціл — наводка починається заново
                locked = target;
                charge = 0;
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GUARDIAN_ATTACK, 0.9f, 1.2f);
            }

            int needed = Math.max(10, getInt("charge-ticks") - (level - 1) * 5);
            charge++;
            draw(target, (double) charge / needed);

            if (charge >= needed) {
                fire(target);
                charge = 0;
            }
        }

        /** Ціль — перша жива істота на промені, але не крізь стіну. */
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

        /** Ненаведений промінь ще жевріє в руці — видно, що зброя заряджена. */
        private void idle() {
            Location hand = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(0.6));
            hand.getWorld().spawnParticle(Particle.BUBBLE_POP, hand, 2, 0.1, 0.1, 0.1, 0.01);
        }

        /** Бірюзовий → багряний: за кольором променя видно, скільки лишилось до розряду. */
        private void draw(LivingEntity target, double progress) {
            Location from = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(0.5));
            Location to = target.getEyeLocation();
            World world = from.getWorld();

            Color color = progress < 0.75
                    ? Color.fromRGB(40, 200, 190)
                    : Color.fromRGB(215, 55, 90);
            Particle.DustOptions dust = new Particle.DustOptions(color, (float) (0.7 + progress * 0.6));

            Vector path = to.toVector().subtract(from.toVector());
            double length = path.length();
            Vector step = path.normalize().multiply(0.4);

            Location point = from.clone();
            for (double travelled = 0; travelled < length; travelled += 0.4) {
                point.add(step);
                world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust);
            }

            if (progress >= 0.75) {
                world.spawnParticle(Particle.BUBBLE_POP, to, 3, 0.3, 0.3, 0.3, 0.02);
            }
        }

        /** Розряд прошиває лінію: наведена ціль дістає повністю, решта — часткою. */
        private void fire(LivingEntity target) {
            Location eye = player.getEyeLocation();
            World world = player.getWorld();

            Vector direction = target.getEyeLocation().toVector().subtract(eye.toVector());
            double length = direction.length();
            direction.normalize();

            double damage = getDouble("damage") * (1 + (level - 1) * 0.25)
                    * (player.isInWater() ? getDouble("water-multiplier") : 1.0);
            double reach = getDouble("pierce-radius") + (level - 1) * 0.2;

            for (Entity nearby : world.getNearbyEntities(eye, length, length, length)) {
                if (!(nearby instanceof LivingEntity victim) || victim.equals(player)) continue;

                Vector to = victim.getLocation().add(0, victim.getHeight() / 2, 0).toVector().subtract(eye.toVector());
                double along = to.dot(direction);
                if (along < 0 || along > length + 0.5) continue;
                if (to.clone().subtract(direction.clone().multiply(along)).length() > reach) continue;

                victim.damage(damage * (victim.equals(target) ? 1.0 : getDouble("pierce-damage-share")), player);
                victim.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE,
                        (getInt("curse-seconds") + (level - 1) * 2) * 20, 2, true, true, true));

                if (victim instanceof Player drowning) drowning.setRemainingAir(0);
                world.spawnParticle(Particle.BUBBLE_POP, victim.getEyeLocation(), 20, 0.3, 0.4, 0.3, 0.08);
            }

            world.playSound(eye, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.1f, 1.4f);
            world.playSound(eye, Sound.ENTITY_GUARDIAN_ATTACK, 1.2f, 0.7f);
            world.spawnParticle(Particle.SPLASH, target.getEyeLocation(), 40, 0.4, 0.4, 0.4, 0.2);

            Location point = eye.clone();
            for (double travelled = 0; travelled < length; travelled += 0.35) {
                point.add(direction.clone().multiply(0.35));
                world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 235, 200), 1.4f));
            }
        }

        private void stop() {
            cancel();
            player.setCooldown(Material.TRIDENT, Math.max(10, getInt("cooldown-ticks") - (level - 1) * 5));

            // з AIMING виходимо із затримкою: кидок на відпусканні прилітає вже після зупинки задачі
            getPlugin().getServer().getScheduler().runTaskLater(getPlugin(),
                    () -> AIMING.remove(player.getUniqueId()), 3L);
        }
    }
}
