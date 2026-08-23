package me.enchantments.mechanics;

import me.enchantments.main.Mechanic;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ланцюг душ.
 *
 * Убитий монстр не віддає душу гравцю — вона зривається з тіла і летить дугою до наступного
 * ворога поблизу, б'є з відкиданням, і з нього до наступного.
 *
 * Смерть від самого ланцюга нової дуги не породжує — жертва позначається в PDC,
 * інакше зачистка спавнера перетворилася б на нескінченну реакцію.
 *
 * Id лишився 'radiant_aura', щоб не переробляти капелюх в Oraxen.
 */
public final class RadiantAuraMechanic extends Mechanic {

    private static final NamespacedKey CHAINED = new NamespacedKey("enchantments", "soul_chained");
    private static final Color SOUL = Color.fromRGB(0x5FF4E0);

    private final Map<UUID, Long> readyAt = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "radiant_aura";
    }

    @Override
    public String getName() {
        return "Ланцюг душ";
    }

    @Override
    public String getDescription() {
        return "Убитий ворог відпускає душу — вона влітає в наступних, розкидаючи їх";
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.TOTEM_OF_UNDYING);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND, EnchantSlot.HEAD);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "max-jumps", 3,
            "jump-range", 10.0,
            "soul-speed", 0.9,
            "knockback", 0.9,
            "damage", 4.5,
            "damage-falloff", 0.8,
            "cooldown-ticks", 40
        );
    }

    @Override
    public double getEnchantingTableChance() {
        return 0.03;
    }

    @Override
    public int getWeight() {
        return 4;
    }

    @Override
    public double getChestLootChance() {
        return 0.05;
    }

    @Override
    public List<String> getLootChests() {
        return List.of(
            "minecraft:chests/end_city_treasure",
            "minecraft:chests/stronghold_library",
            "minecraft:chests/ancient_city",
            "minecraft:chests/village/village_temple"
        );
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of("CLERIC", Map.of("villager-level", 5, "min-level", 1, "max-level", 1, "price", 38));
    }

    @Override
    public int getAnvilBaseCost() {
        return 12;
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getEntity() instanceof Monster monster)) return;
        if (monster.getPersistentDataContainer().has(CHAINED, PersistentDataType.BYTE)) return;

        Player killer = monster.getKiller();
        if (killer == null || levelOnPlayer(killer) == 0) return;

        long now = monster.getWorld().getFullTime();
        Long ready = readyAt.get(killer.getUniqueId());
        if (ready != null && now < ready) return;
        readyAt.put(killer.getUniqueId(), now + getInt("cooldown-ticks"));

        Location origin = monster.getLocation().add(0, 0.9, 0);
        origin.getWorld().spawnParticle(Particle.FLASH, origin, 1, SOUL);
        origin.getWorld().playSound(origin, Sound.BLOCK_SOUL_SAND_BREAK, 1.0f, 1.6f);
        origin.getWorld().playSound(origin, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.6f, 1.8f);

        Set<UUID> visited = new HashSet<>();
        visited.add(monster.getUniqueId());

        new Chain(killer, origin, visited).runTaskTimer(getPlugin(), 3L, 1L);
    }

    /**
     * Душа летить сама: щотіка проходить soul-speed блоків уздовж дуги до обраної цілі
     * і б'є лише коли долітає. Ціль весь політ ведеться, тож від рухомої вона не відстає.
     */
    private final class Chain extends BukkitRunnable {

        private final Player killer;
        private final Set<UUID> visited;

        private Location from;
        private ItemDisplay orb;
        private LivingEntity target;
        private Location to;
        private double lift;
        private double step;
        private double flown;
        private double damage;
        private int jump;

        private Chain(Player killer, Location origin, Set<UUID> visited) {
            this.killer = killer;
            this.from = origin;
            this.visited = visited;
            this.damage = getDouble("damage");
            this.orb = spawnOrb(origin);
        }

        @Override
        public void run() {
            if (!killer.isOnline()) {
                fade();
                cancel();
                return;
            }
            if (target != null && (target.isDead() || !target.isValid())) target = null;
            if (target == null && !aim()) {
                fade();
                cancel();
                return;
            }

            to = target.getLocation().add(0, target.getHeight() * 0.6, 0);
            flown += step;

            if (flown < 1) {
                fly(point(flown));
                return;
            }

            strike(target, to);
            visited.add(target.getUniqueId());
            from = to;
            target = null;
            damage *= getDouble("damage-falloff");
            jump++;
        }

        private boolean aim() {
            if (jump >= getInt("max-jumps")) return false;

            target = nextTarget();
            if (target == null) return false;

            double distance = from.distance(target.getLocation());
            lift = 0.6 + distance * 0.12;
            step = getDouble("soul-speed") / Math.max(distance, 0.5);
            flown = 0;

            from.getWorld().playSound(from, Sound.ENTITY_EVOKER_CAST_SPELL, 0.8f, 1.5f);
            return true;
        }

        private LivingEntity nextTarget() {
            double range = getDouble("jump-range");
            LivingEntity best = null;
            double bestDistance = range;

            for (Entity nearby : from.getWorld().getNearbyEntities(from, range, range, range)) {
                if (!(nearby instanceof LivingEntity candidate)) continue;
                if (candidate.equals(killer) || candidate.isDead()) continue;
                if (!(candidate instanceof Monster)) continue;
                if (visited.contains(candidate.getUniqueId())) continue;

                double distance = candidate.getLocation().distance(from);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
            return best;
        }

        /** Точка на дузі: пряма від попередньої цілі до наступної плюс провисання синусом. */
        private Location point(double at) {
            Location point = from.clone().add(to.clone().subtract(from).toVector().multiply(at));
            return point.add(0, Math.sin(Math.PI * at) * lift, 0);
        }

        private void fly(Location at) {
            at.setYaw(0);
            at.setPitch(0);
            if (orb == null) orb = spawnOrb(at);
            else orb.teleport(at);
        }

        private ItemDisplay spawnOrb(Location at) {
            return at.getWorld().spawn(at, ItemDisplay.class, display -> {
                display.setItemStack(new ItemStack(Material.NETHER_STAR));
                display.setBillboard(Display.Billboard.CENTER);
                display.setBrightness(new Display.Brightness(15, 15));
                display.setGlowing(true);
                display.setGlowColorOverride(SOUL);
                display.setTransformation(new Transformation(
                        new Vector3f(), new Quaternionf(), new Vector3f(1.0f, 1.0f, 1.0f), new Quaternionf()));
                display.setTeleportDuration(1);
                display.setPersistent(false);
            });
        }

        private void strike(LivingEntity target, Location at) {
            target.getPersistentDataContainer().set(CHAINED, PersistentDataType.BYTE, (byte) 1);
            target.damage(damage, killer);

            Vector push = at.toVector().subtract(from.toVector()).normalize().multiply(getDouble("knockback"));
            target.setVelocity(push.setY(Math.max(0.4, push.getY())));

            burst();
            at.getWorld().spawnParticle(Particle.FLASH, at, 1, SOUL);
            at.getWorld().playSound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 1.3f + jump * 0.2f);
            at.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.4f);

            flash(at);
        }

        /** Удар: орб роздувається просто на цілі й гасне — сам собі спалах. */
        private void burst() {
            if (orb == null) return;
            ItemDisplay spent = orb;
            orb = null;

            spent.setInterpolationDelay(0);
            spent.setInterpolationDuration(3);
            spent.setTransformation(new Transformation(
                    new Vector3f(), new Quaternionf(), new Vector3f(2.4f, 2.4f, 2.4f), new Quaternionf()));
            Bukkit.getScheduler().runTaskLater(getPlugin(), spent::remove, 4L);
        }

        /** Ланцюг обірвався — душа стискається в точку й гасне. */
        private void fade() {
            from.getWorld().playSound(from, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.7f, 0.6f);
            if (orb == null) return;

            ItemDisplay spent = orb;
            orb = null;
            spent.setInterpolationDelay(0);
            spent.setInterpolationDuration(5);
            spent.setTransformation(new Transformation(
                    new Vector3f(), new Quaternionf(), new Vector3f(0.01f, 0.01f, 0.01f), new Quaternionf()));
            Bukkit.getScheduler().runTaskLater(getPlugin(), spent::remove, 6L);
        }
    }

    /** Кожен удар на мить підсвічує місце — світло клієнтське, у світі нічого не лишається. */
    private void flash(Location location) {
        Location at = location.getBlock().getLocation();
        if (!at.getBlock().getType().isAir()) return;

        BlockData original = at.getBlock().getBlockData();
        for (Player viewer : at.getWorld().getPlayers()) {
            if (viewer.getLocation().distanceSquared(at) < 4096) {
                viewer.sendBlockChange(at, Material.LIGHT.createBlockData("[level=15]"));
            }
        }

        Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
            for (Player viewer : at.getWorld().getPlayers()) {
                if (viewer.getLocation().distanceSquared(at) < 4096) {
                    viewer.sendBlockChange(at, original);
                }
            }
        }, 25L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        readyAt.remove(e.getPlayer().getUniqueId());
    }
}
