package me.enchantments.utils;

import java.util.List;

public record EnchantMeta(
        String key,
        String name,
        String translationKey,
        String supportedItems,
        int weight,
        int maxLevel,
        Cost minCost,
        Cost maxCost,
        int anvilCost,
        List<EnchantSlot> slots,
        String exclusiveSet,
        boolean hideGlint,
        Sources sources,
        List<TriggerConfig> triggers
) {
    public record Cost(int base, int perLevel) {}

    public record Sources(boolean enchantingTable, List<LootSource> lootTables, List<TradeSource> trades) {}

    public record LootSource(String table, double chance, int minLevel, int maxLevel) {}

    public record TradeSource(String profession, int villagerLevel, int minLevel, int maxLevel, int price) {}
}
