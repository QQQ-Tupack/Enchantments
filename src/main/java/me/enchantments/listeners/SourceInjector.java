package me.enchantments.listeners;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.enchantments.mechanics.Mechanic;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SourceInjector implements Listener {

    private final Random random = new Random();
    private final Set<String> missingWarned = ConcurrentHashMap.newKeySet();

    @EventHandler
    public void onLoot(LootGenerateEvent event) {
        if (event.getLootTable() == null) return;

        NamespacedKey key = event.getLootTable().getKey();
        if (!key.getKey().startsWith("chests/")) return;   // тільки скрині, не риболовля/дропи блоків
        String table = key.toString();

        for (Mechanic mechanic : Mechanic.getAll()) {
            if (!mechanic.isMechanicEnabled()) continue;

            FileConfiguration config = mechanic.getConfig();
            if (config == null) continue;
            if (!config.getBoolean("obtaining.chests.enabled", true)) continue;

            List<String> chests = config.getStringList("obtaining.chests.list");
            if (!chests.isEmpty() && !chests.contains(table)) continue;   // порожній = будь-яка скриня

            double chance = config.getDouble("obtaining.chests.chance", 0.05);
            if (random.nextDouble() >= chance) continue;

            ItemStack book = enchantedBook(mechanic, randomLevel(1, mechanic.getMaxLevel()));
            if (book != null) event.getLoot().add(book);
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

                String tradeProfession = trade.getString("profession", tradeKey);
                if (!tradeProfession.equalsIgnoreCase(profession)) continue;

                int requiredVillagerLevel = trade.getInt("villager-level", 1);
                if (villager.getVillagerLevel() < requiredVillagerLevel) continue;

                int minLevel = trade.getInt("min-level", 1);
                int maxLevel = trade.getInt("max-level", mechanic.getMaxLevel());
                int price = Math.max(1, Math.min(64, trade.getInt("price", 5)));

                ItemStack book = enchantedBook(mechanic, randomLevel(minLevel, maxLevel));
                if (book == null) return;

                MerchantRecipe recipe = new MerchantRecipe(book, 0, 3, true);
                recipe.addIngredient(new ItemStack(Material.EMERALD, price));
                event.setRecipe(recipe);
                return;
            }
        }
    }

    private int randomLevel(int min, int max) {
        if (min < 1) min = 1;
        return min >= max ? min : min + random.nextInt(max - min + 1);
    }

    private ItemStack enchantedBook(Mechanic mechanic, int level) {
        String id = mechanic.getId().toLowerCase();

        Enchantment ench = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT)
                .get(new NamespacedKey("enchantments", id));

        if (ench == null) {
            if (missingWarned.add(id)) {
                Mechanic.getPlugin().getLogger().warning(
                        "Зачарування 'enchantments:" + id + "' відсутнє в реєстрі — датапак не завантажений. "
                                + "Перевірте /datapack list і перезапустіть сервер.");
            }
            return null;
        }

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        if (meta == null) return null;

        meta.addStoredEnchant(ench, Math.max(1, Math.min(level, mechanic.getMaxLevel())), true);
        book.setItemMeta(meta);
        return book;
    }
}