package me.enchantments.mechanics;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import me.enchantments.main.Mechanic;

import java.util.List;
import java.util.Map;

/**
 * Довідковий приклад: тут перевизначено все, що взагалі можна перевизначити.
 * У реальній механіці лишають тільки потрібне — решта має робочі значення за замовчуванням.
 */
public class ExampleMechanic extends Mechanic {

    // ==========================================
    //   ОПИС ЗАЧАРУВАННЯ
    // ==========================================

    @Override
    public String getId() {
        return "example_enchant";
    }

    @Override
    public String getName() {
        return "Приклад Зачарування";
    }

    @Override
    public String getDescription() {
        return "Повністю завершений приклад: усі джерела отримання, тригери й утиліти.";
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    /** Порожній список = будь-який предмет. */
    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
                       Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
                       Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
                       Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE);
    }

    @Override
    public List<Material> getExcludedItems() {
        return List.of(Material.NETHERITE_AXE);
    }

    /** Слоти, у яких зачарування вважається активним. */
    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND);
    }

    // ==========================================
    //   ДЖЕРЕЛА ОТРИМАННЯ
    // ==========================================

    @Override
    public boolean isEnchantingTableAllowed() {
        return true;
    }

    @Override
    public double getEnchantingTableChance() {
        return 0.12;
    }

    @Override
    public boolean isChestLootAllowed() {
        return true;
    }

    @Override
    public double getChestLootChance() {
        return 0.05;
    }

    @Override
    public List<String> getLootChests() {
        return List.of(
            "minecraft:chests/end_city_treasure",
            "minecraft:chests/ancient_city"
        );
    }

    @Override
    public boolean isVillagerTradeAllowed() {
        return true;
    }

    @Override
    public Map<String, Map<String, Object>> getVillagerTrades() {
        return Map.of(
            "LIBRARIAN", Map.of(
                "villager-level", 3,
                "min-level", 1,
                "max-level", 3,
                "price", 24
            )
        );
    }

    // ==========================================
    //   ВАРТІСТЬ, ВАГА, СУМІСНІСТЬ
    // ==========================================

    @Override
    public int getAnvilBaseCost() {
        return 8;
    }

    @Override
    public int getAnvilCostPerLevel() {
        return 4;
    }

    /** Вага в таблиці зачарування: більша — випадає частіше. */
    @Override
    public int getWeight() {
        return 10;
    }

    @Override
    protected boolean hideEnchantmentGlintDefault() {
        return false;
    }

    /** false = зачарування не знімається в точилі. */
    @Override
    protected boolean grindstoneRemovableDefault() {
        return true;
    }

    /** Свій id, `minecraft:sharpness` або тег `#minecraft:exclusive_set/armor`. */
    @Override
    public List<String> getExclusiveWith() {
        return List.of("minecraft:sharpness");
    }

    /** Ванільні data-driven ефекти — вміст поля "effects" у json зачарування. */
    @Override
    public String getEffects() {
        return "{}";
    }

    // ==========================================
    //   НАЛАШТУВАННЯ (секція settings у конфігу)
    // ==========================================

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "cooldown-ticks", 100,
            "hunger-cost", 3,
            "power-multiplier", 1.5,
            "radius", 4.0,
            "announce-name", "Приклад",
            "affect-owner", false
        );
    }

    // ==========================================
    //   ОФОРМЛЕННЯ (спільне з MechanicUtils)
    // ==========================================

    @Override protected Particle activationParticle() { return Particle.ENCHANT; }
    @Override protected Sound activationChime() { return Sound.BLOCK_AMETHYST_BLOCK_CHIME; }
    @Override protected Sound activationCast() { return Sound.ENTITY_ILLUSIONER_CAST_SPELL; }
    @Override protected Sound denySound() { return Sound.BLOCK_NOTE_BLOCK_BASS; }

    /** Вікно між двома присіданнями для onDoubleSneak. */
    @Override protected long doubleSneakWindow() { return 400L; }

    // ==========================================
    //   ЖИТТЄВИЙ ЦИКЛ
    // ==========================================

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

    // ==========================================
    //   ТРИГЕРИ
    //   Рівень уже перевірено, механіка вже увімкнена.
    //   Sneak-варіанти без @Override падають у звичайні.
    // ==========================================

    /** ПКМ */
    @Override
    protected void onRightClick(PlayerInteractEvent event, Player player, int level) {
        cast(player, event.getItem(), level);
    }

    /** Шифт + ПКМ */
    @Override
    protected void onSneakRightClick(PlayerInteractEvent event, Player player, int level) {
        cast(player, event.getItem(), level);
    }

    /** ЛКМ */
    @Override
    protected void onLeftClick(PlayerInteractEvent event, Player player, int level) {
    }

    /** Шифт + ЛКМ */
    @Override
    protected void onSneakLeftClick(PlayerInteractEvent event, Player player, int level) {
    }

    /** Подвійний шифт */
    @Override
    protected void onDoubleSneak(Player player, int level) {
    }

    /** Будь-яка зміна стану присідання */
    @Override
    protected void onSneakToggle(Player player, int level, boolean sneaking) {
    }

    /** F */
    @Override
    protected void onSwapHands(PlayerSwapHandItemsEvent event, Player player, int level) {
    }

    /** Шифт + F */
    @Override
    protected void onSneakSwapHands(PlayerSwapHandItemsEvent event, Player player, int level) {
    }

    /** Q */
    @Override
    protected void onDropItem(PlayerDropItemEvent event, Player player, int level) {
    }

    /** Шифт + Q */
    @Override
    protected void onSneakDropItem(PlayerDropItemEvent event, Player player, int level) {
    }

    /** Удар у ближньому бою */
    @Override
    protected void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity target, int level) {
        target.damage(event.getDamage() * (getDouble("power-multiplier") - 1), player);
    }

    /** Будь-яка шкода по власнику */
    @Override
    protected void onDamaged(EntityDamageEvent event, Player player, int level) {
    }

    /** Власник когось убив */
    @Override
    protected void onKill(EntityDeathEvent event, Player killer, LivingEntity victim, int level) {
    }

    @Override
    protected void onBlockBreak(BlockBreakEvent event, Player player, int level) {
    }

    /** Події поза списком тригерів — звичайним листенером; клас уже зареєстрований. */
    @EventHandler(ignoreCancelled = true)
    public void onRiptide(PlayerRiptideEvent event) {
        if (!isMechanicEnabled()) return;
        if (levelOnPlayer(event.getPlayer()) <= 0) return;
    }

    // ==========================================
    //   ТИПОВА АКТИВАЦІЯ
    // ==========================================

    private void cast(Player player, ItemStack item, int level) {
        if (levelOf(item) == 0) return;

        if (!ready(player, item)) {
            deny(player);
            return;
        }
        if (!spend(player, getInt("hunger-cost"))) return;

        cooldown(player, item, getInt("cooldown-ticks") - (level - 1) * 20);
        activation(player);
        effects(player, level);
    }

    private void effects(Player player, int level) {
        double radius = getDouble("radius") + (level - 1);
        boolean affectOwner = getBoolean("affect-owner");
        String label = getString("announce-name");

        particles(player.getLocation().add(0, 1, 0), Particle.CRIT, 20, 0.4);
        particleCircle(player.getLocation().add(0, 0.1, 0), Particle.SOUL_FIRE_FLAME, radius, 24);
        particleLine(player.getEyeLocation(), player.getEyeLocation().add(player.getLocation().getDirection().multiply(radius)),
                Particle.END_ROD, 0.3);
        soundAt(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.7f, 1.4f);
        soundFor(player, Sound.BLOCK_BEACON_ACTIVATE, 0.3f, 1.8f);

        for (var nearby : player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity target)) continue;
            if (!affectOwner && target.equals(player)) continue;
            target.damage(getDouble("power-multiplier") * level, player);
        }
    }
}