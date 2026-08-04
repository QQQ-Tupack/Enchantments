package me.enchantments.mechanics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;

/**
 * Посох Краю. Присідання + ПКМ перемикає режим, ПКМ стріляє.
 * Режим живе в самому предметі, тому не потребує жодних мап і переживає перезапуск.
 * Який режим активний — чути й видно: у шалкера бірюзовий портал і високий писк,
 * у подиху дракона фіолетовий вихор і рик.
 */
public final class EndBreathMechanic extends Mechanic {

    private static final NamespacedKey MODE = new NamespacedKey("enchantments", "end_breath_mode");

    @Override
    public String getId() {
        return "end_breath";
    }

    @Override
    public String getName() {
        return "Подих Краю";
    }

    @Override
    public String getDescription() {
        return "Стріляє кулею шалкера або подихом дракона; присідання з ПКМ змінює режим";
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE,
                       Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.MAINHAND);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "bullet-cooldown-ticks", 25,
            "breath-cooldown-ticks", 70,
            "switch-cooldown-ticks", 10,
            "projectile-speed", 1.4
        );
    }

    // здобувається з боса, тому жодних столів, скринь і селян
    @Override public boolean isEnchantingTableAllowed() { return false; }
    @Override public boolean isChestLootAllowed() { return false; }
    @Override public boolean isVillagerTradeAllowed() { return false; }
    @Override public int getAnvilBaseCost() { return 25; }

    // ignoreCancelled тут ставити не можна: при ПКМ по повітрю Bukkit виставляє
    // useItemInHand у DENY, і подія приходить уже "скасованою" — саме тому здібності
    // спрацьовували лише коли дивишся на блок
    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent e) {
        if (!isMechanicEnabled()) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack staff = e.getItem();
        int level = levelOf(staff);
        if (level == 0) return;

        Player player = e.getPlayer();
        if (player.hasCooldown(staff.getType())) return;
        e.setCancelled(true);

        if (player.isSneaking()) {
            switchMode(player, staff);
        } else if (dragonMode(staff)) {
            breath(player, level);
        } else {
            bullet(player, level);
        }
    }

    // ==========================================
    //   РЕЖИМ — У САМОМУ ПРЕДМЕТІ
    // ==========================================

    private boolean dragonMode(ItemStack staff) {
        ItemMeta meta = staff.getItemMeta();
        return meta != null && meta.getPersistentDataContainer()
                .getOrDefault(MODE, PersistentDataType.BYTE, (byte) 0) == 1;
    }

    private void switchMode(Player player, ItemStack staff) {
        ItemMeta meta = staff.getItemMeta();
        if (meta == null) return;

        boolean dragon = !dragonMode(staff);
        meta.getPersistentDataContainer().set(MODE, PersistentDataType.BYTE, (byte) (dragon ? 1 : 0));
        staff.setItemMeta(meta);

        player.setCooldown(staff.getType(), getInt("switch-cooldown-ticks"));
        ring(player, dragon ? Particle.DRAGON_BREATH : Particle.PORTAL);
        switchSound(player, dragon);
    }

    /**
     * Три шари звуку: клац затвора, тембр режиму й нота, що показує напрямок перемикання.
     * Низька нота — важкий подих дракона, висока — легка куля шалкера.
     */
    private void switchSound(Player player, boolean dragon) {
        World world = player.getWorld();
        Location at = player.getLocation();

        world.playSound(at, Sound.UI_BUTTON_CLICK, 0.7f, dragon ? 0.7f : 1.6f);
        world.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, dragon ? 0.6f : 1.7f);
        world.playSound(at, dragon ? Sound.ENTITY_ENDER_DRAGON_GROWL : Sound.ENTITY_SHULKER_AMBIENT,
                0.6f, dragon ? 1.4f : 1.8f);

        getPlugin().getServer().getScheduler().runTaskLater(getPlugin(), () -> world.playSound(
                player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.6f, dragon ? 0.55f : 1.9f), 3L);
    }

    /** Кільце навколо гравця — миттєво читається як "режим змінився". */
    private void ring(Player player, Particle particle) {
        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2 * i / 24;
            player.getWorld().spawnParticle(particle,
                    player.getLocation().add(Math.cos(angle) * 1.2, 1.0, Math.sin(angle) * 1.2),
                    2, 0.02, 0.02, 0.02, 0.01);
        }
    }

    // ==========================================
    //   ПОСТРІЛИ
    // ==========================================

    private void bullet(Player player, int level) {
        Vector direction = player.getLocation().getDirection().multiply(getDouble("projectile-speed"));
        ShulkerBullet bullet = player.launchProjectile(ShulkerBullet.class, direction);
        bullet.setShooter(player);

        player.setCooldown(player.getInventory().getItemInMainHand().getType(),
                Math.max(5, getInt("bullet-cooldown-ticks") - (level - 1) * 4));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 1f, 1.3f);
        new Trail(bullet, Particle.END_ROD, Particle.PORTAL).runTaskTimer(getPlugin(), 1L, 1L);
    }

    private void breath(Player player, int level) {
        Vector direction = player.getLocation().getDirection().multiply(getDouble("projectile-speed"));
        DragonFireball fireball = player.launchProjectile(DragonFireball.class, direction);
        fireball.setShooter(player);

        player.setCooldown(player.getInventory().getItemInMainHand().getType(),
                Math.max(20, getInt("breath-cooldown-ticks") - (level - 1) * 10));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 1f, 0.9f);
        new Trail(fireball, Particle.DRAGON_BREATH, Particle.REVERSE_PORTAL).runTaskTimer(getPlugin(), 1L, 1L);
    }

    /** Хвіст із частинок, що живе рівно стільки, скільки летить снаряд. */
    private static final class Trail extends BukkitRunnable {

        private final Projectile projectile;
        private final Particle core;
        private final Particle aura;

        private Trail(Projectile projectile, Particle core, Particle aura) {
            this.projectile = projectile;
            this.core = core;
            this.aura = aura;
        }

        @Override
        public void run() {
            if (!projectile.isValid() || projectile.isDead()) {
                cancel();
                return;
            }
            projectile.getWorld().spawnParticle(core, projectile.getLocation(), 3, 0.08, 0.08, 0.08, 0.01);
            projectile.getWorld().spawnParticle(aura, projectile.getLocation(), 6, 0.25, 0.25, 0.25, 0.02);
        }
    }
}
