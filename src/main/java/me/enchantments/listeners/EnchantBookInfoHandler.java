package me.enchantments.listeners;

import io.papermc.paper.registry.TypedKey;
import me.enchantments.main.Enchantments;
import me.enchantments.main.Mechanic;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class EnchantBookInfoHandler implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String BASE = "book-info.";
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
    private static final int MAX_ITEMS = 14;
    /** Рендеряться моделлю, пласкої текстури в атласі items не мають — показуємо назвою. */
    private static final Set<Material> NO_ICON = EnumSet.of(Material.SHIELD);

    private final JavaPlugin plugin = Enchantments.getInstance();
    private final Map<UUID, Long> hintCooldowns = new ConcurrentHashMap<>();

    /**
     * Дані однієї книги. Кастомна механіка й ванільне зачарування заповнюють один
     * і той самий набір, тому сторінки в конфігу спільні, а резолвер — один.
     * null означає "джерело такого не має" і рендериться як empty.
     */
    private record Info(Component name, Component description, String id, int level, int maxLevel, int weight,
                        String anvilCost, String tableChance, String chestChance, Boolean grindstoneRemovable,
                        Boolean treasure, Boolean villagerTrade, List<String> items, List<String> excludedItems,
                        List<String> incompatible, List<String> lootChests, List<String> professions) {
    }

    // ---------- Події ----------

    /**
     * Без ignoreCancelled: Bukkit створює PlayerInteractEvent з useInteractedBlock = DENY,
     * якщо блока немає (RIGHT_CLICK_AIR), а isCancelled() читає саме це поле — тому клік
     * по повітрю приходив "скасованим" і листенер не викликався.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        if (event.useItemInHand() == Event.Result.DENY) return;

        Optional<Info> info = read(event.getItem());
        if (info.isEmpty()) return;

        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);

        Player player = event.getPlayer();
        // openBook усередині обробки взаємодії інколи губиться клієнтом — відкриваємо наступним тіком
        plugin.getServer().getScheduler().runTask(plugin, () -> open(player, info.get()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) offerHint(player, event.getItem().getItemStack());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) offerHint(player, event.getOldCursor());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (onCooldown(player)) return; // щоб не сканувати інвентар щокліку

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (ItemStack item : player.getInventory().getContents()) {
                if (read(item).isPresent()) {
                    offerHint(player, item);
                    return;
                }
            }
        });
    }

    // ---------- Підказка ----------

    private void offerHint(Player player, ItemStack item) {
        if (onCooldown(player) || read(item).isEmpty()) return;
        hintCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        player.sendMessage(MM.deserialize(text("hint.message", "")));
    }

    private boolean onCooldown(Player player) {
        Long last = hintCooldowns.get(player.getUniqueId());
        long cooldown = config().getLong(BASE + "hint.cooldown-minutes", 30) * 60_000L;
        return last != null && System.currentTimeMillis() - last < cooldown;
    }

    // ---------- Книга ----------

    private void open(Player player, Info info) {
        List<String> pages = config().getStringList(BASE + "pages");
        if (pages.isEmpty()) return;

        TagResolver resolver = resolver(info);
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        book.editMeta(BookMeta.class, meta -> {
            meta.title(MM.deserialize(text("title", "<name>"), resolver));
            meta.author(MM.deserialize(text("author", "QQQ-CRAFT")));
            meta.addPages(pages.stream().map(page -> MM.deserialize(page, resolver)).toArray(Component[]::new));
        });

        player.openBook(book);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
    }

    private TagResolver resolver(Info info) {
        String all = text("label-all-items", "Усі предмети");
        return TagResolver.resolver(
                Placeholder.component("name", info.name()),
                Placeholder.component("description", info.description()),
                Placeholder.unparsed("id", info.id()),
                Placeholder.unparsed("level", roman(info.level())),
                Placeholder.unparsed("max-level", roman(info.maxLevel())),
                Placeholder.unparsed("weight", String.valueOf(info.weight())),
                Placeholder.unparsed("anvil-cost", info.anvilCost()),
                Placeholder.parsed("table-chance", info.tableChance()),
                Placeholder.parsed("chest-chance", info.chestChance()),
                Placeholder.parsed("grindstone-removable", flag(info.grindstoneRemovable())),
                Placeholder.parsed("treasure", flag(info.treasure())),
                Placeholder.parsed("villager-trade", flag(info.villagerTrade())),
                Placeholder.parsed("items", info.items() == null ? all : icons(info.items())),
                Placeholder.parsed("excluded-items", names(info.excludedItems())),
                Placeholder.parsed("incompatible", list(info.incompatible())),
                Placeholder.parsed("loot-chests", list(info.lootChests())),
                Placeholder.parsed("villager-professions", list(info.professions()))
        );
    }

    // ---------- Джерела даних ----------

    /** Читає предмет один раз: спершу кастомна механіка, потім ванільне зачарування. */
    private Optional<Info> read(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) return Optional.empty();
        if (!(item.getItemMeta() instanceof EnchantmentStorageMeta meta)) return Optional.empty();

        Map<Enchantment, Integer> stored = meta.getStoredEnchants();
        for (Mechanic mechanic : Mechanic.getAll()) {
            Integer level = stored.get(mechanic.enchantment());
            if (level != null) return Optional.of(info(mechanic, level));
        }
        if (!config().getBoolean(BASE + "include-vanilla", true)) return Optional.empty();
        return stored.entrySet().stream().findFirst().map(entry -> info(entry.getKey(), entry.getValue()));
    }

    private Info info(Mechanic mechanic, int level) {
        String key = "enchantment.enchantments." + mechanic.getId();
        String off = yesNo(false);
        List<Material> supported = mechanic.getSupportedItems();

        return new Info(
                Component.translatable(key).fallback(mechanic.getName()),
                Component.translatable(key + ".description").fallback(mechanic.getDescription()),
                mechanic.getId(), level, mechanic.getMaxLevel(), mechanic.weight(),
                mechanic.getAnvilBaseCost() + " + " + mechanic.getAnvilCostPerLevel() + "/lvl",
                mechanic.isEnchantingTableAllowed() ? percent(mechanic.getEnchantingTableChance()) : off,
                mechanic.isChestLootAllowed() ? percent(mechanic.getChestLootChance()) : off,
                mechanic.isGrindstoneRemovable(), null, mechanic.isVillagerTradeAllowed(),
                supported == null || supported.isEmpty() ? null : ids(supported),
                ids(mechanic.getExcludedItems()),
                mechanic.exclusiveWith().stream()
                        .map(id -> langOr("enchantment.enchantments." + id, pretty(id))).toList(),
                mechanic.getLootChests().stream().map(EnchantBookInfoHandler::lootChest).toList(),
                mechanic.getVillagerTrades().keySet().stream()
                        .map(String::valueOf).map(EnchantBookInfoHandler::profession).toList()
        );
    }

    private Info info(Enchantment enchantment, int level) {
        NamespacedKey key = enchantment.getKey();
        String named = text("names." + key.getKey(), null);
        Component name = named != null && enchantment.description() instanceof TranslatableComponent translatable
                ? translatable.fallback(named)
                : enchantment.description();

        List<String> items = new ArrayList<>();
        for (TypedKey<ItemType> item : enchantment.getSupportedItems()) items.add(item.key().value());
        List<String> incompatible = new ArrayList<>();
        for (TypedKey<Enchantment> exclusive : enchantment.getExclusiveWith()) {
            incompatible.add(lang("enchantment." + exclusive.key().namespace() + "." + exclusive.key().value()));
        }

        return new Info(name,
                Component.translatable("enchantment." + key.getNamespace() + "." + key.getKey() + ".description")
                        .fallback(text("descriptions." + key.getKey(), text("unknown-description", ""))),
                key.toString(), level, enchantment.getMaxLevel(), enchantment.getWeight(),
                String.valueOf(enchantment.getAnvilCost()),
                yesNo(enchantment.isDiscoverable()), empty(),
                null, enchantment.isTreasure(), enchantment.isTradeable(),
                items, List.of(), incompatible, List.of(), List.of()
        );
    }

    // ---------- Конфіг ----------

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    private String text(String path, String fallback) {
        return config().getString(BASE + path, fallback);
    }

    private String empty() {
        return text("empty", "<dark_gray>-");
    }

    private String yesNo(boolean value) {
        return value ? text("label-yes", "так") : text("label-no", "ні");
    }

    private String flag(Boolean value) {
        return value == null ? empty() : yesNo(value);
    }

    private String percent(double chance) {
        return String.format("%.1f%%", chance * 100);
    }

    private String list(List<String> values) {
        if (values == null || values.isEmpty()) return empty();
        String format = text("list-item", "- {value}");
        return values.stream().map(value -> format.replace("{value}", value)).collect(Collectors.joining("\n"));
    }

    /** Довгі списки обрізаються, бо сторінка книги — 14 рядків. */
    private String names(List<String> itemIds) {
        List<String> names = new ArrayList<>(itemIds.stream()
                .limit(MAX_ITEMS).map(EnchantBookInfoHandler::itemName).toList());
        if (itemIds.size() > MAX_ITEMS) names.add("...");
        return list(names);
    }

    /** Ряд іконок в один рядок; предмети без пласкої текстури підставляються назвою. */
    private String icons(List<String> itemIds) {
        if (itemIds.isEmpty()) return empty();
        String row = itemIds.stream().limit(MAX_ITEMS)
                .map(EnchantBookInfoHandler::icon).collect(Collectors.joining(" "));
        return itemIds.size() > MAX_ITEMS ? row + " ..." : row;
    }

    // ---------- Теги ----------

    private static String icon(String itemId) {
        Material material = Material.matchMaterial(itemId);
        if (material == null || NO_ICON.contains(material)) return itemName(itemId);
        return material.isBlock()
                ? "<white><sprite:blocks:block/" + itemId + ">"
                : "<white><sprite:items:item/" + itemId + ">";
    }

    /** Material сам знає свій ключ — item.* чи block.*, залежно від предмета. */
    private static String itemName(String itemId) {
        Material material = Material.matchMaterial(itemId);
        return lang(material != null ? material.translationKey() : "item.minecraft." + itemId);
    }

    private static String lang(String key) {
        return "<lang:" + key + ">";
    }

    private static String langOr(String key, String fallback) {
        return "<lang_or:" + key + ":'" + fallback + "'>";
    }

    /** Лут-таблиці не мають перекладу в грі, тому власний ключ. */
    private static String lootChest(String lootTable) {
        String id = lootTable.substring(lootTable.lastIndexOf('/') + 1);
        return langOr("enchantments.chest." + id, pretty(id));
    }

    private static String profession(String profession) {
        String id = profession.toLowerCase(Locale.ROOT);
        return langOr("entity.minecraft.villager." + id, pretty(id));
    }

    private static List<String> ids(List<Material> materials) {
        return materials.stream().map(material -> material.getKey().getKey()).toList();
    }

    private static String pretty(String id) {
        String text = id.replace('_', ' ');
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String roman(int level) {
        return level > 0 && level < ROMAN.length ? ROMAN[level] : String.valueOf(level);
    }
}