package me.enchantments.mechanics;

import me.enchantments.main.Enchantments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public final class AxeThrowMechanic extends Mechanic {

    private final Set<UUID> thrown = new HashSet<>();
    private final Set<UUID> onCooldown = new HashSet<>();

    @Override
    public String getId() {
        return "axe_throw";
    }

    @Override
    public String getName() {
        return "Кидок сокири";
    }

    @Override
    public String getDescription() {
        return "Дозволяє кидати сокиру гарячою клавішею";
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    protected boolean hideEnchantmentGlintDefault() {
        return false;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE, 
                       Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.MAINHAND);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "cooldown-ticks", 60,
            "max-flight-ticks", 15,
            "return-ticks", 20,
            "hunger-cost", 1,
            "durability-cost", 2,
            "damage-mult", 0.75
        );
    }

    @EventHandler
    public void onThrow(PlayerInteractEvent e) {
        if (!isMechanicEnabled()) return;
        Player p = e.getPlayer();
        ItemStack axe = p.getInventory().getItemInMainHand();
        if (levelOf(axe) == 0) return;

        boolean blockedByShield = p.getInventory().getItemInOffHand().getType() == Material.SHIELD || p.isBlocking();
        boolean rightClick = e.getAction() == Action.RIGHT_CLICK_AIR
                || (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && e.getClickedBlock().isPassable());
        if (!rightClick || blockedByShield || p.getGameMode() == GameMode.CREATIVE || thrown.contains(p.getUniqueId())) return;

        int hungerCost = getInt("hunger-cost");
        if (onCooldown.contains(p.getUniqueId())) {
            p.sendActionBar(Component.text("Сокира ще не готова!", NamedTextColor.RED));
            return;
        }
        if (p.getFoodLevel() < hungerCost) {
            p.sendActionBar(Component.text("Занадто голодний для кидання!", NamedTextColor.RED));
            return;
        }

        p.setFoodLevel(p.getFoodLevel() - hungerCost);
        onCooldown.add(p.getUniqueId());
        int cooldownTicks = getInt("cooldown-ticks");
        Bukkit.getScheduler().runTaskLater(Enchantments.getInstance(), () -> onCooldown.remove(p.getUniqueId()), cooldownTicks);

        World w = p.getWorld();
        Location start = p.getEyeLocation();
        p.getInventory().setItemInMainHand(null);
        Item flying = w.dropItem(start, axe);
        flying.setPickupDelay(Integer.MAX_VALUE);
        flying.setVelocity(start.getDirection().normalize().multiply(2));
        w.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1, 1);
        thrown.add(p.getUniqueId());

        int maxFlightTicks = getInt("max-flight-ticks");
        double damageMult = getDouble("damage-mult");
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!flying.isValid() || t > maxFlightTicks) { returnAxe(p, flying); cancel(); return; }

                for (Entity ent : flying.getNearbyEntities(1, 1, 1)) {
                    if (ent instanceof LivingEntity target && ent != p && canHit(p, target)) {
                        target.damage(damage(axe) * damageMult, p);
                        w.spawnParticle(Particle.CRIT, ent.getLocation(), 10);
                        w.playSound(ent.getLocation(), Sound.ENTITY_PLAYER_HURT, 1, 1);
                        returnAxe(p, flying);
                        cancel();
                        return;
                    }
                }
                w.spawnParticle(Particle.SWEEP_ATTACK, flying.getLocation(), 2);
                t++;
            }
        }.runTaskTimer(Enchantments.getInstance(), 0, 1);
    }

    private void returnAxe(Player p, Item flying) {
        int returnTicks = getInt("return-ticks");
        int durabilityCost = getInt("durability-cost");
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!flying.isValid()) { thrown.remove(p.getUniqueId()); cancel(); return; }

                Vector toPlayer = p.getEyeLocation().toVector().subtract(flying.getLocation().toVector());
                if (toPlayer.length() < 1.2 || t > returnTicks) {
                    ItemStack axe = damage(p, flying.getItemStack(), durabilityCost);
                    flying.remove();
                    thrown.remove(p.getUniqueId());
                    if (axe != null) {
                        Map<Integer, ItemStack> leftover = p.getInventory().addItem(axe);
                        leftover.values().forEach(i -> p.getWorld().dropItem(p.getLocation(), i));
                    }
                    p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_THROW, 0.4f, 1);
                    cancel();
                    return;
                }
                flying.setVelocity(toPlayer.normalize().multiply(1.4));
                t++;
            }
        }.runTaskTimer(Enchantments.getInstance(), 0, 1);
    }

    private boolean canHit(Player p, LivingEntity e) {
        if (e instanceof Player) return true;
        if (e instanceof Tameable t && t.isTamed() && p.equals(t.getOwner())) return false;
        return true;
    }

    private double damage(ItemStack axe) {
        return switch (axe.getType()) {
            case WOODEN_AXE, GOLDEN_AXE -> 7.0;
            case STONE_AXE, IRON_AXE, DIAMOND_AXE -> 9.0;
            case NETHERITE_AXE -> 10.0;
            default -> 1.0;
        };
    }

    private ItemStack damage(Player p, ItemStack item, int amount) {
        if (item == null || p.getGameMode() == GameMode.CREATIVE || !(item.getItemMeta() instanceof Damageable d)) return item;
        d.setDamage(d.getDamage() + amount);
        item.setItemMeta((org.bukkit.inventory.meta.ItemMeta) d);
        return d.getDamage() >= item.getType().getMaxDurability() ? null : item;
    }
}
