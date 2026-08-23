package me.enchantments.mechanics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import me.enchantments.main.Mechanic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BoneLegionMechanic extends Mechanic {

    private static final NamespacedKey MASTER = new NamespacedKey("enchantments", "legion_master");

    @Override
    public String getId() {
        return "bone_legion";
    }

    @Override
    public String getName() {
        return "Череп полководця";
    }

    @Override
    public String getDescription() {
        return "Піднімає з землі скелетів, які б'ються за тебе, доки не розсиплються прахом";
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.WITHER_SKELETON_SKULL);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.MAINHAND);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "cooldown-ticks", 200,
            "lifespan-seconds", 35,
            "summon-health", 20.0,
            "summon-damage", 4.0,
            "hunger-cost", 6
        );
    }

    @Override public boolean isEnchantingTableAllowed() { return false; }
    @Override public boolean isChestLootAllowed() { return false; }
    @Override public boolean isVillagerTradeAllowed() { return false; }
    @Override public int getAnvilBaseCost() { return 30; }

    @Override
    protected void onRightClick(PlayerInteractEvent e, Player player, int level) {
        ItemStack skull = e.getItem();
        int held = levelOf(skull);
        if (held == 0) return;

        e.setUseItemInHand(Event.Result.DENY);
        e.setUseInteractedBlock(Event.Result.DENY);

        if (!ready(player, skull) || legion(player).size() >= held) {
            deny(player);
            return;
        }

        Location grave = ground(player);
        if (grave == null) {
            deny(player);
            return;
        }
        if (!spend(player, getInt("hunger-cost"))) return;

        cooldown(player, skull, Math.max(60, getInt("cooldown-ticks") - (held - 1) * 40));
        activation(player);
        raise(player, grave);
    }

    @Override
    protected void onAttack(EntityDamageByEntityEvent e, Player master, LivingEntity target, int level) {
        if (levelOf(master.getInventory().getItemInMainHand()) == 0) return;

        for (WitherSkeleton servant : legion(master)) {
            servant.setTarget(target);
            particles(servant.getLocation().add(0, 2, 0), Particle.SOUL_FIRE_FLAME, 4, 0.15, 0.1, 0.15, 0.01);
        }
    }

    private Location ground(Player player) {
        Location base = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(2));
        for (int dy = 1; dy >= -3; dy--) {
            Location candidate = base.clone().add(0, dy, 0);
            if (candidate.getBlock().getType().isAir()
                    && candidate.clone().add(0, 1, 0).getBlock().getType().isAir()
                    && candidate.clone().add(0, -1, 0).getBlock().getType().isSolid()) {
                return candidate;
            }
        }
        return null;
    }

    private void raise(Player master, Location grave) {
        soundAt(grave, Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.6f);
        soundAt(grave, Sound.BLOCK_SOUL_SOIL_BREAK, 1f, 0.6f);
        particles(grave, Particle.SOUL, 40, 0.4, 0.1, 0.4, 0.08);
        particles(grave, Particle.BLOCK, 25, 0.4, 0.1, 0.4, 0, Material.SOUL_SAND.createBlockData());

        WitherSkeleton servant = grave.getWorld().spawn(grave, WitherSkeleton.class, skeleton -> {
            attribute(skeleton, Attribute.MAX_HEALTH, getDouble("summon-health"));
            attribute(skeleton, Attribute.ATTACK_DAMAGE, getDouble("summon-damage"));
            skeleton.setHealth(getDouble("summon-health"));
            skeleton.getPersistentDataContainer().set(MASTER, PersistentDataType.STRING,
                    master.getUniqueId().toString());
            skeleton.setRemoveWhenFarAway(true);
            skeleton.setCanPickupItems(false);
            skeleton.getEquipment().clear();
            skeleton.getEquipment().setItemInMainHand(new ItemStack(Material.STONE_SWORD));
            skeleton.getEquipment().setItemInMainHandDropChance(0f);
        });

        new Crumble(servant).runTaskTimer(getPlugin(), 20L, 20L);
    }

    private void attribute(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    private List<WitherSkeleton> legion(Player master) {
        List<WitherSkeleton> found = new ArrayList<>();
        String id = master.getUniqueId().toString();

        for (Entity nearby : master.getNearbyEntities(48, 48, 48)) {
            if (nearby instanceof WitherSkeleton skeleton
                    && id.equals(skeleton.getPersistentDataContainer().get(MASTER, PersistentDataType.STRING))) {
                found.add(skeleton);
            }
        }
        return found;
    }

    private final class Crumble extends BukkitRunnable {

        private final WitherSkeleton servant;
        private int seconds;

        private Crumble(WitherSkeleton servant) {
            this.servant = servant;
        }

        @Override
        public void run() {
            if (!servant.isValid() || servant.isDead()) {
                cancel();
                return;
            }

            int lifespan = getInt("lifespan-seconds");
            Location at = servant.getLocation().add(0, 1, 0);

            if (++seconds >= lifespan - 5) {
                particles(at, Particle.ASH, 12, 0.3, 0.5, 0.3, 0.02);
                particles(at, Particle.SOUL, 3, 0.2, 0.4, 0.2, 0.01);
            }
            if (seconds < lifespan) return;

            cancel();
            soundAt(at, Sound.ENTITY_SKELETON_DEATH, 0.7f, 0.6f);
            particles(at, Particle.ASH, 40, 0.4, 0.6, 0.4, 0.05);
            particles(at, Particle.SOUL, 15, 0.3, 0.5, 0.3, 0.04);
            servant.remove();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!(e.getDamager() instanceof WitherSkeleton servant)) return;

        String id = servant.getPersistentDataContainer().get(MASTER, PersistentDataType.STRING);
        if (victim.getUniqueId().toString().equals(id)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onServantInfighting(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof WitherSkeleton attacker)) return;
        if (!(e.getEntity() instanceof WitherSkeleton victim)) return;

        String a = attacker.getPersistentDataContainer().get(MASTER, PersistentDataType.STRING);
        String b = victim.getPersistentDataContainer().get(MASTER, PersistentDataType.STRING);
        if (a != null && a.equals(b)) {
            e.setCancelled(true);
            if (attacker instanceof Mob mob) mob.setTarget(null);
        }
    }

    @EventHandler
    public void onServantDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof WitherSkeleton servant)) return;
        if (!servant.getPersistentDataContainer().has(MASTER, PersistentDataType.STRING)) return;

        e.getDrops().clear();
        e.setDroppedExp(0);

        Location at = servant.getLocation().add(0, 1, 0);
        particles(at, Particle.ASH, 30, 0.4, 0.5, 0.4, 0.04);
        soundAt(at, Sound.BLOCK_SOUL_SAND_BREAK, 0.8f, 0.7f);
    }
}
