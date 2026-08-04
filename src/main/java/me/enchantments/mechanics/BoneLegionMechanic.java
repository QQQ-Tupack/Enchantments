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
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Череп полководця.
 *
 * ПКМ піднімає з-під землі візер-скелета, який б'ється за тебе й за півхвилини розсипається прахом.
 * Скільки їх одночасно — стільки, який рівень зачарування.
 *
 * Слуги б'ють те, що б'єш ти, не чіпають господаря й нічого не лишають після смерті,
 * тож легіон не перетворюється на ферму кісток. Хто чий — записано на самому скелеті,
 * тому плагін не тримає жодного списку: помер скелет — зникла й прив'язка.
 */
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
            "summon-range", 4.0
        );
    }

    @Override public boolean isEnchantingTableAllowed() { return false; }
    @Override public boolean isChestLootAllowed() { return false; }
    @Override public boolean isVillagerTradeAllowed() { return false; }
    @Override public int getAnvilBaseCost() { return 30; }

    // ==========================================
    //   ПРИКЛИКАННЯ
    // ==========================================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent e) {
        if (!isMechanicEnabled()) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack skull = e.getItem();
        int level = levelOf(skull);
        if (level == 0) return;

        Player player = e.getPlayer();
        // череп — предмет, що ставиться: без цього ПКМ по блоку просто поставить голову
        e.setUseItemInHand(Event.Result.DENY);
        e.setUseInteractedBlock(Event.Result.DENY);

        if (player.hasCooldown(skull.getType())) return;
        if (legion(player).size() >= level) return; // легіон уже повний

        Location grave = ground(player);
        if (grave == null) return;

        player.setCooldown(skull.getType(), Math.max(60, getInt("cooldown-ticks") - (level - 1) * 40));
        raise(player, grave, level);
    }

    /** Місце під ногами гравця, куди можна вписати скелета. */
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

    private void raise(Player master, Location grave, int level) {
        grave.getWorld().playSound(grave, Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.6f);
        grave.getWorld().playSound(grave, Sound.BLOCK_SOUL_SOIL_BREAK, 1f, 0.6f);
        grave.getWorld().spawnParticle(Particle.SOUL, grave, 40, 0.4, 0.1, 0.4, 0.08);
        grave.getWorld().spawnParticle(Particle.BLOCK, grave, 25, 0.4, 0.1, 0.4,
                Material.SOUL_SAND.createBlockData());

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

    /** Скелет доживає свій строк і осипається прахом — трупів після легіону не лишається. */
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

            // останні п'ять секунд скелет помітно розсипається
            if (++seconds >= lifespan - 5) {
                at.getWorld().spawnParticle(Particle.ASH, at, 12, 0.3, 0.5, 0.3, 0.02);
                at.getWorld().spawnParticle(Particle.SOUL, at, 3, 0.2, 0.4, 0.2, 0.01);
            }
            if (seconds < lifespan) return;

            cancel();
            at.getWorld().playSound(at, Sound.ENTITY_SKELETON_DEATH, 0.7f, 0.6f);
            at.getWorld().spawnParticle(Particle.ASH, at, 40, 0.4, 0.6, 0.4, 0.05);
            at.getWorld().spawnParticle(Particle.SOUL, at, 15, 0.3, 0.5, 0.3, 0.04);
            servant.remove();
        }
    }

    // ==========================================
    //   ПОВЕДІНКА ЛЕГІОНУ
    // ==========================================

    /** Слуги в межах чутності господаря. Пошук по мітці, тому жодного списку тримати не треба. */
    private List<WitherSkeleton> legion(Player master) {
        List<WitherSkeleton> found = new ArrayList<>();
        String id = master.getUniqueId().toString();

        for (Entity nearby : master.getNearbyEntities(48, 48, 48)) {
            if (!(nearby instanceof WitherSkeleton skeleton)) continue;
            if (id.equals(skeleton.getPersistentDataContainer().get(MASTER, PersistentDataType.STRING))) {
                found.add(skeleton);
            }
        }
        return found;
    }

    /** Кого б'є господар — на того й кидається легіон. Наказів віддавати не треба. */
    @EventHandler(ignoreCancelled = true)
    public void onMasterAttack(EntityDamageByEntityEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getDamager() instanceof Player master)) return;
        if (!(e.getEntity() instanceof LivingEntity target)) return;
        if (levelOf(master.getInventory().getItemInMainHand()) == 0) return;

        for (WitherSkeleton servant : legion(master)) {
            servant.setTarget(target);
            servant.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                    servant.getLocation().add(0, 2, 0), 4, 0.15, 0.1, 0.15, 0.01);
        }
    }

    /** Слуга не б'є господаря — ні мечем, ні через промах. */
    @EventHandler(ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!(e.getDamager() instanceof WitherSkeleton servant)) return;

        String id = servant.getPersistentDataContainer().get(MASTER, PersistentDataType.STRING);
        if (victim.getUniqueId().toString().equals(id)) e.setCancelled(true);
    }

    /** Легіон нічого не лишає: ні кісток, ні черепів, ні досвіду. */
    @EventHandler
    public void onServantDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof WitherSkeleton servant)) return;
        if (!servant.getPersistentDataContainer().has(MASTER, PersistentDataType.STRING)) return;

        e.getDrops().clear();
        e.setDroppedExp(0);

        Location at = servant.getLocation().add(0, 1, 0);
        at.getWorld().spawnParticle(Particle.ASH, at, 30, 0.4, 0.5, 0.4, 0.04);
        at.getWorld().playSound(at, Sound.BLOCK_SOUL_SAND_BREAK, 0.8f, 0.7f);
    }

    /** Чужий легіон не має нацьковувати слуг одне на одного через випадковий зачіп. */
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
}
