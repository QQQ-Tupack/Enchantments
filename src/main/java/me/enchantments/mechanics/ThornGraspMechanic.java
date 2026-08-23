package me.enchantments.mechanics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.enchantments.main.Mechanic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ThornGraspMechanic extends Mechanic {

    /** Тіло ліани й «жива» верхівка — верхівка завжди на найвищому намальованому блоці. */
    private static final Material VINE_BODY = Material.TWISTING_VINES_PLANT;
    private static final Material VINE_TIP = Material.TWISTING_VINES;

    private static final int MAX_HEIGHT = 3;
    private static final int GROW_STEP = 2;
    private static final double GRASP_RADIUS = 2.0;
    private static final int VIEW_DISTANCE_SQUARED = 64 * 64;

    @Override public String getId() { return "thorn_grasp"; }
    @Override public String getName() { return "Лози"; }
    @Override public String getDescription() { return "Удар по власнику обплітає нападника колючими лозами й тримає його на місці."; }
    @Override public int getMaxLevel() { return 3; }
    @Override public List<Material> getSupportedItems() { return List.of(Material.TOTEM_OF_UNDYING); }
    @Override public List<EnchantSlot> getSlots() { return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND, EnchantSlot.HEAD); }

    @Override public double getEnchantingTableChance() { return 0.10; }
    @Override public List<String> getLootChests() { return List.of("minecraft:chests/jungle_temple"); }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of("FARMER", Map.of("level", 3, "min-emeralds", 10, "max-emeralds", 22));
    }

    @Override public int getAnvilBaseCost() { return 6; }
    @Override public int getAnvilCostPerLevel() { return 3; }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "cooldown-seconds", 15,
            "hold-seconds", 3,
            "hold-seconds-per-level", 0.5
        );
    }

    @Override
    protected void onDamaged(EntityDamageEvent event, Player player, int level) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) return;
        if (!(byEntity.getDamager() instanceof LivingEntity attacker) || attacker.equals(player)) return;

        ItemStack item = wornItem(player);
        if (!ready(player, item)) return;
        cooldown(player, item, getInt("cooldown-seconds") * 20);

        int holdTicks = (int) ((getInt("hold-seconds") + getDouble("hold-seconds-per-level") * (level - 1)) * 20);
        Location at = attacker.getLocation();
        List<Player> viewers = viewersAround(at);
        Set<Block> taken = new HashSet<>();

        soundAt(at, Sound.BLOCK_MOSS_BREAK, 1.0f, 0.7f);
        soundAt(at, Sound.BLOCK_BIG_DRIPLEAF_TILT_DOWN, 0.8f, 0.6f);

        grasp(player, attacker, viewers, taken, holdTicks);

        // Кільце ловить усю зграю, а не лише того, хто встиг ударити.
        for (Entity entity : attacker.getWorld().getNearbyEntities(at, GRASP_RADIUS, GRASP_RADIUS, GRASP_RADIUS)) {
            if (!(entity instanceof Mob mob) || mob.equals(attacker)) continue;
            if (mob.getLocation().distanceSquared(at) > GRASP_RADIUS * GRASP_RADIUS) continue;
            if (mob instanceof Tameable tameable && player.equals(tameable.getOwner())) continue;
            grasp(player, mob, viewers, taken, holdTicks);
        }
    }

    private ItemStack wornItem(Player player) {
        for (EnchantSlot slot : getSlots()) {
            ItemStack item = switch (slot) {
                case MAINHAND -> player.getInventory().getItemInMainHand();
                case OFFHAND -> player.getInventory().getItemInOffHand();
                case HEAD -> player.getInventory().getHelmet();
                case CHEST -> player.getInventory().getChestplate();
                case LEGS -> player.getInventory().getLeggings();
                case FEET -> player.getInventory().getBoots();
                case BODY -> null;
            };
            if (levelOf(item) > 0) return item;
        }
        return null;
    }

    private void grasp(Player owner, LivingEntity victim, List<Player> viewers, Set<Block> taken, int ticks) {
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 5, false, true));
        victim.setVelocity(new Vector(0, 0, 0));
        entangle(owner, victim, viewers, taken, ticks);
    }

    /** Одна ліана на одну ціль: росте по ній самій, тримається, потім опадає. */
    private void entangle(Player owner, LivingEntity victim, List<Player> viewers, Set<Block> taken, int ticks) {
        List<Block> column = buildColumn(owner, victim, taken);
        double height = Math.max(1.0, victim.getHeight());
        int growEnd = Math.max(1, column.size()) * GROW_STEP;
        int retractStart = growEnd + ticks;

        new BukkitRunnable() {
            int elapsed;
            int drawn;

            @Override
            public void run() {
                // Ціль загинула — не чекаємо решту часу, одразу розплітаємось.
                if (!victim.isValid() && elapsed < retractStart) elapsed = retractStart;

                if (elapsed < growEnd) {
                    if (elapsed % GROW_STEP == 0 && drawn < column.size()) {
                        if (drawn > 0) send(viewers, column.get(drawn - 1), VINE_BODY);
                        send(viewers, column.get(drawn), VINE_TIP);
                        growEffects(victim.getLocation(), drawn);
                        drawn++;
                    }
                    if (elapsed % 2 == 0) helix(victim, height, elapsed);
                    elapsed++;
                    return;
                }

                if (elapsed < retractStart) {
                    if (elapsed % 2 == 0) helix(victim, height, elapsed);
                    if (elapsed % 12 == 0) soundAt(victim.getLocation(), Sound.BLOCK_WEEPING_VINES_HIT, 0.3f, 0.6f);
                    elapsed++;
                    return;
                }

                if (drawn > 0) {
                    if (elapsed % GROW_STEP == 0) {
                        drawn--;
                        Block block = column.get(drawn);
                        restore(viewers, block);
                        if (drawn > 0) send(viewers, column.get(drawn - 1), VINE_TIP);
                        particles(block.getLocation().add(0.5, 0.5, 0.5),
                                Particle.SPORE_BLOSSOM_AIR, 6, 0.3, 0.2, 0.3, 0.0);
                    }
                    elapsed++;
                    return;
                }

                cancel();
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    /** Колона по самій цілі — знизу вгору на її зріст. Перешкода або власник обривають ріст. */
    private List<Block> buildColumn(Player owner, LivingEntity victim, Set<Block> taken) {
        Block ownerFeet = owner.getLocation().getBlock();
        Block ownerHead = ownerFeet.getRelative(BlockFace.UP);
        int height = Math.min(MAX_HEIGHT, Math.max(1, (int) Math.ceil(victim.getHeight())));

        List<Block> column = new ArrayList<>();
        Block feet = victim.getLocation().getBlock();
        for (int y = 0; y < height; y++) {
            Block block = feet.getRelative(0, y, 0);
            if (!block.getType().isAir()) break;
            if (block.equals(ownerFeet) || block.equals(ownerHead)) break;
            // Сусід, що стоїть у тому ж блоці, вже обплетений — не малюємо двічі.
            if (!taken.add(block)) break;
            column.add(block);
        }
        return column;
    }

    private List<Player> viewersAround(Location at) {
        List<Player> viewers = new ArrayList<>();
        for (Player nearby : at.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(at) <= VIEW_DISTANCE_SQUARED) viewers.add(nearby);
        }
        return viewers;
    }

    private void send(List<Player> viewers, Block block, Material material) {
        BlockData data = material.createBlockData();
        for (Player viewer : viewers) {
            if (viewer.isOnline()) viewer.sendBlockChange(block.getLocation(), data);
        }
    }

    private void restore(List<Player> viewers, Block block) {
        for (Player viewer : viewers) {
            if (viewer.isOnline()) viewer.sendBlockChange(block.getLocation(), block.getBlockData());
        }
    }

    /** Два вусики по спіралі довкола тіла — саме вони читаються як «обплело». */
    private void helix(LivingEntity victim, double height, int tick) {
        Location base = victim.getLocation();
        double radius = Math.max(0.35, victim.getWidth() * 0.6);

        for (int i = 0; i < 4; i++) {
            double part = i / 4.0;
            double y = 0.15 + height * part;
            double angle = 2 * Math.PI * (part * 1.5 + tick * 0.08);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            particles(base.clone().add(x, y, z), Particle.SPORE_BLOSSOM_AIR, 1, 0, 0, 0, 0);
            particles(base.clone().add(-x, y, -z), Particle.HAPPY_VILLAGER, 1, 0, 0, 0, 0);
        }
    }

    /** Кожен блок ліани — на пів тону вище, тому ріст чути як висхідну гаму. */
    private void growEffects(Location at, int layer) {
        Location point = at.clone().add(0, layer + 0.5, 0);
        soundAt(point, Sound.BLOCK_WEEPING_VINES_PLACE, 0.9f, 0.7f + layer * 0.15f);
        particles(point, Particle.SPORE_BLOSSOM_AIR, 10, 0.4, 0.3, 0.4, 0.0);
        particles(point, Particle.HAPPY_VILLAGER, 6, 0.4, 0.3, 0.4, 0.0);
    }
}