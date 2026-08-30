package me.enchantments.mechanics;

import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.util.Vector;

import io.papermc.paper.datacomponent.DataComponentTypes;

import me.enchantments.main.Enchantments;
import me.enchantments.main.Mechanic;

public final class GrapplingHookMechanic extends Mechanic {

    private final Set<UUID> flying = new HashSet<>();
    private final Map<UUID, Integer> mainHandBusy = new HashMap<>();

    private static final double DRAG = 0.91;
    private static final double CAP = 3.9;
    private static final double FALL = 5.88;

    private static final double LIP = 1.5;
    private static final double ARC = 6.0;
    private static final double KICK = 0.42;
    private static final double DIVE = 1.0;

    @Override public String getId() { return "grappling_hook"; }
    @Override public String getName() { return "Гак кішка"; }
    @Override public String getDescription() { return "Дозволяє рухатися швидко за допомогою гака кішки"; }
    @Override public int getMaxLevel() { return 3; }
    @Override protected boolean hideEnchantmentGlintDefault() { return true; }
    @Override protected boolean grindstoneRemovableDefault() { return false; }
    @Override public List<Material> getSupportedItems() { return List.of(Material.BEDROCK); }
    @Override public List<EnchantSlot> getSlots() { return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND); }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "launch-multiplier", 2.5,
            "min-entity-distance", 3.0,
            "immunity-ticks", 60,
            "durability-cost", 1
        );
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getEntity() instanceof Player p) || !flying.contains(p.getUniqueId())) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.FLY_INTO_WALL
                || e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !e.getAction().isRightClick()) return;
        ItemStack main = e.getItem();
        if (main == null || main.getType().isAir()) return;
        if (e.useItemInHand() == Event.Result.DENY
                || e.useInteractedBlock() == Event.Result.DENY
                || e.getAction() == Action.RIGHT_CLICK_BLOCK
                || main.hasData(DataComponentTypes.CONSUMABLE)) {
            mainHandBusy.put(e.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
        }
    }

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (!isMechanicEnabled()) return;
        if (e.getHand() == null) return;
        Player p = e.getPlayer();
        ItemStack rod = p.getInventory().getItem(e.getHand());
        if (levelOf(rod) == 0) return;

        if (e.getHand() == EquipmentSlot.OFF_HAND && mainHandBusy(p)) {
            if (e.getState() == PlayerFishEvent.State.FISHING) e.setCancelled(true);
            return;
        }

        double minEntityDistance = getDouble("min-entity-distance");
        int durabilityCost = getInt("durability-cost");

        FishHook hook = e.getHook();
        hook.setVelocity(hook.getVelocity().multiply(getDouble("launch-multiplier")));
        p.setFallDistance(0);

        Bukkit.getScheduler().runTaskTimer(Enchantments.getInstance(), task -> {
        	if (hook.isDead() || !hook.isValid() || hook.isInWater()
                    || hook.getLocation().distance(p.getLocation()) >= 20) {
                if (!hook.isDead()) hook.remove();
                task.cancel();
            } else if (e.getState() == PlayerFishEvent.State.CAUGHT_ENTITY && e.getCaught() instanceof LivingEntity target) {
                if (target.getLocation().distance(p.getLocation()) >= minEntityDistance) {
                    pull(p, target.getLocation(), 0);
                    p.getInventory().setItem(e.getHand(), damage(p, rod, durabilityCost));
                }
                hook.remove();
                task.cancel();
            } else if (hook.isOnGround() || nearSolidBlock(hook.getLocation())) {
                Location at = hook.getLocation();
                pull(p, goal(at), roof(at) ? 0 : LIP);
                p.getInventory().setItem(e.getHand(), damage(p, rod, durabilityCost));
                hook.remove();
                task.cancel();
            }
        }, 0L, 1L);
    }

    private boolean mainHandBusy(Player p) {
        Integer tick = mainHandBusy.remove(p.getUniqueId());
        return tick != null && Bukkit.getCurrentTick() - tick <= 1;
    }

    private boolean nearSolidBlock(Location l) {
        return !l.clone().add(1, 0, 0).getBlock().isPassable() || !l.clone().add(-1, 0, 0).getBlock().isPassable()
                || !l.clone().add(0, 1, 0).getBlock().isPassable() || !l.clone().add(0, -1, 0).getBlock().isPassable()
                || !l.clone().add(0, 0, 1).getBlock().isPassable() || !l.clone().add(0, 0, -1).getBlock().isPassable();
    }

    private static Location goal(Location at) {
        return at.getBlock().getLocation().add(0.5, 0, 0.5);
    }

    private static boolean roof(Location at) {
        return !at.clone().add(0, 1, 0).getBlock().isPassable();
    }

    private void pull(Player p, Location goal, double clear) {
        Location from = p.getLocation();
        boolean ground = p.isOnGround();

        double dx = goal.getX() - from.getX();
        double dz = goal.getZ() - from.getZ();
        double dy = goal.getY() - from.getY();
        double away = Math.hypot(dx, dz);
        double grip = ground ? 0.546 : DRAG;
        double ceiling = Math.max(dy + clear, 0) + ARC;
        boolean below = dy + clear < 0;

        int ticks = 1;
        while (ticks < 45 && peak(ticks) < dy + clear) ticks++;
        while (!below && ticks < 20 && away / spread(grip, ticks) > CAP && peak(ticks) < ceiling) ticks++;

        double up = below ? -DIVE : Math.min(top(ticks), CAP);
        if (!below && ground) up = Math.max(up, KICK);
        double reach = spread(grip, below ? dive(up, dy) : ticks);

        p.setVelocity(new Vector(
                Math.clamp(dx / reach, -CAP, CAP),
                up,
                Math.clamp(dz / reach, -CAP, CAP)));
        p.setFallDistance(0);

        particles(from, Particle.CRIT, 5, 0.1);
        soundFor(p, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.5f);

        UUID uid = p.getUniqueId();
        flying.add(uid);
        Bukkit.getScheduler().runTaskLater(Enchantments.getInstance(),
                () -> flying.remove(uid), getInt("immunity-ticks"));
    }

    private static int dive(double up, double dy) {
        double speed = up;
        double height = 0;
        int ticks = 0;
        while (ticks < 100 && height > dy) {
            height += speed;
            speed = (speed - FALL / 49) * 0.98;
            ticks++;
        }
        return ticks;
    }

    private static double top(int ticks) {
        return FALL * (Math.pow(0.98, -ticks) - 1);
    }

    private static double peak(int ticks) {
        double rise = (1 - Math.pow(0.98, ticks)) / 0.02;
        return top(ticks) * rise - FALL * (ticks - rise);
    }

    private static double spread(double grip, int ticks) {
        return 1 + grip * (1 - Math.pow(DRAG, ticks)) / (1 - DRAG);
    }

    private ItemStack damage(Player p, ItemStack item, int amount) {
        if (item == null || !(item.getItemMeta() instanceof Damageable d)) return item;
        int maxDurability = d.hasMaxDamage() ? d.getMaxDamage() : item.getType().getMaxDurability();
        int newDamage = d.getDamage() + amount;
        if (maxDurability > 0 && newDamage >= maxDurability) {
            soundFor(p, Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            return null;
        }
        d.setDamage(newDamage);
        item.setItemMeta(d);
        return item;
    }
}