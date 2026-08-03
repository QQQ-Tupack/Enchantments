package me.enchantments.listeners;

import me.enchantments.mechanics.Mechanic;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.Random;

public final class SourceInjector implements Listener {

    private final Random random = new Random();

    @EventHandler
    public void onLoot(LootGenerateEvent event) {
        if (event.getLootTable() == null) return;
        String table = event.getLootTable().getKey().toString();

        for (Mechanic mechanic : Mechanic.getAll()) {
            if (!mechanic.isMechanicEnabled()) continue;

            FileConfiguration config = mechanic.getConfig();
            if (config == null) continue;

            if (!config.getBoolean("obtaining.chests.enabled", true)) continue;
            double chance = config.getDouble("obtaining.chests.chance", 0.05);
            if (random.nextDouble() > chance) continue;

            java.util.List<String> chests = config.getStringList("obtaining.chests.list");
            if (chests.contains(table)) {
                int level = randomLevel(1, mechanic.getMaxLevel());
                event.getLoot().add(enchantedBook(mechanic.getId(), level));
            }
        }
    }

    @EventHandler
    public void onTrade(VillagerAcquireTradeEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        String profession = villager.getProfession().getKey().getKey();

        for (Mechanic mechanic : Mechanic.getAll()) {
            if (!mechanic.isMechanicEnabled()) continue;

            FileConfiguration config = mechanic.getConfig();
            if (config == null) continue;

            if (!config.getBoolean("obtaining.villagers.enabled", true)) continue;

            ConfigurationSection tradesSec = config.getConfigurationSection("obtaining.villagers.trades");
            if (tradesSec == null) continue;

            for (String tradeKey : tradesSec.getKeys(false)) {
                ConfigurationSection trade = tradesSec.getConfigurationSection(tradeKey);
                if (trade == null) continue;

                String tradeProfession = trade.getString("profession", "");
                if (!tradeProfession.equalsIgnoreCase(profession)) continue;

                int requiredVillagerLevel = trade.getInt("villager-level", 1);
                if (villager.getVillagerLevel() < requiredVillagerLevel) continue;

                int minLevel = trade.getInt("min-level", 1);
                int maxLevel = trade.getInt("max-level", mechanic.getMaxLevel());
                int price = trade.getInt("price", 5);

                int level = randomLevel(minLevel, maxLevel);
                MerchantRecipe recipe = new MerchantRecipe(enchantedBook(mechanic.getId(), level), 0, 3, true);
                recipe.addIngredient(new ItemStack(org.bukkit.Material.EMERALD, price));
                event.setRecipe(recipe);
                return;
            }
        }
    }

    private int randomLevel(int min, int max) {
        return min >= max ? min : min + random.nextInt(max - min + 1);
    }

    private ItemStack enchantedBook(String mechanicId, int level) {
        Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.fromString("enchantments:" + mechanicId.toLowerCase()));
        if (ench == null) {
            ench = Enchantment.getByName(mechanicId.toUpperCase().replace("-", "_"));
        }
        
        ItemStack book = new ItemStack(org.bukkit.Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        if (meta != null && ench != null) {
            meta.addStoredEnchant(ench, level, true);
            book.setItemMeta(meta);
        }
        return book;
    }
}