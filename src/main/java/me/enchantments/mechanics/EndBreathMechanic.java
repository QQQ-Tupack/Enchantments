package me.enchantments.mechanics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.enchantments.main.Mechanic;

import java.util.List;
import java.util.Map;

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
            "projectile-speed", 1.4,
            "bullet-hunger-cost", 1,
            "breath-hunger-cost", 3
        );
    }

    @Override public boolean isEnchantingTableAllowed() { return false; }
    @Override public boolean isChestLootAllowed() { return false; }
    @Override public boolean isVillagerTradeAllowed() { return false; }
    @Override public int getAnvilBaseCost() { return 25; }
    @Override protected boolean grindstoneRemovableDefault() { return false; }

    @Override
    protected void onRightClick(PlayerInteractEvent e, Player player, int level) {
        ItemStack staff = armed(e, player);
        if (staff == null) return;

        boolean dragon = dragonMode(staff);
        if (!spend(player, getInt(dragon ? "breath-hunger-cost" : "bullet-hunger-cost"))) return;

        activation(player);
        if (dragon) breath(player, staff, level);
        else bullet(player, staff, level);
    }

    @Override
    protected void onSneakRightClick(PlayerInteractEvent e, Player player, int level) {
        ItemStack staff = armed(e, player);
        if (staff != null) switchMode(player, staff);
    }

    private ItemStack armed(PlayerInteractEvent e, Player player) {
        ItemStack staff = e.getItem();
        if (levelOf(staff) == 0) return null;

        e.setCancelled(true);
        if (!ready(player, staff)) {
            deny(player);
            return null;
        }
        return staff;
    }

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

        cooldown(player, staff, getInt("switch-cooldown-ticks"));
        particleCircle(player.getLocation().add(0, 1, 0), dragon ? Particle.DRAGON_BREATH : Particle.PORTAL, 1.2, 24);
        switchSound(player, dragon);
    }

    private void switchSound(Player player, boolean dragon) {
        Location at = player.getLocation();

        soundAt(at, Sound.UI_BUTTON_CLICK, 0.7f, dragon ? 0.7f : 1.6f);
        soundAt(at, Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, dragon ? 0.6f : 1.7f);
        soundAt(at, dragon ? Sound.ENTITY_ENDER_DRAGON_GROWL : Sound.ENTITY_SHULKER_AMBIENT,
                0.6f, dragon ? 1.4f : 1.8f);

        getPlugin().getServer().getScheduler().runTaskLater(getPlugin(), () -> soundAt(
                player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.6f, dragon ? 0.55f : 1.9f), 3L);
    }

    private void bullet(Player player, ItemStack staff, int level) {
        Vector direction = player.getLocation().getDirection().multiply(getDouble("projectile-speed"));
        ShulkerBullet bullet = player.launchProjectile(ShulkerBullet.class, direction);
        bullet.setShooter(player);

        cooldown(player, staff, Math.max(5, getInt("bullet-cooldown-ticks") - (level - 1) * 4));
        soundAt(player.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 1f, 1.3f);
        new Trail(bullet, Particle.END_ROD, Particle.PORTAL).runTaskTimer(getPlugin(), 1L, 1L);
    }

    private void breath(Player player, ItemStack staff, int level) {
        Vector direction = player.getLocation().getDirection().multiply(getDouble("projectile-speed"));
        DragonFireball fireball = player.launchProjectile(DragonFireball.class, direction);
        fireball.setShooter(player);

        cooldown(player, staff, Math.max(20, getInt("breath-cooldown-ticks") - (level - 1) * 10));
        soundAt(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 1f, 0.9f);
        new Trail(fireball, Particle.DRAGON_BREATH, Particle.REVERSE_PORTAL).runTaskTimer(getPlugin(), 1L, 1L);
    }

    private final class Trail extends BukkitRunnable {

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
            particles(projectile.getLocation(), core, 3, 0.08, 0.08, 0.08, 0.01);
            particles(projectile.getLocation(), aura, 6, 0.25, 0.25, 0.25, 0.02);
        }
    }
}
