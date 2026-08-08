package me.enchantments.mechanics;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ланцюг душ.
 *
 * Убитий монстр не віддає душу гравцю — вона зривається з тіла і світловою дугою
 * б'є в наступного ворога поблизу, звідти в наступного, і так до чотирьох цілей.
 * Нежить від дотику душі спалахує.
 *
 * Смерть від самого ланцюга нової дуги не породжує — жертва позначається в PDC,
 * інакше зачистка спавнера перетворилася б на нескінченну реакцію.
 *
 * Id лишився 'radiant_aura', щоб не переробляти капелюх в Oraxen.
 */
public final class RadiantAuraMechanic extends Mechanic {

    private static final NamespacedKey CHAINED = new NamespacedKey("enchantments", "soul_chained");

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
        return "Убитий ворог відпускає душу — вона стрибає від цілі до цілі, спалюючи нежить";
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
            "max-jumps", 4,
            "jump-range", 7.0,
            "damage", 4.5,
            "damage-falloff", 0.8,
            "undead-burn-seconds", 4,
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

    // ==========================================
    //   ЗАПУСК ЛАНЦЮГА
    // ==========================================

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getEntity() instanceof Monster monster)) return;

        // смерть від самої душі нового ланцюга не починає
        if (monster.getPersistentDataContainer().has(CHAINED, PersistentDataType.BYTE)) return;

        Player killer = monster.getKiller();
        if (killer == null || levelOnPlayer(killer) == 0) return;

        long now = monster.getWorld().getFullTime();
        Long ready = readyAt.get(killer.getUniqueId());
        if (ready != null && now < ready) return;
        readyAt.put(killer.getUniqueId(), now + getInt("cooldown-ticks"));

        Location origin = monster.getLocation().add(0, 0.9, 0);
        origin.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, origin, 20, 0.2, 0.2, 0.2, 0.05);
        origin.getWorld().spawnParticle(Particle.FLASH, origin, 1);
        origin.getWorld().playSound(origin, Sound.BLOCK_SOUL_SAND_BREAK, 1.0f, 1.6f);

        Set<UUID> visited = new HashSet<>();
        visited.add(monster.getUniqueId());

        new Chain(killer, origin, visited).runTaskTimer(getPlugin(), 3L, 4L);
    }

    /** Душа шукає найближчого ворога попереду себе, б'є і летить далі — по одному стрибку раз на 4 тіки. */
    private final class Chain extends BukkitRunnable {

        private final Player killer;
        private final Set<UUID> visited;

        private Location from;
        private double damage;
        private int jump;

        private Chain(Player killer, Location origin, Set<UUID> visited) {
            this.killer = killer;
            this.from = origin;
            this.visited = visited;
            this.damage = getDouble("damage");
        }

        @Override
        public void run() {
            if (!killer.isOnline() || jump >= getInt("max-jumps")) {
                fade();
                cancel();
                return;
            }

            LivingEntity target = nextTarget();
            if (target == null) {
                fade();
                cancel();
                return;
            }

            Location to = target.getLocation().add(0, target.getHeight() * 0.6, 0);
            drawArc(from, to);
            strike(target, to);

            visited.add(target.getUniqueId());
            from = to;
            damage *= getDouble("damage-falloff");
            jump++;
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

        private void strike(LivingEntity target, Location at) {
            target.getPersistentDataContainer().set(CHAINED, PersistentDataType.BYTE, (byte) 1);
            target.damage(damage, killer);

            if (target.getCategory() == EntityCategory.UNDEAD) {
                target.setFireTicks(Math.max(target.getFireTicks(), getInt("undead-burn-seconds") * 20));
            }

            at.getWorld().spawnParticle(Particle.FLASH, at, 1);
            at.getWorld().spawnParticle(Particle.END_ROD, at, 18, 0.2, 0.2, 0.2, 0.06);
            at.getWorld().spawnParticle(Particle.SOUL, at, 8, 0.25, 0.25, 0.25, 0.02);
            at.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.8f + jump * 0.25f);
            at.getWorld().playSound(at, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.6f, 1.5f);

            flash(at);
        }

        /** Дуга з провисанням — душа не летить по прямій, а перекидається через повітря. */
        private void drawArc(Location start, Location end) {
            Vector delta = end.toVector().subtract(start.toVector());
            int steps = Math.max(8, (int) (delta.length() * 4));
            double lift = 0.6 + delta.length() * 0.12;

            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                Location point = start.clone().add(delta.clone().multiply(t));
                point.add(0, Math.sin(Math.PI * t) * lift, 0);

                point.getWorld().spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
                if (i % 3 == 0) {
                    point.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, point, 1, 0.02, 0.02, 0.02, 0);
                }
            }
            start.getWorld().playSound(start, Sound.ENTITY_BREEZE_SHOOT, 0.5f, 1.8f);
        }

        /** Ланцюг обірвався — душа розсипається на місці останнього удару. */
        private void fade() {
            from.getWorld().spawnParticle(Particle.SOUL, from, 25, 0.3, 0.4, 0.3, 0.05);
            from.getWorld().playSound(from, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.7f, 0.6f);
        }
    }

    // ==========================================
    //   КЛІЄНТСЬКИЙ СПАЛАХ
    // ==========================================

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