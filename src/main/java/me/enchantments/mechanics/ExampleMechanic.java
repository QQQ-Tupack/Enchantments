package me.enchantments.mechanics;

import me.enchantments.utils.EnchantMeta;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Приклад механіки - використовуйте як шаблон для створення нових механік
 * 
 * КРОК 1: Замініть назву класу на вашу (наприклад: MyAwesomeMechanic)
 * КРОК 2: Додайте приватні поля для параметрів з конфіга
 * КРОК 3: Реалізуйте конструктор та витягніть параметри
 * КРОК 4: Добавте EventHandler методи для слухання подій
 * КРОК 5: Додайте логіку вашої механіки
 * КРОК 6: Зареєструйте механіку в EnchantmentRegistry
 * КРОК 7: Добавте конфіг енчарту у config.yml
 */
public final class ExampleMechanic extends Mechanic {

    // ──────────────────────────────────────────────────────────────────
    // КРОК 2: Додайте приватні поля для параметрів з конфіга
    // ──────────────────────────────────────────────────────────────────
    
    private final int exampleIntParam;
    private final double exampleDoubleParam;
    private final String exampleStringParam;
    private final boolean exampleBooleanParam;

    // ──────────────────────────────────────────────────────────────────
    // КРОК 3: Реалізуйте конструктор та витягніть параметри з конфіга
    // ──────────────────────────────────────────────────────────────────
    
    public ExampleMechanic(EnchantMeta meta) {
        super(meta);
        
        // Отримайте перший trigger (найчастіше у вас буде тільки один)
        var params = meta.triggers().isEmpty() ? null : meta.triggers().get(0);
        
        // Витягніть параметри з конфіга з дефолтними значеннями
        exampleIntParam = params != null ? params.getInt("example_int", 10) : 10;
        exampleDoubleParam = params != null ? params.getDouble("example_double", 0.5) : 0.5;
        exampleStringParam = params != null ? params.getString("example_string", "default") : "default";
        exampleBooleanParam = params != null ? params.getBoolean("example_boolean", true) : true;
    }

    // ──────────────────────────────────────────────────────────────────
    // КРОК 4 & 5: EventHandler методи для слухання подій та логіка
    // ──────────────────────────────────────────────────────────────────
    
    /**
     * Приклад: слухаємо PlayerInteractEvent
     * Замініть цей метод на ті события які вам потрібні
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        // Перевірте чи предмет має це зачарування
        int level = levelOf(item);
        if (level == 0) return;
        
        // ТУТ ВАША ЛОГІКА
        // player.sendMessage("ExampleMechanic спрацював на рівні: " + level);
    }

    // ──────────────────────────────────────────────────────────────────
    // Приклади інших подій які ви можете слухати:
    // ──────────────────────────────────────────────────────────────────
    
    /*
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        // Коли гравець рухається
    }
    
    @EventHandler
    public void onPlayerDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        // Коли гравець отримує шкоду
    }
    
    @EventHandler
    public void onPlayerAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        // Коли гравець атакує сутність
    }
    
    @EventHandler
    public void onPlayerJump(PlayerJumpEvent e) {
        // Коли гравець стрибає (Paper only)
    }
    
    @EventHandler
    public void onPlayerFish(PlayerFishEvent e) {
        // Коли гравець рибалить
    }
    
    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent e) {
        // Коли гравець їсть/п'є предмет
    }
    */

    // ──────────────────────────────────────────────────────────────────
    // Корисні методи з базового класу Mechanic
    // ──────────────────────────────────────────────────────────────────
    
    /*
    // Отримайте рівень зачарування на предметі
    int level = levelOf(itemStack);
    
    // Отримайте рівень зачарування що гравець носить
    int level = levelOnPlayer(player);
    
    // Отримайте метаінформацію про зачарування
    String name = meta.name();
    int maxLevel = meta.maxLevel();
    List<EnchantSlot> slots = meta.slots();
    */
}

// ══════════════════════════════════════════════════════════════════════════════
// КРОК 6: Зареєструйте механіку в EnchantmentRegistry
// ══════════════════════════════════════════════════════════════════════════════

/*
Знайдіть метод readMechanics() у EnchantmentRegistry.java та додайте:

case "example_mechanic" -> new ExampleMechanic(meta);
*/

// ══════════════════════════════════════════════════════════════════════════════
// КРОК 7: Додайте конфіг енчарту у config.yml
// ══════════════════════════════════════════════════════════════════════════════

/*
  example_enchantment:
    name: "Приклад Зачарування"
    supported_items: "#enchantments:axes"  # або конкретний предмет
    slots: [mainhand]
    weight: 2
    max_level: 3
    triggers:
      - mechanic: example_mechanic
        example_int: 10
        example_double: 0.5
        example_string: "default"
        example_boolean: true
    sources:
      enchanting_table: true
      loot_tables:
        - table: minecraft:chests/abandoned_mineshaft
          chance: 0.1
          min_level: 1
          max_level: 3
      villager_trades:
        - { profession: librarian, villager_level: 3, min_level: 1, max_level: 3, price: 30 }
*/
