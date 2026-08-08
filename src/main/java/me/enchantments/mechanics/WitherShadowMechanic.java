package me.enchantments.mechanics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Тінь згасання. Удар мечем зриває з ворога тінь — і вона лишається на землі.
 *
 * Далі тінь повзе за жертвою сама: не по прямій, а по поверхні — обходить кути, піднімається
 * на блок, стікає в яму, але не заходить на воду й не долає стіну вище одного блока.
 * Наздогнала — руки з-під землі тримають жертву на місці, потім слід зривається іссушенням.
 *
 * Ландшафт не змінюється взагалі: чорний слід — це sendBlockChange, який сам згасає.
 */
public final class WitherShadowMechanic extends Mechanic {

    /** Одна тінь на ціль: інакше юрба гравців застеляє арену соул-соілом. */
    private static final Set<UUID> HUNTED = new HashSet<>();

    /** Вісім напрямків — діагоналі й дають обхід кутів. */
    private static final int[][] AROUND = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    @Override
    public String getId() {
        return "wither_shadow";
    }

    @Override
    public String getName() {
        return "Тінь згасання";
    }

    @Override
    public String getDescription() {
        return "Удар зриває з ворога тінь, що повзе слідом по землі й хапає його з-під ніг";
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
                       Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.MAINHAND);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "cooldown-ticks", 160,
            "step-ticks", 4,          // 5 блоків/с — від спринту втекти можна, від бігу вже ні
            "max-steps", 70,
            "lose-range", 26.0,
            "grab-ticks", 30,
            "grab-radius", 1.4,
            "damage", 9.0,
            "wither-seconds", 8,
            "trail-fade-ticks", 45
        );
    }

    @Override public boolean isEnchantingTableAllowed() { return false; }
    @Override public boolean isChestLootAllowed() { return false; }
    @Override public boolean isVillagerTradeAllowed() { return false; }
    @Override public int getAnvilBaseCost() { return 28; }

    // ==========================================
    //   ЗРИВ ТІНІ
    // ==========================================

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!isMechanicEnabled()) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (!(e.getDamager() instanceof Player player)) return;
        if (!(e.getEntity() instanceof LivingEntity prey)) return;

        ItemStack sword = player.getInventory().getItemInMainHand();
        int level = levelOf(sword);
        if (level == 0) return;
        if (player.hasCooldown(sword.getType())) return;

        Block start = surfaceAt(prey.getLocation(), prey.getLocation().getBlockY());
        if (start == null) return; // жертва у воді або в повітрі — тіні нема на що лягти
        if (!HUNTED.add(prey.getUniqueId())) return;

        player.setCooldown(sword.getType(), Math.max(60, getInt("cooldown-ticks") - (level - 1) * 30));

        World world = prey.getWorld();
        world.playSound(prey.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.7f, 0.5f);
        world.playSound(prey.getLocation(), Sound.BLOCK_SOUL_SAND_BREAK, 0.9f, 0.7f);
        world.spawnParticle(Particle.SMOKE, prey.getLocation().add(0, 0.2, 0), 30, 0.3, 0.05, 0.3, 0.02);
        world.spawnParticle(Particle.SOUL, prey.getLocation().add(0, 0.2, 0), 8, 0.3, 0.05, 0.3, 0.02);

        paint(start);
        new Shadow(player, prey, level, start).runTaskTimer(getPlugin(), 10L, 1L); // піврядка форе жертві
    }

    // ==========================================
    //   ПЕРЕСЛІДУВАННЯ
    // ==========================================

    private final class Shadow extends BukkitRunnable {

        private final Player caster;
        private final LivingEntity prey;
        private final int level;

        private Block at;
        private int ticks;
        private int steps;
        private boolean holding;
        private int held;

        private Shadow(Player caster, LivingEntity prey, int level, Block at) {
            this.caster = caster;
            this.prey = prey;
            this.level = level;
            this.at = at;
        }

        @Override
        public void run() {
            if (!caster.isOnline() || !prey.isValid() || prey.isDead()
                    || !prey.getWorld().equals(at.getWorld())) {
                dissipate();
                return;
            }

            if (holding) {
                hold();
                if (++held >= getInt("grab-ticks")) burst();
                return;
            }

            if (prey.getLocation().distance(at.getLocation()) > getDouble("lose-range")) {
                dissipate();
                return;
            }

            trail();

            if (reached()) {
                grab();
                return;
            }

            if (++ticks % getInt("step-ticks") != 0) return;
            if (++steps > getInt("max-steps") + (level - 1) * 20) {
                dissipate();
                return;
            }

            Block next = nextStep();
            if (next == null) { // загнали у воду чи в стіну — тінь розсіюється
                dissipate();
                return;
            }
            at = next;
            paint(at);
        }

        /** Хапає тільки з-під ніг: якщо жертва вилізла на два блоки вище — тінь ще повзе, але дістати не може. */
        private boolean reached() {
            Location center = at.getLocation().add(0.5, 1, 0.5);
            double radius = getDouble("grab-radius");
            double dx = prey.getLocation().getX() - center.getX();
            double dz = prey.getLocation().getZ() - center.getZ();
            double dy = prey.getLocation().getY() - center.getY();
            return dx * dx + dz * dz <= radius * radius && Math.abs(dy) <= 2.0;
        }

        /** Крок туди, де ближче до цілі: підйом на блок, спуск на два, вода — стіна. */
        private Block nextStep() {
            Location goal = prey.getLocation();
            double best = distance(at, goal);
            Block chosen = null;

            for (int[] offset : AROUND) {
                Location side = at.getLocation().add(offset[0], 0, offset[1]);
                Block candidate = surfaceAt(side, at.getY());
                if (candidate == null) continue;

                double distance = distance(candidate, goal);
                if (distance < best) {
                    best = distance;
                    chosen = candidate;
                }
            }
            return chosen;
        }

        private double distance(Block block, Location goal) {
            double dx = block.getX() + 0.5 - goal.getX();
            double dz = block.getZ() + 0.5 - goal.getZ();
            return dx * dx + dz * dz;
        }

        private void trail() {
            Location top = at.getLocation().add(0.5, 1.02, 0.5);
            top.getWorld().spawnParticle(Particle.SOUL, top, 1, 0.2, 0, 0.2, 0.008);
            top.getWorld().spawnParticle(Particle.SMOKE, top, 2, 0.25, 0.02, 0.25, 0.004);
        }

        // ==========================================
        //   ЗАХВАТ
        // ==========================================

        private void grab() {
            holding = true;
            World world = prey.getWorld();
            Location feet = prey.getLocation();

            world.playSound(feet, Sound.BLOCK_SOUL_SAND_BREAK, 1.1f, 0.5f);
            world.playSound(feet, Sound.ENTITY_WITHER_SHOOT, 0.6f, 0.5f);

            for (int i = 0; i < 12; i++) { // руки з-під землі
                double angle = Math.PI * 2 * i / 12;
                world.spawnParticle(Particle.SOUL,
                        feet.clone().add(Math.cos(angle) * 0.6, 0.05, Math.sin(angle) * 0.6),
                        3, 0.02, 0.3, 0.02, 0.07);
            }
        }

        private void hold() {
            prey.setVelocity(new Vector(0, -0.08, 0));
            prey.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 5, true, false, false));
            prey.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 10, 128, true, false, false));

            Location feet = prey.getLocation();
            feet.getWorld().spawnParticle(Particle.SOUL, feet, 3, 0.35, 0.05, 0.35, 0.04);
            feet.getWorld().spawnParticle(Particle.SMOKE, feet.clone().add(0, 0.6, 0), 2, 0.3, 0.3, 0.3, 0.01);
        }

        private void burst() {
            Location center = prey.getLocation().add(0, 0.6, 0);
            World world = center.getWorld();

            prey.damage(getDouble("damage") * (1 + (level - 1) * 0.2), caster);
            prey.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,
                    (getInt("wither-seconds") + (level - 1) * 2) * 20, level - 1, true, true, true));

            world.playSound(center, Sound.ENTITY_WITHER_HURT, 1.1f, 0.6f);
            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.5f);
            world.spawnParticle(Particle.SOUL, center, 50, 0.5, 0.5, 0.5, 0.25);
            world.spawnParticle(Particle.LARGE_SMOKE, center, 25, 0.4, 0.4, 0.4, 0.05);

            dissipate();
        }

        private void dissipate() {
            cancel();
            HUNTED.remove(prey.getUniqueId());
            if (at != null) {
                at.getWorld().spawnParticle(Particle.SMOKE,
                        at.getLocation().add(0.5, 1, 0.5), 12, 0.3, 0.05, 0.3, 0.02);
            }
        }
    }

    // ==========================================
    //   СЛІД
    // ==========================================

    /** Поверхня під точкою: підйом максимум на блок, спуск на два, під водою поверхні нема. */
    private Block surfaceAt(Location at, int fromY) {
        World world = at.getWorld();
        for (int dy = 1; dy >= -2; dy--) {
            Block candidate = world.getBlockAt(at.getBlockX(), fromY + dy, at.getBlockZ());
            if (!candidate.getType().isSolid()) continue;

            Block above = candidate.getRelative(0, 1, 0);
            if (above.getType().isSolid() || above.isLiquid()) continue;
            return candidate;
        }
        return null;
    }

    /** Чорний слід малюється тільки клієнту й сам згасає — світ лишається цілим. */
    private void paint(Block block) {
        BlockData original = block.getBlockData();
        BlockData shade = Material.SOUL_SOIL.createBlockData();

        for (Player viewer : block.getWorld().getPlayers()) {
            viewer.sendBlockChange(block.getLocation(), shade);
        }
        getPlugin().getServer().getScheduler().runTaskLater(getPlugin(), () -> {
            for (Player viewer : block.getWorld().getPlayers()) {
                viewer.sendBlockChange(block.getLocation(), original);
            }
        }, getInt("trail-fade-ticks"));

        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_SOUL_SOIL_STEP, 0.5f, 0.6f);
    }
}
