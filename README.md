# Enchantments

> **Пишіть механіку один раз. Вішайте її на будь-який предмет через зачарування.**

## Що таке Enchantments?

**Enchantments** — фреймворк для Paper, який перетворює довільну Java-логіку на ванільне зачарування.

Механіка пишеться один раз як звичайний Java-клас, що успадковує `Mechanic`. Далі вона працює на будь-якому предметі, підтримка якого визначена в самій механіці — чоботи, сокири, броня коня/вовка, щит, вудка — і не потребує окремої інтеграції під кожен новий предмет.

Плагін сам генерує:

* ванільний datapack (`data/enchantments/enchantment/<id>.json`) з правильним `weight`, `max_level`, `slots`, `supported_items`;
* тег предметів (`data/enchantments/tags/item/<id>.json`) з `getSupportedItems()`;
* конфіг механіки (`plugins/Enchantments/mechanics/<id>.yml`) з дефолтами з коду.

---

## Створення механіки

Механіка — звичайний клас, що успадковує `Mechanic` і перевизначає обов'язкові методи метаданих.

```java
public final class DoubleJumpMechanic extends Mechanic {

    @Override
    public String getId() {
        return "double_jump";
    }

    @Override
    public String getName() {
        return "Подвійний стрибок";
    }

    @Override
    public String getDescription() {
        return "Дозволяє гравцю виконати подвійний стрибок у повітрі";
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS, Material.IRON_BOOTS,
                       Material.GOLDEN_BOOTS, Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.FEET);
    }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "velocity-y", 0.6,
            "velocity-y-per-level", 0.1,
            "forward-mult", 0.5,
            "food-cost", 0.1,
            "min-food", 7,
            "safe-fall-height", 3.0
        );
    }

    @EventHandler
    public void onJump(PlayerJumpEvent event) {
        // ваша логіка
    }
}
```

Обов'язкові метадані:

| Метод | Призначення |
|---|---|
| `getId()` | унікальний ідентифікатор — ключ enchantment'а і файлу конфігу |
| `getName()` | назва, що йде у `description`/`fallback` |
| `getDescription()` | опис (наразі лише для конфігу, не в datapack) |
| `getMaxLevel()` | максимальний рівень |
| `getSupportedItems()` | список `Material`, з яких генерується item-tag |
| `getSlots()` | список `EnchantSlot`, у яких зачарування діє |

Реєстрація виконується самим плагіном при старті:

```java
Mechanic.init(this);
new DoubleJumpMechanic().register();
new AxeCleaveMechanic().register();
// ...
```

---


## Джерела отримання, вартість, вага

Ці параметри мають дефолти в коді, які й потрапляють у `mechanics/<id>.yml` при першому запуску:

```java
@Override
public boolean isEnchantingTableAllowed() { return true; }

@Override
public double getEnchantingTableChance() { return 0.12; }

@Override
public boolean isChestLootAllowed() { return true; }

@Override
public double getChestLootChance() { return 0.05; }

@Override
public List<String> getLootChests() {
    return List.of("minecraft:chests/end_city_treasure");
}

@Override
public boolean isVillagerTradeAllowed() { return true; }

@Override
public Map<String, Map<String, Object>> getVillagerTrades() {
    return Map.of("LIBRARIAN", Map.of("level", 2, "min-emeralds", 10, "max-emeralds", 24));
}

@Override
public int getAnvilBaseCost() { return 8; }

@Override
public int getAnvilCostPerLevel() { return 4; }

@Override
public int getWeight() { return 10; }
```

## Кастомні налаштування механіки

Усе, що специфічне для конкретної механіки, іде через `getDefaults()` + геттери, і живе під `settings.*` в yml:

```java
@Override
public Map<String, Object> getDefaults() {
    return Map.of("cleave-radius", 2.0, "cleave-damage", 3.0);
}
```

Читання в рантаймі:

```java
double radius = getDouble("cleave-radius");
double damage = getDouble("cleave-damage");
```
---

## Основна ідея

Механіка описується один раз як Java-клас. Плагін бере на себе реєстрацію слухача, конфіг з дефолтами, і генерацію ванільного зачарування з правильними предметами та слотами — розробник пише лише логіку в `@EventHandler`.