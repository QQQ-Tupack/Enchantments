package me.enchantments.mechanics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;

/**
 * Зброя вардена: зона тиші.
 *
 * Удар булавою розстеляє навколо мертву тишу. Поки вона тримається, кожен різкий рух
 * усередині відлунює болем — а хто завмер, тому нічого не буде. Це не пробиває броню
 * і майже нічого не варте проти обережного гравця: зброя не вбиває, а диктує правила бою.
 *
 * Рішення читається без жодного слова: у зоні темно, з підлоги йде скалк, а кожен ривок
 * ловить удар. Гравець розуміє "не рухайся" з першого разу.
 */
public final class WardenEchoMechanic extends Mechanic {

    @Override
    public String getId() {
        return "warden_echo";
    }

    @Override
    public String getName() {
        return "Зона тиші";
    }

    @Override
    public String getDescription() {
        return "Розстеляє тишу навколо: усередині неї карається кожен різкий рух";
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.MACE);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.MAINHAND);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "cooldown-ticks", 160,
            "radius", 7.0,
            "duration-ticks", 120,
            "motion-threshold", 0.14,
            "damage-per-pulse", 1.6,
            "pulse-interval", 5
        );
    }

    @Override public boolean isEnchantingTableAllowed() { return false; }
    @Override public boolean isChestLootAllowed() { return false; }
    @Override public boolean isVillagerTradeAllowed() { return false; }
    @Override public int getAnvilBaseCost() { return 30; }

    // ignoreCancelled тут ставити не можна: при ПКМ по повітрю Bukkit виставляє
    // useItemInHand у DENY, і подія приходить уже "скасованою" — саме тому здібності
    // спрацьовували лише коли дивишся на блок
    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent e) {
        if (!isMechanicEnabled()) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack mace = e.getItem();
        int level = levelOf(mace);
        if (level == 0) return;

        Player player = e.getPlayer();
        if (player.hasCooldown(mace.getType())) return;

        e.setCancelled(true);
        player.setCooldown(mace.getType(), Math.max(60, getInt("cooldown-ticks") - (level - 1) * 30));

        Location epicenter = player.getLocation().clone();
        double radius = getDouble("radius") + (level - 1);

        player.getWorld().playSound(epicenter, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.4f, 0.7f);
        player.getWorld().playSound(epicenter, Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.6f);
        blind(epicenter, radius, player, level);

        new Silence(epicenter, radius, player, level).runTaskTimer(getPlugin(), 1L, 1L);
    }

    /** Темрява ставиться один раз при накритті — далі зона працює сама. */
    private void blind(Location epicenter, double radius, Player caster, int level) {
        int duration = getInt("duration-ticks");
        for (Entity nearby : epicenter.getWorld().getNearbyEntities(epicenter, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity target) || target.equals(caster)) continue;
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, duration, 0, true, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, level - 1, true, true, true));
        }
    }

    /** Купол зі скалку, який б'є тих, хто в ньому смикається. */
    private final class Silence extends BukkitRunnable {

        private final Location epicenter;
        private final double radius;
        private final Player caster;
        private final int level;
        private int ticks;

        private Silence(Location epicenter, double radius, Player caster, int level) {
            this.epicenter = epicenter;
            this.radius = radius;
            this.caster = caster;
            this.level = level;
        }

        @Override
        public void run() {
            if (++ticks > getInt("duration-ticks")) {
                epicenter.getWorld().playSound(epicenter, Sound.BLOCK_SCULK_SENSOR_CLICKING_STOP, 1f, 1.2f);
                cancel();
                return;
            }

            dome();
            if (ticks % Math.max(1, getInt("pulse-interval")) != 0) return;

            for (Entity nearby : epicenter.getWorld().getNearbyEntities(epicenter, radius, radius, radius)) {
                if (!(nearby instanceof LivingEntity target) || target.equals(caster)) continue;

                // жодного стану: сама швидкість цілі й вирішує, чи вона "шумить"
                double motion = nearby.getVelocity().length();
                if (motion < getDouble("motion-threshold")) continue;

                double damage = getDouble("damage-per-pulse") * (1 + (level - 1) * 0.3) * Math.min(3.0, motion * 4);
                target.damage(damage, caster);
                target.getWorld().spawnParticle(Particle.SONIC_BOOM, target.getLocation().add(0, 1, 0), 1);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.5f, 1.6f);
            }
        }

        /** Пульсуюче кільце по землі + скалкові іскри: межу зони видно завжди. */
        private void dome() {
            double pulse = radius * (0.55 + 0.45 * Math.abs(Math.sin(ticks * 0.08)));
            int points = (int) (pulse * 8);

            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2 * i / points;
                Location point = epicenter.clone().add(Math.cos(angle) * pulse, 0.15, Math.sin(angle) * pulse);
                point.getWorld().spawnParticle(Particle.SCULK_SOUL, point, 1, 0, 0, 0, 0);
            }
            if (ticks % 4 == 0) {
                epicenter.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, epicenter.clone().add(0, 1, 0),
                        6, radius / 2, 1.0, radius / 2, 0);
            }
        }
    }
}
