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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.enchantments.main.Mechanic;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SoulStormMechanic extends Mechanic {

    private static final NamespacedKey SOULS = new NamespacedKey("enchantments", "soul_storm_souls");

    @Override
    public String getId() {
        return "soul_storm";
    }

    @Override
    public String getName() {
        return "Буря душ";
    }

    @Override
    public String getDescription() {
        return "П'є життя з кожного удару, збирає душі вбитих і випускає їх вихором";
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
            "cooldown-ticks", 140,
            "damage", 8.0,
            "damage-per-soul", 0.5,
            "radius", 2.6,
            "base-range", 12,
            "range-per-soul", 0.4,
            "max-souls", 20,
            "lifesteal-percent", 0.10,
            "lifesteal-cap", 1.5
        );
    }

    @Override public boolean isEnchantingTableAllowed() { return false; }
    @Override public boolean isChestLootAllowed() { return false; }
    @Override public boolean isVillagerTradeAllowed() { return false; }
    @Override public int getAnvilBaseCost() { return 25; }
    @Override protected boolean grindstoneRemovableDefault() { return false; }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getDamager() instanceof Player player)) return;
        if (!(e.getEntity() instanceof LivingEntity)) return;

        ItemStack sword = player.getInventory().getItemInMainHand();
        if (levelOf(sword) == 0) return;

        double heal = Math.min(getDouble("lifesteal-cap"), e.getFinalDamage() * getDouble("lifesteal-percent"));
        if (heal <= 0) return;

        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        double max = attribute != null ? attribute.getValue() : 20.0;
        if (player.getHealth() >= max) return;

        player.setHealth(Math.min(max, player.getHealth() + heal));
        player.getWorld().spawnParticle(Particle.SCULK_SOUL, player.getLocation().add(0, 1.6, 0), 2, 0.2, 0.2, 0.2, 0.01);

        player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_ITEM_TAKEN, 0.35f, 0.7f);
    }

    /** Вбитий лишає в клинку душу. Чим більше душ — тим страшніша буря. */
    @EventHandler
    public void onKill(EntityDeathEvent e) {
        if (!isMechanicEnabled()) return;

        Player killer = e.getEntity().getKiller();
        if (killer == null) return;

        ItemStack sword = killer.getInventory().getItemInMainHand();
        int level = levelOf(sword);
        if (level == 0) return;

        int souls = souls(sword);
        if (souls >= getInt("max-souls")) return;

        int stored = souls + 1;
        setSouls(sword, stored);
        e.getEntity().getWorld().spawnParticle(Particle.SOUL, e.getEntity().getLocation().add(0, 1, 0), 8, 0.2, 0.4, 0.2, 0.04);
        absorbSound(killer, stored);
    }

    private void absorbSound(Player killer, int stored) {
        int max = Math.max(1, getInt("max-souls"));
        float progress = Math.min(1f, (float) stored / max);
        Location at = killer.getLocation();

        killer.playSound(at, Sound.PARTICLE_SOUL_ESCAPE, 0.7f, 0.6f + progress * 1.0f);
        killer.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 0.7f + progress * 1.1f);

        if (stored >= max) {
            killer.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
            killer.playSound(at, Sound.ENTITY_WITHER_SPAWN, 0.25f, 1.8f);
        } else if (stored % 5 == 0) {
            killer.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 0.8f + progress);
        }
    }

    private int souls(ItemStack sword) {
        ItemMeta meta = sword.getItemMeta();
        return meta == null ? 0 : meta.getPersistentDataContainer()
                .getOrDefault(SOULS, PersistentDataType.INTEGER, 0);
    }

    private void setSouls(ItemStack sword, int value) {
        ItemMeta meta = sword.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(SOULS, PersistentDataType.INTEGER, value);
        sword.setItemMeta(meta);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent e) {
        if (!isMechanicEnabled()) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack sword = e.getItem();
        int level = levelOf(sword);
        if (level == 0) return;

        Player player = e.getPlayer();
        if (player.hasCooldown(sword.getType())) return;

        int souls = souls(sword);
        setSouls(sword, 0);

        e.setCancelled(true);
        player.setCooldown(sword.getType(), Math.max(40, getInt("cooldown-ticks") - (level - 1) * 25));
        float charge = Math.min(1f, (float) souls / Math.max(1, getInt("max-souls")));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.9f, 0.85f - charge * 0.4f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.7f + charge * 0.5f, 0.7f);
        if (souls > 0) {
            player.getWorld().playSound(player.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.5f);
        }

        new Storm(player, level, souls).runTaskTimer(getPlugin(), 0L, 1L);
    }

    private final class Storm extends BukkitRunnable {

        private final Player caster;
        private final int level;
        private final int souls;
        private final Vector step;
        private final Set<UUID> alreadyHit = new HashSet<>();
        private final int maxSteps;
        private Location head;
        private int travelled;

        private Storm(Player caster, int level, int souls) {
            this.caster = caster;
            this.level = level;
            this.souls = souls;
            this.head = caster.getEyeLocation();
            this.step = caster.getLocation().getDirection().normalize().multiply(0.9);
            this.maxSteps = (int) ((getInt("base-range") + souls * getDouble("range-per-soul")) / 0.9);
        }

        @Override
        public void run() {
            if (!caster.isOnline() || travelled++ > maxSteps) {
                cancel();
                return;
            }

            head = head.add(step);
            if (head.getBlock().getType().isSolid()) {
                head.getWorld().spawnParticle(Particle.SOUL, head, 30, 0.4, 0.4, 0.4, 0.06);
                head.getWorld().playSound(head, Sound.PARTICLE_SOUL_ESCAPE, 0.8f, 0.6f);
                cancel();
                return;
            }

            spiral();
            damage();
        }

        private void spiral() {
            int arms = 3 + Math.min(5, souls / 4);
            double radius = getDouble("radius") * 0.6;

            for (int i = 0; i < arms; i++) {
                double angle = (travelled * 0.55) + (Math.PI * 2 * i / arms);
                Location point = head.clone().add(Math.cos(angle) * radius, Math.sin(angle) * radius, 0);
                head.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, point, 1, 0, 0, 0, 0);
            }
            head.getWorld().spawnParticle(Particle.SOUL, head, 6, 0.3, 0.3, 0.3, 0.02);
            head.getWorld().spawnParticle(Particle.SMOKE, head, 4, 0.35, 0.35, 0.35, 0.01);
        }

        private void damage() {
            double radius = getDouble("radius");
            double power = (getDouble("damage") + souls * getDouble("damage-per-soul")) * (1 + (level - 1) * 0.2);

            for (Entity nearby : head.getWorld().getNearbyEntities(head, radius, radius, radius)) {
                if (!(nearby instanceof LivingEntity target) || target.equals(caster)) continue;
                if (!alreadyHit.add(target.getUniqueId())) continue;

                target.damage(power, caster);
                target.getWorld().spawnParticle(Particle.SCULK_SOUL, target.getLocation().add(0, 1, 0), 14, 0.3, 0.5, 0.3, 0.04);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.5f, 1.4f);
            }
        }
    }
}
