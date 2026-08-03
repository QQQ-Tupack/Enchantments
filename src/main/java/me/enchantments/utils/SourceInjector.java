package me.enchantments.utils;

import me.enchantments.mechanics.Mechanic;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
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
        String table = event.getLootTable().getKey().toString();
        for (Mechanic m : EnchantmentRegistry.mechanics()) {
            for (EnchantMeta.LootSource source : m.meta().sources().lootTables()) {
                if (!source.table().equals(table)) continue;
                if (random.nextDouble() > source.chance()) continue;
                event.getLoot().add(enchantedBook(m.meta().key(), randomLevel(source.minLevel(), source.maxLevel())));
            }
        }
    }

    @EventHandler
    public void onTrade(VillagerAcquireTradeEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        String profession = villager.getProfession().getKey().getKey();

        for (Mechanic m : EnchantmentRegistry.mechanics()) {
            for (EnchantMeta.TradeSource trade : m.meta().sources().trades()) {
                if (!trade.profession().equalsIgnoreCase(profession)) continue;
                if (villager.getVillagerLevel() < trade.villagerLevel()) continue;

                int level = randomLevel(trade.minLevel(), trade.maxLevel());
                MerchantRecipe recipe = new MerchantRecipe(enchantedBook(m.meta().key(), level), 0, 3, true);
                recipe.addIngredient(new ItemStack(org.bukkit.Material.EMERALD, trade.price()));
                event.setRecipe(recipe);
                return;
            }
        }
    }

    private int randomLevel(int min, int max) {
        return min >= max ? min : min + random.nextInt(max - min + 1);
    }

    private ItemStack enchantedBook(String key, int level) {
        Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.fromString("enchantments:" + key));
        ItemStack book = new ItemStack(org.bukkit.Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(ench, level, true);
        book.setItemMeta(meta);
        return book;
    }
}
