package me.enchantments.listeners;

import me.enchantments.main.Enchantments;
import me.enchantments.main.Mechanic;

// EnchantMeta removed — use Mechanic.hideEnchantmentGlint()
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.Set;

public final class GlintUpdateListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) schedule(p);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p) schedule(p);
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        schedule(e.getPlayer());
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        schedule(e.getPlayer());
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) schedule(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        schedule(e.getPlayer());
    }

    private void schedule(Player p) {
        Bukkit.getScheduler().runTask(Enchantments.getInstance(), () -> updateGlint(p));
    }

    private void updateGlint(Player p) {
        Set<String> hideKeys = hideGlintKeys();
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;
            if (!item.hasItemMeta()) continue;

            boolean shouldApply = false;

            // Проверяем актуальные зачарования предмета на момент вызова метода
            for (org.bukkit.enchantments.Enchantment ench : item.getItemMeta().getEnchants().keySet()) {
                String keyString = ench.getKey().toString(); // Например: "enchantments:double_jump" или "minecraft:sharpness"

                // Проверяем, содержит ли ключ "enchantments:"
                if (keyString.contains("enchantments:")) {
                    shouldApply = true;
                    break; // Нашли подходящее зачарование, дальше можно не проверять этот предмет
                }
            }

            // Если условие выполнено, применяем метод
            if (shouldApply) {
                applyGlint(item, hideKeys);
            }
        }
    }

    private void applyGlint(ItemStack item, Set<String> hideKeys) {
        if (item == null || item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        Map<Enchantment, Integer> enchants = meta.getEnchants();
        boolean hide = !enchants.isEmpty()
                && enchants.keySet().stream().allMatch(ench -> hideKeys.contains(ench.getKey().toString()));

        Boolean current = meta.hasEnchantmentGlintOverride() ? meta.getEnchantmentGlintOverride() : null;
        Boolean target = hide ? Boolean.FALSE : null;
        if (!java.util.Objects.equals(current, target)) {
            meta.setEnchantmentGlintOverride(target);
            item.setItemMeta(meta);
        }
    }

    private Set<String> hideGlintKeys() {
        Set<String> keys = new java.util.HashSet<>();
        for (Mechanic m : Mechanic.getAll()) {
            if (m.hideEnchantmentGlint()) keys.add("enchantments:" + m.getId().toLowerCase());
        }
        return keys;
    }
}