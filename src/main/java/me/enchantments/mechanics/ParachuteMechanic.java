package me.enchantments.mechanics;

import me.enchantments.main.Mechanic;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ParachuteMechanic extends Mechanic {

    private final Map<UUID, List<Chicken>> active = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "parachute";
    }

    @Override
    public String getName() {
        return "Парашут";
    }

    @Override
    public String getDescription() {
        return "Присівши в падінні, розкриває парашут і сповільнює спуск.";
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE, Material.IRON_CHESTPLATE,
                       Material.COPPER_CHESTPLATE, Material.GOLDEN_CHESTPLATE, Material.DIAMOND_CHESTPLATE,
                       Material.NETHERITE_CHESTPLATE);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.CHEST);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "duration-ticks", 100,
            "cooldown-ticks", 200,
            "fall-distance-trigger", 3.0,
            "descend-speed", 0.3
        );
    }

    @Override
    protected void onSneakToggle(Player player, int level, boolean sneaking) {
        if (!sneaking || player.isFlying() || player.isOnGround() || player.getGameMode() != GameMode.SURVIVAL) return;
        if (player.getLocation().subtract(0, 1, 0).getBlock().getType().isSolid()) return;
        if (player.getFallDistance() < getDouble("fall-distance-trigger")) return;

        ItemStack chest = player.getInventory().getChestplate();
        if (!ready(player, chest)) {
            deny(player);
            return;
        }

        cooldown(player, chest, getInt("cooldown-ticks"));
        open(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.FALL && active.containsKey(p.getUniqueId())) {
            close(p);
            e.setCancelled(true);
        }
    }

    private void open(Player player) {
        UUID uid = player.getUniqueId();
        double descendSpeed = getDouble("descend-speed");

        List<Chicken> chickens = Stream.of(0, 1, -1).map(x -> spawn(player, x)).collect(Collectors.toList());
        active.put(uid, chickens);
        soundFor(player, Sound.ENTITY_PARROT_FLY, 1f, 1f);

        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t > getInt("duration-ticks") || player.isGliding() || !player.isSneaking() || player.isOnGround()
                        || player.getLocation().subtract(0, 1, 0).getBlock().getType().isSolid() || !active.containsKey(uid)) {
                    close(player);
                    cancel();
                    return;
                }
                Vector v = player.getVelocity();
                player.setVelocity(new Vector(v.getX(), -descendSpeed, v.getZ()));
                particles(player.getLocation(), Particle.CLOUD, 1, 0);
                player.setFallDistance(0);
                t++;
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    private void close(Player player) {
        active.getOrDefault(player.getUniqueId(), List.of()).forEach(c -> { c.setLeashHolder(null); c.remove(); });
        active.remove(player.getUniqueId());
    }

    private Chicken spawn(Player p, int offsetX) {
        Chicken c = p.getWorld().spawn(p.getLocation().add(offsetX, 2, 0), Chicken.class);
        c.setInvulnerable(true);
        c.setLeashHolder(p);
        return c;
    }
}
