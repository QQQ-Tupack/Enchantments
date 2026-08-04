package me.enchantments.mechanics;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class SaturationBonusMechanic extends Mechanic {

    private static final NamespacedKey HEARTS_KEY = new NamespacedKey("enchantments", "feast_hearts");

    private final Map<UUID, Map<Material, Long>> menus = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "saturation_bonus";
    }

    @Override
    public String getName() {
        return "Селянська трапеза";
    }

    @Override
    public String getDescription() {
        return "Чим різноманітніше ви харчуєтесь, тим ситніше живете: голод дошкуляє менше, а тіло міцнішає";
    }

    @Override
    public int getMaxLevel() {
        return 3;
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
            "menu-memory-minutes", 10,
            "memory-per-level", 5,
            "dishes-for-hunger", 3,
            "dishes-for-hearts", 5,
            "dishes-for-regen", 7,
            "hunger-slowdown", 0.25,
            "bonus-hearts", 2.0,
            "regen-duration", 8
        );
    }

    @Override
    public double getEnchantingTableChance() {
        return 0.08;
    }

    @Override
    public int getWeight() {
        return 12;
    }

    @Override
    public double getChestLootChance() {
        return 0.07;
    }

    @Override
    public List<String> getLootChests() {
        return List.of(
            "minecraft:chests/village/village_plains_house",
            "minecraft:chests/village/village_desert_house",
            "minecraft:chests/village/village_savanna_house",
            "minecraft:chests/village/village_taiga_house"
        );
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of("FARMER", Map.of("villager-level", 2, "min-level", 1, "max-level", 1, "price", 14));
    }

    @Override
    public int getAnvilBaseCost() {
        return 4;
    }

    @Override
    public int getAnvilCostPerLevel() {
        return 2;
    }

    @Override
    protected void onEnable() {
        Bukkit.getScheduler().runTaskTimer(getPlugin(), () -> {
            if (!isMechanicEnabled()) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                int level = levelOnPlayer(player);
                if (level == 0) {
                    applyHearts(player, 0);
                    continue;
                }
                applyHearts(player, dishes(player) >= getInt("dishes-for-hearts") ? getDouble("bonus-hearts") : 0);
            }
        }, 200L, 200L);
    }

    // ==========================================
    //   ТРАПЕЗА
    // ==========================================

    @EventHandler(ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        if (!isMechanicEnabled()) return;

        Player player = e.getPlayer();
        int level = levelOnPlayer(player);
        if (level == 0) return;

        Material dish = e.getItem().getType();
        if (!dish.isEdible()) return;

        Map<Material, Long> menu = menus.computeIfAbsent(player.getUniqueId(), id -> new EnumMap<>(Material.class));
        long now = System.currentTimeMillis();
        boolean isNew = !menu.containsKey(dish) || menu.get(dish) <= now;

        long memory = (getInt("menu-memory-minutes") + (long) getInt("memory-per-level") * (level - 1)) * 60_000L;
        menu.put(dish, now + memory);

        int count = dishes(player);
        int forHearts = getInt("dishes-for-hearts");
        int forRegen = getInt("dishes-for-regen");

        if (isNew) {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.4f, 1.4f + count * 0.05f);
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.2, 0), 6, 0.3, 0.3, 0.3, 0.01);
            player.sendActionBar(Component.text("§a✦ Нова страва — меню: §2" + count + "§8/" + forRegen));
        } else {
            player.sendActionBar(Component.text("§8Це вже було в меню — §7" + count + "§8/" + forRegen));
        }

        applyHearts(player, count >= forHearts ? getDouble("bonus-hearts") : 0);

        if (count >= forRegen) {
            int seconds = Math.max(1, getInt("regen-duration"));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, seconds * 20, 0, true, true, true));
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.6f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (!isMechanicEnabled()) return;
        if (!(e.getEntity() instanceof Player player)) return;
        if (e.getFoodLevel() >= player.getFoodLevel()) return; // голод не падає
        if (levelOnPlayer(player) == 0) return;
        if (dishes(player) < getInt("dishes-for-hunger")) return;

        double slowdown = Math.max(0.0, Math.min(0.9, getDouble("hunger-slowdown")));
        if (ThreadLocalRandom.current().nextDouble() < slowdown) e.setCancelled(true);
    }

    // ==========================================
    //   МЕНЮ ТА СЕРЦЯ
    // ==========================================

    private int dishes(Player player) {
        Map<Material, Long> menu = menus.get(player.getUniqueId());
        if (menu == null) return 0;

        long now = System.currentTimeMillis();
        menu.values().removeIf(expiry -> expiry <= now);
        return menu.size();
    }

    private void applyHearts(Player player, double hearts) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) return;

        double target = hearts * 2.0;
        double current = 0;

        for (AttributeModifier modifier : new ArrayList<>(attribute.getModifiers())) {
            if (!HEARTS_KEY.equals(modifier.getKey())) continue;
            current = modifier.getAmount();
            if (current == target) return;
            attribute.removeModifier(modifier);
        }

        if (target <= 0) {
            if (current > 0 && player.getHealth() > attribute.getValue()) {
                player.setHealth(attribute.getValue());
            }
            return;
        }

        attribute.addModifier(new AttributeModifier(HEARTS_KEY, target,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));

        if (current <= 0) {
            player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1.4, 0), 6, 0.4, 0.4, 0.4, 0.01);
        }
    }

    // ==========================================
    //   ОЧИЩЕННЯ
    // ==========================================

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        menus.remove(e.getEntity().getUniqueId());
        applyHearts(e.getEntity(), 0);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        menus.remove(e.getPlayer().getUniqueId());
        applyHearts(e.getPlayer(), 0);
    }
}