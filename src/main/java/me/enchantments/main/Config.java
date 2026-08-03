package me.enchantments.main;

public final class Config {

    public void register() {
        Enchantments.getInstance().saveDefaultConfig();
        Enchantments.getInstance().reloadConfig();
    }
}
