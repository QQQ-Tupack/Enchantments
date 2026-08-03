package me.enchantments.mechanics;

import me.enchantments.main.Enchantments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SoulboundMechanic extends Mechanic {

    /**
     * Порожній supported означає "усі предмети гри" — далі з них віднімається getExcludedItems().
     * Так не доводиться перелічувати кожен меч і кожен шолом окремо.
     */
    private static final List<Material> EXCLUDED = buildExcluded();

    private NamespacedKey storageKey;

    @Override
    public String getId() {
        return "soulbound";
    }

    @Override
    public String getName() {
        return "Прив'язка душі";
    }

    @Override
    public String getDescription() {
        return "Річ повертається до вас після смерті, але дорого платить за це власною міцністю";
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of();
    }

    @Override
    public List<Material> getExcludedItems() {
        return EXCLUDED;
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND, EnchantSlot.HEAD,
                       EnchantSlot.CHEST, EnchantSlot.LEGS, EnchantSlot.FEET);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "durability-cost-percent", 25,
            "break-on-empty-durability", false,
            "restore-message", true
        );
    }

    // ---- отримання: тільки скарб, зі столу не випадає ----

    @Override
    public boolean isEnchantingTableAllowed() {
        return false;
    }

    @Override
    public double getEnchantingTableChance() {
        return 0.0;
    }

    @Override
    public int getWeight() {
        return 2;
    }

    @Override
    public double getChestLootChance() {
        return 0.03;
    }

    @Override
    public List<String> getLootChests() {
        return List.of(
            "minecraft:chests/ancient_city",
            "minecraft:chests/end_city_treasure",
            "minecraft:chests/stronghold_library",
            "minecraft:chests/woodland_mansion"
        );
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of(
            "LIBRARIAN", Map.of("villager-level", 5, "min-level", 1, "max-level", 1, "price", 60),
            "CLERIC", Map.of("villager-level", 5, "min-level", 1, "max-level", 1, "price", 64)
        );
    }

    @Override
    public int getAnvilBaseCost() {
        return 20;
    }

    @Override
    public int getAnvilCostPerLevel() {
        return 8;
    }

    @Override
    protected void onEnable() {
        storageKey = new NamespacedKey(getPlugin(), "soulbound_storage");
    }

    // ==========================================
    //   ЗАБОРОНА НА ВИКЛЮЧЕНИХ ПРЕДМЕТАХ
    // ==========================================

    @EventHandler(ignoreCancelled = true)
    public void onAnvil(PrepareAnvilEvent e) {
        if (!isMechanicEnabled()) return;

        ItemStack result = e.getResult();
        if (result == null || levelOf(result) == 0) return;
        if (isItemAllowed(result.getType())) return;

        e.setResult(null);
    }

    // ==========================================
    //   СМЕРТЬ І ПОВЕРНЕННЯ
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        if (!isMechanicEnabled()) return;
        if (e.getKeepInventory()) return;

        Player player = e.getEntity();
        PlayerInventory inventory = player.getInventory();
        ItemStack[] saved = new ItemStack[inventory.getSize()];
        boolean any = false;

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            if (levelOf(item) == 0) continue;
            if (!isItemAllowed(item.getType())) continue; // виключений предмет просто випадає

            wear(item);
            if (item.getAmount() <= 0) {
                inventory.setItem(slot, null);
                continue;
            }

            saved[slot] = item.clone();
            any = true;

            for (Iterator<ItemStack> it = e.getDrops().iterator(); it.hasNext(); ) {
                ItemStack drop = it.next();
                if (drop != null && drop.isSimilar(item) && drop.getAmount() == item.getAmount()) {
                    it.remove();
                    break;
                }
            }
            inventory.setItem(slot, null);
        }

        if (!any) return;

        for (int i = 0; i < saved.length; i++) {
            if (saved[i] == null) saved[i] = new ItemStack(Material.AIR);
        }
        player.getPersistentDataContainer().set(storageKey, PersistentDataType.BYTE_ARRAY,
                ItemStack.serializeItemsAsBytes(saved));
    }

    /** Ціна повернення: річ помітно зношується, тож Soulbound не робить спорядження вічним. */
    private void wear(ItemStack item) {
        int percent = getInt("durability-cost-percent");
        if (percent <= 0) return;
        if (!(item.getItemMeta() instanceof Damageable damageable)) return;

        int max = item.getType().getMaxDurability();
        if (max <= 0) return;

        int cost = Math.max(1, max * percent / 100);
        int damage = damageable.getDamage() + cost;

        if (damage >= max) {
            if (getBoolean("break-on-empty-durability")) {
                item.setAmount(0);
                return;
            }
            damage = max - 1; // лишаємо річ живою на останньому вдиху
        }

        damageable.setDamage(damage);
        item.setItemMeta(damageable);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTaskLater(Enchantments.getInstance(), () -> restore(e.getPlayer()), 1L);
    }

    /** Якщо гравець вийшов, не відродившись, речі чекають у PDC до наступного входу. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(Enchantments.getInstance(), () -> {
            if (!e.getPlayer().isDead()) restore(e.getPlayer());
        }, 20L);
    }

    private void restore(Player player) {
        byte[] data = player.getPersistentDataContainer().get(storageKey, PersistentDataType.BYTE_ARRAY);
        if (data == null) return;
        player.getPersistentDataContainer().remove(storageKey);

        ItemStack[] saved;
        try {
            saved = ItemStack.deserializeItemsFromBytes(data);
        } catch (Exception ex) {
            getPlugin().getLogger().warning("Не вдалось відновити Soulbound-речі гравця " + player.getName());
            return;
        }

        PlayerInventory inventory = player.getInventory();
        List<ItemStack> leftovers = new ArrayList<>();
        int restored = 0;

        for (int slot = 0; slot < saved.length && slot < inventory.getSize(); slot++) {
            ItemStack item = saved[slot];
            if (item == null || item.getType().isAir()) continue;

            ItemStack occupying = inventory.getItem(slot);
            if (occupying == null || occupying.getType().isAir()) {
                inventory.setItem(slot, item);
            } else {
                leftovers.add(item);
            }
            restored++;
        }

        for (ItemStack leftover : leftovers) {
            for (ItemStack overflow : inventory.addItem(leftover).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
        }

        if (restored == 0) return;

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.6f);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.03);
        if (getBoolean("restore-message")) {
            player.sendMessage(Component.text("Прив'язані речі повернулись до вас (" + restored + ").", NamedTextColor.LIGHT_PURPLE));
        }
    }

    // ==========================================
    //   ЩО НЕ МОЖЕ БУТИ ПРИВ'ЯЗАНИМ
    // ==========================================

    private static List<Material> buildExcluded() {
        Set<Material> excluded = new LinkedHashSet<>();

        // одноразові рятівники та все, що споживається — інакше це нескінченний тотем
        excluded.add(Material.TOTEM_OF_UNDYING);
        excluded.add(Material.ENDER_PEARL);
        excluded.add(Material.ENDER_EYE);
        excluded.add(Material.CHORUS_FRUIT);
        excluded.add(Material.EXPERIENCE_BOTTLE);
        excluded.add(Material.FIREWORK_ROCKET);
        excluded.add(Material.SNOWBALL);
        excluded.add(Material.EGG);
        excluded.add(Material.POTION);
        excluded.add(Material.SPLASH_POTION);
        excluded.add(Material.LINGERING_POTION);

        Material windCharge = Material.getMaterial("WIND_CHARGE");
        if (windCharge != null) excluded.add(windCharge);

        for (Material material : Material.values()) {
            if (material.isLegacy() || !material.isItem() || material.isAir()) continue;
            String name = material.name();

            // будь-яка їжа
            if (material.isEdible()) {
                excluded.add(material);
                continue;
            }
            // яйця прикликання
            if (name.endsWith("_SPAWN_EGG")) {
                excluded.add(material);
                continue;
            }
            // контейнери: шалкер зі всією базою всередині не повинен переживати смерть
            if (name.endsWith("SHULKER_BOX") || name.endsWith("BUNDLE")) {
                excluded.add(material);
            }
        }

        return List.copyOf(excluded);
    }
}
