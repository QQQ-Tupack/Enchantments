package me.enchantments.mechanics;

import me.enchantments.utils.EnchantMeta;
import me.enchantments.utils.EnchantSlot;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

public abstract class Mechanic implements Listener {

    protected final EnchantMeta meta;
    protected final Enchantment enchantment;

    protected Mechanic(EnchantMeta meta) {
        this.meta = meta;
        this.enchantment = Registry.ENCHANTMENT.get(NamespacedKey.fromString("enchantments:" + meta.key()));
    }

    public EnchantMeta meta() {
        return meta;
    }

    public String commandName() {
        return null;
    }

    protected int levelOf(ItemStack item) {
        return item == null || enchantment == null ? 0 : item.getEnchantmentLevel(enchantment);
    }

    protected int levelOnPlayer(Player player) {
        for (EnchantSlot slot : meta.slots()) {
            int level = levelOf(player.getInventory().getItem(slot.bukkit()));
            if (level > 0) return level;
        }
        return 0;
    }
}

record Scaled(double base, double perLevel) {
    double at(int level) {
        return base + (level - 1) * perLevel;
    }
}
