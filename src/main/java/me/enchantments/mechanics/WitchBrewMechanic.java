package me.enchantments.mechanics;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import me.enchantments.main.Mechanic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class WitchBrewMechanic extends Mechanic {

    private final Map<UUID, Long> selfImmunity = new HashMap<>();

    @Override public String getId() { return "witch_brew"; }
    @Override public String getName() { return "Відьомське Вариво"; }
    @Override public String getDescription() { return "Удар по власнику розбиває склянку під нападником: хмара з випадковим негативним ефектом."; }
    @Override public int getMaxLevel() { return 3; }
    @Override public List<Material> getSupportedItems() { return List.of(Material.TOTEM_OF_UNDYING); }
    @Override public List<EnchantSlot> getSlots() { return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND, EnchantSlot.HEAD); }

    @Override public double getEnchantingTableChance() { return 0.10; }
    @Override public List<String> getLootChests() { return List.of("minecraft:chests/woodland_mansion"); }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of("CLERIC", Map.of("level", 3, "min-emeralds", 14, "max-emeralds", 28));
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "cooldown-seconds", 20,
            "cloud-radius", 2.5,
            "cloud-duration-seconds", 4,
            "effect-duration-seconds", 5
        );
    }

    @Override
    protected void onDisable() {
        selfImmunity.clear();
    }

    @Override
    protected void onDamaged(EntityDamageEvent event, Player player, int level) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) return;
        if (!(byEntity.getDamager() instanceof LivingEntity attacker) || attacker.equals(player)) return;

        ItemStack item = wornItem(player);
        if (!ready(player, item)) return;
        cooldown(player, item, getInt("cooldown-seconds") * 20);

        double radius = getDouble("cloud-radius");
        int cloudTicks = getInt("cloud-duration-seconds") * 20;
        int effectTicks = getInt("effect-duration-seconds") * 20 + (level - 1) * 40;

        PotionEffectType type;
        Color color;
        switch (ThreadLocalRandom.current().nextInt(3)) {
            case 0 -> { type = PotionEffectType.POISON; color = Color.fromRGB(78, 147, 49); }
            case 1 -> { type = PotionEffectType.WEAKNESS; color = Color.fromRGB(72, 77, 72); }
            default -> { type = PotionEffectType.SLOWNESS; color = Color.fromRGB(90, 108, 129); }
        }

        selfImmunity.put(player.getUniqueId(), System.currentTimeMillis() + cloudTicks * 50L + 500L);

        Location at = attacker.getLocation();
        AreaEffectCloud cloud = (AreaEffectCloud) at.getWorld().spawnEntity(at, EntityType.AREA_EFFECT_CLOUD);
        cloud.setSource(player);
        cloud.setRadius((float) radius);
        cloud.setDuration(cloudTicks);
        cloud.setRadiusPerTick(0f);
        cloud.setParticle(Particle.WITCH);
        cloud.setColor(color);
        cloud.addCustomEffect(new PotionEffect(type, effectTicks, level - 1), true);

        soundAt(at, Sound.ENTITY_SPLASH_POTION_BREAK, 1.0f, 1.0f);
        soundAt(at, Sound.ENTITY_WITCH_AMBIENT, 0.6f, 1.2f);
        particles(at.clone().add(0, 1, 0), Particle.WITCH, 40, radius / 2, 0.5, radius / 2, 0.02);
    }

    private ItemStack wornItem(Player player) {
        for (EnchantSlot slot : getSlots()) {
            ItemStack item = switch (slot) {
                case MAINHAND -> player.getInventory().getItemInMainHand();
                case OFFHAND -> player.getInventory().getItemInOffHand();
                case HEAD -> player.getInventory().getHelmet();
                case CHEST -> player.getInventory().getChestplate();
                case LEGS -> player.getInventory().getLeggings();
                case FEET -> player.getInventory().getBoots();
                case BODY -> null;
            };
            if (levelOf(item) > 0) return item;
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!isMechanicEnabled()) return;
        if (event.getCause() != EntityPotionEffectEvent.Cause.AREA_EFFECT_CLOUD) return;
        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED
                && event.getAction() != EntityPotionEffectEvent.Action.CHANGED) return;

        UUID id = event.getEntity().getUniqueId();
        Long until = selfImmunity.get(id);
        if (until == null) return;
        if (System.currentTimeMillis() > until) {
            selfImmunity.remove(id);
            return;
        }

        event.setCancelled(true);
    }
}
