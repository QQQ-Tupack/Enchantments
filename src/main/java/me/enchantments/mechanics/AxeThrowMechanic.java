package me.enchantments.mechanics;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.enchantments.main.Mechanic;

import java.util.List;
import java.util.Map;

public final class AxeThrowMechanic extends Mechanic {

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

    @Override
    protected void onRightClick(PlayerInteractEvent e, Player player, int level) {
        ItemStack axe = e.getItem();
        if (levelOf(axe) == 0) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK
                && (e.getClickedBlock() == null || !e.getClickedBlock().isPassable())) return;
        if (player.getInventory().getItemInOffHand().getType() == Material.SHIELD || player.isBlocking()) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;

        if (!ready(player, axe)) {
            deny(player);
            return;
        }
        if (!spend(player, getInt("hunger-cost"))) return;

        cooldown(player, axe, getInt("cooldown-ticks"));
        activation(player);

        Location start = player.getEyeLocation();
        ItemStack copy = axe.clone();
        player.getInventory().setItemInMainHand(null);

        Item flying = player.getWorld().dropItem(start, copy);
        flying.setPickupDelay(Integer.MAX_VALUE);
        flying.setVelocity(start.getDirection().normalize().multiply(2));
        soundAt(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1f);

        fly(player, flying, copy);
    }

    private void fly(Player player, Item flying, ItemStack axe) {
        int maxFlightTicks = getInt("max-flight-ticks");
        double damageMult = getDouble("damage-mult");

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!flying.isValid() || t++ > maxFlightTicks) {
                    returnAxe(player, flying);
                    cancel();
                    return;
                }

                for (Entity nearby : flying.getNearbyEntities(1, 1, 1)) {
                    if (nearby instanceof LivingEntity target && !target.equals(player) && canHit(player, target)) {
                        target.damage(damage(axe) * damageMult, player);
                        particles(target.getLocation(), Particle.CRIT, 10, 0);
                        soundAt(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
                        returnAxe(player, flying);
                        cancel();
                        return;
                    }
                }
                particles(flying.getLocation(), Particle.SWEEP_ATTACK, 2, 0);
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    private void returnAxe(Player player, Item flying) {
        int returnTicks = getInt("return-ticks");
        int durabilityCost = getInt("durability-cost");

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!flying.isValid()) {
                    cancel();
                    return;
                }

                Vector toPlayer = player.getEyeLocation().toVector().subtract(flying.getLocation().toVector());
                if (toPlayer.length() >= 1.2 && t++ <= returnTicks) {
                    flying.setVelocity(toPlayer.normalize().multiply(1.4));
                    return;
                }

                ItemStack axe = damage(player, flying.getItemStack(), durabilityCost);
                flying.remove();
                if (axe != null) {
                    player.getInventory().addItem(axe).values()
                            .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
                }
                soundAt(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 0.4f, 1f);
                cancel();
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    private boolean canHit(Player player, LivingEntity entity) {
        if (entity instanceof Player) return true;
        return !(entity instanceof Tameable tame && tame.isTamed() && player.equals(tame.getOwner()));
    }

    private double damage(ItemStack axe) {
        return switch (axe.getType()) {
            case WOODEN_AXE, GOLDEN_AXE -> 7.0;
            case STONE_AXE, IRON_AXE, DIAMOND_AXE -> 9.0;
            case NETHERITE_AXE -> 10.0;
            default -> 1.0;
        };
    }

    private ItemStack damage(Player player, ItemStack item, int amount) {
        if (item == null || player.getGameMode() == GameMode.CREATIVE
                || !(item.getItemMeta() instanceof Damageable meta)) return item;

        meta.setDamage(meta.getDamage() + amount);
        item.setItemMeta(meta);
        return meta.getDamage() >= item.getType().getMaxDurability() ? null : item;
    }
}
