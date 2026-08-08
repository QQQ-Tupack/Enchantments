package me.enchantments.mechanics;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PhantomVeilMechanic extends Mechanic {

    private final Map<UUID, Location> lastPosition = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> stillCount = new ConcurrentHashMap<>();
    private final Set<UUID> veiled = ConcurrentHashMap.newKeySet();

    @Override
    public String getId() {
        return "phantom_veil";
    }

    @Override
    public String getName() {
        return "Примарна вуаль";
    }

    @Override
    public String getDescription() {
        return "Присядь і завмри — вороги перестануть тебе помічати, доки ти не поворухнешся";
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.TOTEM_OF_UNDYING);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND, EnchantSlot.HEAD);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "radius", 12.0,
            "activation-seconds", 2
        );
    }

    @Override
    public double getEnchantingTableChance() {
        return 0.03;
    }

    @Override
    public int getWeight() {
        return 4;
    }

    @Override
    public double getChestLootChance() {
        return 0.05;
    }

    @Override
    public List<String> getLootChests() {
        return List.of(
            "minecraft:chests/woodland_mansion",
            "minecraft:chests/pillager_outpost",
            "minecraft:chests/jungle_temple",
            "minecraft:chests/ancient_city"
        );
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of("LEATHERWORKER", Map.of("villager-level", 4, "min-level", 1, "max-level", 1, "price", 28));
    }

    @Override
    public int getAnvilBaseCost() {
        return 11;
    }

    @Override
    protected void onEnable() {
        Bukkit.getScheduler().runTaskTimer(getPlugin(), () -> {
            if (!isMechanicEnabled()) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (levelOnPlayer(player) == 0) {
                    dropVeil(player);
                    continue;
                }
                tick(player);
            }
        }, 5L, 5L);
    }

    private void tick(Player player) {
        UUID id = player.getUniqueId();
        Location now = player.getLocation();
        Location before = lastPosition.put(id, now.clone());

        boolean still = player.isSneaking()
            && before != null
            && before.getWorld().equals(now.getWorld())
            && before.distanceSquared(now) < 0.0025;

        if (!still) {
            stillCount.remove(id);
            dropVeil(player);
            return;
        }

        int needed = Math.max(1, getInt("activation-seconds")) * 4; // цикл виконується 4 рази на секунду
        int count = stillCount.merge(id, 1, Integer::sum);

        if (count >= needed && veiled.add(id)) {
            player.sendActionBar(Component.text("§8✦ Вуаль огорнула тебе"));
        }
        if (veiled.contains(id)) {
            player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 0.2, 0), 2, 0.3, 0.05, 0.3, 0);
            forget(player);
        }
    }

    /** Моби, що вже йшли на гравця до появи вуалі, події таргету не викликають — знімаємо ціль вручну. */
    private void forget(Player player) {
        double radius = getDouble("radius");
        for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof Mob mob && player.equals(mob.getTarget())) {
                mob.setTarget(null);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getTarget() instanceof Player player)) return;
        if (!veiled.contains(player.getUniqueId())) return;
        if (e.getReason() == EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY) return; // вдарив — сам винен
        if (levelOnPlayer(player) == 0) return;
        if (!e.getEntity().getWorld().equals(player.getWorld())) return;

        double radius = getDouble("radius");
        if (e.getEntity().getLocation().distanceSquared(player.getLocation()) > radius * radius) return;

        e.setTarget(null);
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player player) {
            dropVeil(player);
            return;
        }
        if (!isMechanicEnabled()) return;
        if (!(e.getEntity() instanceof Player player)) return;
        if (!veiled.contains(player.getUniqueId())) return;

        // моб, який усе одно дістав до гравця — удар не проходить, ціль скидається
        Entity source = e.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Entity shooter ? shooter : e.getDamager();

        if (source instanceof Mob mob) {
            mob.setTarget(null);
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        dropVeil(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        veiled.remove(id);
        stillCount.remove(id);
        lastPosition.remove(id);
    }

    private void dropVeil(Player player) {
        if (veiled.remove(player.getUniqueId())) {
            stillCount.remove(player.getUniqueId());
            player.sendActionBar(Component.text("§7Вуаль спала"));
        }
    }
}