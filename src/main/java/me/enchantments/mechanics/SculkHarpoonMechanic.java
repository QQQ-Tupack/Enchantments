package me.enchantments.mechanics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import me.enchantments.main.Mechanic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SculkHarpoonMechanic extends Mechanic {

    private static final NamespacedKey HARPOON = new NamespacedKey("enchantments", "sculk_harpoon");
    private static final NamespacedKey FALLING = new NamespacedKey("enchantments", "sculk_falling");
    private static final NamespacedKey HOOKED = new NamespacedKey("enchantments", "sculk_hooked");
    private static final NamespacedKey HOOKED_UNTIL = new NamespacedKey("enchantments", "sculk_hooked_until");
    private static final NamespacedKey ANCHOR = new NamespacedKey("enchantments", "sculk_anchor");

    private static final long MARK = 6000L;
    private static final double STRAIN = 15.0;

    private static final double DRAG = 0.91;
    private static final double CAP = 3.9;
    private static final double PLAYER_FALL = 5.88;
    private static final double MOB_FALL = 3.92;

    private static final double RANGE = 25.0;
    private static final double RETURN = 1.8;

    private static final double LIP = 3.0;
    private static final double ARC = 12.0;
    private static final double KICK = 0.42;
    private static final double DIVE = 1.5;

    @Override public String getId() { return "sculk_harpoon"; }
    @Override public String getName() { return "Гарпун глибин"; }
    @Override public String getDescription() { return "Тризуб тягне тебе до блока, а живу ціль приковує до нього"; }
    @Override public int getMaxLevel() { return 3; }
    @Override public List<Material> getSupportedItems() { return List.of(Material.TRIDENT); }
    @Override public List<EnchantSlot> getSlots() { return List.of(EnchantSlot.MAINHAND); }

    @Override public boolean isEnchantingTableAllowed() { return false; }
    @Override public boolean isChestLootAllowed() { return false; }
    @Override public boolean isVillagerTradeAllowed() { return false; }
    @Override public int getAnvilBaseCost() { return 30; }
    @Override protected boolean grindstoneRemovableDefault() { return false; }

    @Override
    public List<String> getExclusiveWith() {
        return List.of("minecraft:riptide");
    }
    
    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "cooldown-ticks", 60,
            "chain-seconds", 5,
            "chain-damage", 8.0,
            "rope-ticks", 10,
            "safe-landing-seconds", 4
        );
    }

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

        if (!marked(player)) new Line(player, trident, getInt("rope-ticks")).runTaskTimer(getPlugin(), 1L, 1L);
    }

    private static boolean marked(Player player) {
        Long until = player.getPersistentDataContainer().get(HOOKED_UNTIL, PersistentDataType.LONG);
        return until != null && until >= System.currentTimeMillis();
    }

    @EventHandler
    public void onHit(ProjectileHitEvent e) {
        if (!isMechanicEnabled()) return;

        Integer level = e.getEntity().getPersistentDataContainer().get(HARPOON, PersistentDataType.INTEGER);
        if (level == null || !(e.getEntity().getShooter() instanceof Player player)) return;

        e.getEntity().getPersistentDataContainer().remove(HARPOON);

        if (e.getHitEntity() instanceof LivingEntity target && !target.equals(player)) {
            player.getPersistentDataContainer().set(HOOKED, PersistentDataType.STRING,
                    target.getUniqueId().toString());
            player.getPersistentDataContainer().set(HOOKED_UNTIL, PersistentDataType.LONG,
                    System.currentTimeMillis() + MARK);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.6f);

            new Chain(player, target, 6 - level, getDouble("chain-damage"), getInt("chain-seconds") * 20)
                    .runTaskTimer(getPlugin(), 1L, 1L);
            return;
        }
        if (e.getHitBlock() == null) return;

        Block block = e.getHitBlock();
        BlockFace face = e.getHitBlockFace() == null ? BlockFace.UP : e.getHitBlockFace();

        if (marked(player)) {
            player.getPersistentDataContainer().set(ANCHOR, PersistentDataType.INTEGER_ARRAY,
                    new int[]{block.getX(), block.getY(), block.getZ(), face.ordinal()});
            return;
        }

        grapple(player, goal(block, face), face == BlockFace.DOWN ? 0 : LIP);
    }

    private static Location goal(Block block, BlockFace face) {
        return block.getRelative(face).getLocation().add(0.5, 0, 0.5);
    }

    private static Vector launch(Location from, Location goal, double clear, boolean ground, double fall) {
        double dx = goal.getX() - from.getX();
        double dz = goal.getZ() - from.getZ();
        double dy = goal.getY() - from.getY();
        double away = Math.hypot(dx, dz);
        double grip = ground ? 0.546 : DRAG;
        double ceiling = Math.max(dy + clear, 0) + ARC;
        boolean below = dy + clear < 0;

        int ticks = 1;
        while (ticks < 45 && peak(ticks, fall) < dy + clear) ticks++;
        while (!below && ticks < 20 && away / spread(grip, ticks) > CAP && peak(ticks, fall) < ceiling) ticks++;

        double up = below ? -DIVE : Math.min(top(ticks, fall), CAP);
        if (!below && ground) up = Math.max(up, KICK);
        double reach = spread(grip, below ? dive(up, dy, fall) : ticks);

        return new Vector(
                Math.clamp(dx / reach, -CAP, CAP),
                up,
                Math.clamp(dz / reach, -CAP, CAP));
    }

    private static int dive(double up, double dy, double fall) {
        double speed = up;
        double height = 0;
        int ticks = 0;
        while (ticks < 100 && height > dy) {
            height += speed;
            speed = (speed - fall / 49) * 0.98;
            ticks++;
        }
        return ticks;
    }

    @SuppressWarnings("deprecation")
	private void grapple(Player player, Location goal, double clear) {
        player.setVelocity(launch(player.getLocation(), goal, clear, player.isOnGround(), PLAYER_FALL));
        player.setFallDistance(0);
        player.getPersistentDataContainer().set(FALLING, PersistentDataType.LONG,
                System.currentTimeMillis() + getInt("safe-landing-seconds") * 1000L);

        player.getWorld().playSound(goal, Sound.BLOCK_CHAIN_PLACE, 1f, 0.7f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.8f, 1.3f);
    }

    private static double top(int ticks, double fall) {
        return fall * (Math.pow(0.98, -ticks) - 1);
    }

    private static double peak(int ticks, double fall) {
        double rise = (1 - Math.pow(0.98, ticks)) / 0.02;
        return top(ticks, fall) * rise - fall * (ticks - rise);
    }

    private static double spread(double grip, int ticks) {
        return 1 + grip * (1 - Math.pow(DRAG, ticks)) / (1 - DRAG);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLanding(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(e.getEntity() instanceof Player player)) return;

        Long until = player.getPersistentDataContainer().get(FALLING, PersistentDataType.LONG);
        if (until == null) return;

        player.getPersistentDataContainer().remove(FALLING);
        if (until >= System.currentTimeMillis()) e.setCancelled(true);
    }

    private static Location hand(Player player) {
        return player.getEyeLocation().subtract(0, 0.3, 0);
    }

    private static final class Line extends BukkitRunnable {

        private final Player player;
        private final Trident trident;
        private final Rope rope;
        private int fade;
        private boolean returning;

        private Line(Player player, Trident trident, int fade) {
            this.player = player;
            this.trident = trident;
            this.rope = new Rope(player.getWorld());
            this.fade = fade;
        }

        @Override
        public void run() {
            boolean flying = trident.getPersistentDataContainer().has(HARPOON, PersistentDataType.INTEGER);
            if (!player.isOnline() || !trident.isValid()
                    || (!flying && !returning && (!trident.isInBlock() || fade-- <= 0))) {
                rope.remove();
                cancel();
                return;
            }

            Location hand = hand(player);
            Vector home = hand.toVector().subtract(trident.getLocation().toVector());
            double distance = home.length();

            if (flying && distance > RANGE) {
                returning = true;
                trident.getPersistentDataContainer().remove(HARPOON);
                trident.setGravity(false);
                trident.setDamage(0);
                player.getWorld().playSound(trident.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1f, 0.8f);
            }

            if (returning) {
                if (distance < 2.5) {
                    player.getInventory().addItem(trident.getItemStack())
                            .values().forEach(left -> player.getWorld().dropItem(hand, left));
                    trident.remove();
                    rope.remove();
                    cancel();
                    return;
                }
                trident.setVelocity(home.multiply(RETURN / distance));
            }

            rope.follow(hand, trident.getLocation(), 0.4);
        }
    }

    private static final class Chain extends BukkitRunnable {

        private final Player player;
        private final LivingEntity target;
        private final Rope rope;
        private final double length;
        private final double damage;
        private final int hold;
        private Location anchor;
        private int left = (int) (MARK / 50);
        private boolean caught;
        private double wear;

        private Chain(Player player, LivingEntity target, double length, double damage, int hold) {
            this.player = player;
            this.target = target;
            this.rope = new Rope(player.getWorld());
            this.length = length;
            this.damage = damage;
            this.hold = hold;
        }

        @Override
        public void run() {
            if (!target.isValid() || !player.isOnline() || !player.getWorld().equals(target.getWorld())) {
                snap(false);
                return;
            }
            if (anchor == null && !hooked()) return;

            Location held = target.getEyeLocation();
            Vector back = anchor.toVector().subtract(held.toVector());
            double distance = back.length();

            if (left-- <= 0) {
                target.damage(damage);
                target.getWorld().playSound(held, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.7f);
                snap(false);
                return;
            }

            rope.follow(anchor, held, Math.max(0, length - distance) * 0.25);
            if (distance <= length) {
                caught = true;
                return;
            }
            if (!caught && !target.isOnGround()) return;

            Vector pull = back.multiply(1 / distance);
            Vector flail = target.getVelocity();
            flail.subtract(pull.clone().multiply(flail.dot(pull)));
            target.setVelocity(flail.add(pull.multiply(Math.min(distance - length, 1.0))));

            if (caught) wear += distance - length;
            if (wear >= STRAIN) snap(true);
        }

        private boolean hooked() {
            String mark = player.getPersistentDataContainer().get(HOOKED, PersistentDataType.STRING);
            if (left-- <= 0 || !target.getUniqueId().toString().equals(mark)) {
                snap(false);
                return false;
            }

            int[] spot = player.getPersistentDataContainer().get(ANCHOR, PersistentDataType.INTEGER_ARRAY);
            if (spot == null) {
                rope.follow(hand(player), target.getEyeLocation(), 0.3);
                return false;
            }

            anchor = new Location(player.getWorld(), spot[0] + 0.5, spot[1] + 0.5, spot[2] + 0.5)
                    .add(BlockFace.values()[spot[3]].getDirection().multiply(0.5));
            target.setVelocity(launch(target.getLocation(), anchor, 0, target.isOnGround(),
                    target instanceof Player ? PLAYER_FALL : MOB_FALL));
            target.setFallDistance(0);
            player.getPersistentDataContainer().remove(ANCHOR);
            player.getPersistentDataContainer().remove(HOOKED);
            player.getPersistentDataContainer().remove(HOOKED_UNTIL);
            left = hold;
            player.getWorld().playSound(anchor, Sound.BLOCK_CHAIN_PLACE, 1f, 0.5f);
            return true;
        }

        private void snap(boolean broken) {
            rope.remove();
            cancel();
            if (anchor == null && player.isOnline() && target.getUniqueId().toString()
                    .equals(player.getPersistentDataContainer().get(HOOKED, PersistentDataType.STRING))) {
                player.getPersistentDataContainer().remove(HOOKED);
                player.getPersistentDataContainer().remove(HOOKED_UNTIL);
            }
            if (broken) target.getWorld().playSound(target.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1f, 1.2f);
        }
    }

    private static final class Rope {

        private static final double SEGMENT = 1.0;
        private static final int MAX_LINKS = 24;
        private static final int SMOOTH = 2;

        private final World world;
        private final List<BlockDisplay> links = new ArrayList<>();

        private Rope(World world) {
            this.world = world;
        }

        private void follow(Location from, Location to, double sag) {
            if (!world.equals(from.getWorld()) || !world.equals(to.getWorld())) {
                remove();
                return;
            }

            int count = (int) Math.max(1, Math.min(MAX_LINKS, from.distance(to) / SEGMENT));
            while (links.size() > count) links.remove(links.size() - 1).remove();

            Vector step = to.toVector().subtract(from.toVector()).multiply(1.0 / count);
            Location previous = bend(from, step, 0, count, sag);

            for (int i = 0; i < count; i++) {
                Location next = bend(from, step, i + 1, count, sag);
                if (links.size() == i) links.add(spawn(previous));
                shape(links.get(i), previous, next.toVector().subtract(previous.toVector()));
                previous = next;
            }
        }

        private static Location bend(Location from, Vector step, int index, int count, double sag) {
            Location at = from.clone().add(step.clone().multiply(index));
            if (sag > 0) at.subtract(0, sag * Math.sin(Math.PI * index / count), 0);
            at.setYaw(0);
            at.setPitch(0);
            return at;
        }

        private BlockDisplay spawn(Location at) {
            return world.spawn(at, BlockDisplay.class, display -> {
                display.setBlock(Material.IRON_CHAIN.createBlockData());
                display.setBrightness(new Display.Brightness(15, 15));
                display.setInterpolationDuration(SMOOTH);
                display.setTeleportDuration(SMOOTH);
                display.setPersistent(false);
            });
        }

        private static void shape(BlockDisplay link, Location at, Vector span) {
            double length = span.length();
            if (length < 1e-4) return;

            Quaternionf turn = new Quaternionf()
                    .rotateY((float) Math.atan2(span.getX(), span.getZ()))
                    .rotateX((float) Math.acos(Math.max(-1, Math.min(1, span.getY() / length))));

            link.teleport(at);
            link.setInterpolationDelay(0);
            link.setTransformation(new Transformation(
                    turn.transform(new Vector3f(-0.5f, 0, -0.5f)),
                    turn,
                    new Vector3f(1, (float) length, 1),
                    new Quaternionf()));
        }

        private void remove() {
            links.forEach(BlockDisplay::remove);
            links.clear();
        }
    }
}