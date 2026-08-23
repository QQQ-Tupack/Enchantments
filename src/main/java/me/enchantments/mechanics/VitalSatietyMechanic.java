package me.enchantments.mechanics;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import me.enchantments.main.Mechanic;

import java.util.List;
import java.util.Map;

public final class VitalSatietyMechanic extends Mechanic {

    @Override public String getId() { return "vital_satiety"; }
    @Override public String getName() { return "Гарний апетит"; }
    @Override public String getDescription() { return "Якщо їжа заповнює голод до кінця — коротка регенерація"; }
    @Override public int getMaxLevel() { return 1; }
    @Override public List<Material> getSupportedItems() { return List.of(Material.TOTEM_OF_UNDYING); }
    @Override public List<EnchantSlot> getSlots() { return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND, EnchantSlot.HEAD); }
    @Override public double getEnchantingTableChance() { return 0.07; }
    @Override public double getChestLootChance() { return 0.06; }

    @Override
    public List<String> getLootChests() {
        return List.of(
            "minecraft:chests/village/village_butcher",
            "minecraft:chests/village/village_temple",
            "minecraft:chests/village/village_plains_house",
            "minecraft:chests/igloo_chest"
        );
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of(
            "BUTCHER", Map.of("villager-level", 3, "min-level", 1, "max-level", 1, "price", 18),
            "FARMER", Map.of("villager-level", 4, "min-level", 1, "max-level", 1, "price", 22)
        );
    }

    @Override public int getAnvilBaseCost() { return 6; }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of("regen-duration", 6, "cooldown", 90);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        if (!isMechanicEnabled()) return;
        Player player = e.getPlayer();
        if (levelOnPlayer(player) <= 0) return;
        if (player.getFoodLevel() >= 20) return;

        ItemStack item = wornItem(player);
        if (item == null || !ready(player, item)) return;

        Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
            if (player.getFoodLevel() < 20) return;
            cooldown(player, item, getInt("cooldown") * 20);

            int seconds = Math.max(1, getInt("regen-duration"));
            // ambient=false — яскраві ванільні завитки ефекту, а не приглушені «маякові»
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, seconds * 20, 0, false, true, true));

            // Сплеск сердець по тілу — та сама подача, що у ванільного розмноження тварин
            particles(player.getLocation().add(0, 1.0, 0), Particle.HEART, 7, 0.45);
        }, 1L);
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
}