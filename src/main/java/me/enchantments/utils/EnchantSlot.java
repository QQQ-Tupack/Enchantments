package me.enchantments.utils;

import org.bukkit.inventory.EquipmentSlot;

public enum EnchantSlot {
    MAINHAND(EquipmentSlot.HAND),
    OFFHAND(EquipmentSlot.OFF_HAND),
    FEET(EquipmentSlot.FEET),
    LEGS(EquipmentSlot.LEGS),
    CHEST(EquipmentSlot.CHEST),
    HEAD(EquipmentSlot.HEAD),
    BODY(EquipmentSlot.BODY);

    private final EquipmentSlot bukkit;

    EnchantSlot(EquipmentSlot bukkit) {
        this.bukkit = bukkit;
    }

    public EquipmentSlot bukkit() {
        return bukkit;
    }

    public String datapackId() {
        return name().toLowerCase();
    }
}
