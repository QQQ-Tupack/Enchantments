package me.enchantments.listeners;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;

import me.enchantments.main.Mechanic;

public final class GrindstoneListener implements Listener {

    @EventHandler
    public void onPrepare(PrepareGrindstoneEvent e) {
        ItemStack result = e.getResult();
        if (result == null || result.getType().isAir()) return;

        GrindstoneInventory inventory = e.getInventory();
        ItemStack upper = inventory.getUpperItem();
        ItemStack lower = inventory.getLowerItem();

        ItemStack kept = null;
        for (Mechanic mechanic : Mechanic.getAll()) {
            if (mechanic.isGrindstoneRemovable()) continue;

            int level = Math.max(mechanic.levelOf(upper), mechanic.levelOf(lower));
            if (level == 0) continue;

            Enchantment enchantment = mechanic.enchantment();
            if (enchantment == null) continue;

            if (kept == null) kept = result.clone();
            kept.addUnsafeEnchantment(enchantment, level);
        }

        if (kept != null) e.setResult(kept);
    }
}