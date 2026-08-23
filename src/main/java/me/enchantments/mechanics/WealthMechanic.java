package me.enchantments.mechanics;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.ItemStack;
import me.enchantments.main.Mechanic;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WealthMechanic extends Mechanic {

    // цінність ОДНОГО предмета; внесок типу = value * sqrt(кількість)
    private static final Map<Material, Double> VALUABLES = Map.ofEntries(
        Map.entry(Material.RAW_GOLD, 0.8),
        Map.entry(Material.GOLD_INGOT, 1.0),
        Map.entry(Material.GOLD_BLOCK, 9.0),
        Map.entry(Material.AMETHYST_SHARD, 0.5),
        Map.entry(Material.EMERALD, 2.0),
        Map.entry(Material.EMERALD_BLOCK, 18.0),
        Map.entry(Material.DIAMOND, 4.0),
        Map.entry(Material.DIAMOND_BLOCK, 36.0),
        Map.entry(Material.ANCIENT_DEBRIS, 6.0),
        Map.entry(Material.NETHERITE_SCRAP, 5.0),
        Map.entry(Material.NETHERITE_INGOT, 20.0),
        Map.entry(Material.NETHERITE_BLOCK, 180.0),
        Map.entry(Material.ECHO_SHARD, 8.0),
        Map.entry(Material.HEART_OF_THE_SEA, 15.0),
        Map.entry(Material.GOLDEN_APPLE, 3.0),
        Map.entry(Material.ENCHANTED_GOLDEN_APPLE, 25.0),
        Map.entry(Material.TOTEM_OF_UNDYING, 12.0),
        Map.entry(Material.NETHER_STAR, 40.0),
        Map.entry(Material.DRAGON_EGG, 60.0)
    );

    private final Map<UUID, Long> soundThrottle = new HashMap<>();
    private final Map<UUID, Integer> pendingBonus = new HashMap<>();
    private final Map<UUID, Double> expRemainder = new HashMap<>();

    @Override public String getId() { return "wealth"; }
    @Override public String getName() { return "Статок"; }
    @Override public String getDescription() { return "Чим більше цінностей власник носить із собою, тим більше досвіду отримує з усіх джерел."; }
    @Override public int getMaxLevel() { return 3; }
    @Override public List<Material> getSupportedItems() { return List.of(Material.TOTEM_OF_UNDYING); }
    @Override public List<EnchantSlot> getSlots() { return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND, EnchantSlot.HEAD); }
    @Override public double getChestLootChance() { return 0.06; }
    @Override public List<String> getLootChests() { return List.of("minecraft:chests/village/village_cartographer", "minecraft:chests/simple_dungeon"); }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of("LIBRARIAN", Map.of("level", 3, "min-emeralds", 14, "max-emeralds", 28));
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "bonus-per-point", 0.005,
            "max-bonus-per-level", 0.35,
            "sound-throttle-millis", 700
        );
    }

    @Override
    protected void onDisable() {
        soundThrottle.clear();
        pendingBonus.clear();
        expRemainder.clear();
    }

    @EventHandler
    public void onExpChange(PlayerExpChangeEvent event) {
        if (!isMechanicEnabled()) return;
        Player player = event.getPlayer();
        int amount = event.getAmount();
        if (amount <= 0) return;
        int level = levelOnPlayer(player);
        if (level <= 0) return;

        double points = wealthPoints(player);
        if (points <= 0) return;

        // корінь уже врахований у балах; зверху — стеля, щоб гори ресурсу нічого не давали
        double maxPerLevel = getDouble("max-bonus-per-level");
        double perLevel = Math.min(maxPerLevel, points * getDouble("bonus-per-point"));
        double bonusFraction = perLevel * level;

        UUID id = player.getUniqueId();
        // залишок переноситься, щоб бонус працював і на дрібних краплях досвіду
        double exact = amount * bonusFraction + expRemainder.getOrDefault(id, 0.0);
        int bonus = (int) Math.floor(exact);
        expRemainder.put(id, exact - bonus);
        if (bonus <= 0) return;

        event.setAmount(amount + bonus);
        int pending = pendingBonus.merge(id, bonus, Integer::sum);

        long now = System.currentTimeMillis();
        Long last = soundThrottle.get(id);
        if (last != null && now - last < getInt("sound-throttle-millis")) return;
        soundThrottle.put(id, now);
        pendingBonus.remove(id);
        feedback(player, pending, bonusFraction, perLevel / maxPerLevel);
    }

    // сумарна «цінність» інвентаря: value * sqrt(кількість) по кожному типу окремо
    private double wealthPoints(Player player) {
        Map<Material, Integer> counts = new HashMap<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (!VALUABLES.containsKey(item.getType())) continue;
            counts.merge(item.getType(), item.getAmount(), Integer::sum);
        }
        double points = 0.0;
        for (Map.Entry<Material, Integer> entry : counts.entrySet()) {
            points += VALUABLES.get(entry.getKey()) * Math.sqrt(entry.getValue());
        }
        return points;
    }

    // fill — наскільки гравець близький до стелі бонусу (0..1): від нього залежить тон і густина частинок
    private void feedback(Player player, int bonus, double bonusFraction, double fill) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.6f);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, (float) (1.0 + 0.8 * fill));
        player.getWorld().spawnParticle(
            Particle.ITEM,
            player.getLocation().add(0.0, 1.0, 0.0),
            4 + (int) Math.round(fill * 8),
            0.25, 0.4, 0.25, 0.02,
            new ItemStack(Material.GOLD_NUGGET)
        );
        player.sendActionBar(Component.text(
            "✦ Статок   +" + bonus + " досвіду   (+" + Math.round(bonusFraction * 100) + "%)",
            NamedTextColor.GOLD
        ));
    }
}