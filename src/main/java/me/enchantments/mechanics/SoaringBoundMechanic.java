package me.enchantments.mechanics;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.List;
import java.util.Map;

public final class SoaringBoundMechanic extends Mechanic {

    @Override
    public String getId() {
        return "soaring_bound";
    }

    @Override
    public String getName() {
        return "Побратим";
    }

    @Override
    public String getDescription() {
        return "Вбитий ворог зцілює твоїх приручених тварин поруч; ти більше не б'єш їх випадково";
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
            "radius", 16.0,
            "heal-amount", 4.0,
            "prevent-friendly-fire", true
        );
    }

    @Override
    public double getEnchantingTableChance() {
        return 0.06;
    }

    @Override
    public int getWeight() {
        return 8;
    }

    @Override
    public double getChestLootChance() {
        return 0.05;
    }

    @Override
    public List<String> getLootChests() {
        return List.of(
            "minecraft:chests/village/village_taiga_house",
            "minecraft:chests/village/village_snowy_house",
            "minecraft:chests/village/village_shepherd",
            "minecraft:chests/igloo_chest"
        );
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of(
            "SHEPHERD", Map.of("villager-level", 3, "min-level", 1, "max-level", 1, "price", 20),
            "BUTCHER", Map.of("villager-level", 4, "min-level", 1, "max-level", 1, "price", 24)
        );
    }

    @Override
    public int getAnvilBaseCost() {
        return 6;
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        if (!isMechanicEnabled()) return;

        Player killer = e.getEntity().getKiller();
        if (killer == null || levelOnPlayer(killer) == 0) return;

        double radius = getDouble("radius");
        double heal = getDouble("heal-amount");
        int healed = 0;

        for (Entity nearby : killer.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof Tameable pet) || !(nearby instanceof LivingEntity living)) continue;
            if (!pet.isTamed() || pet.getOwner() == null) continue;
            if (!pet.getOwner().getUniqueId().equals(killer.getUniqueId())) continue;

            AttributeInstance attribute = living.getAttribute(Attribute.MAX_HEALTH);
            double max = attribute != null ? attribute.getValue() : 20.0;
            if (living.getHealth() >= max) continue;

            living.setHealth(Math.min(max, living.getHealth() + heal));
            living.getWorld().spawnParticle(Particle.HEART, living.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0.01);
            healed++;
        }

        if (healed > 0) {
            killer.getWorld().playSound(killer.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 0.4f, 1.3f);
            killer.sendActionBar(Component.text("§d❤ Побратими зцілені: " + healed));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent e) {
        if (!isMechanicEnabled()) return;
        if (!getBoolean("prevent-friendly-fire")) return;
        if (!(e.getDamager() instanceof Player player)) return;
        if (!(e.getEntity() instanceof Tameable pet) || !pet.isTamed()) return;
        if (pet.getOwner() == null || !pet.getOwner().getUniqueId().equals(player.getUniqueId())) return;
        if (player.isSneaking()) return;
        if (levelOnPlayer(player) == 0) return;

        e.setCancelled(true);
    }
}
