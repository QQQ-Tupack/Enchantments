package me.enchantments.main;

import me.enchantments.listeners.GlintUpdateListener;
import me.enchantments.mechanics.ParryMechanic;
import me.enchantments.utils.DatapackGenerator;
import me.enchantments.utils.EnchantmentRegistry;
import me.enchantments.utils.SourceInjector;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Enchantments extends JavaPlugin {

    private static Enchantments instance;

    public static Enchantments getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        new Config().register();
        DatapackGenerator.install();
        EnchantmentRegistry.registerAll(this);
        Bukkit.getPluginManager().registerEvents(new SourceInjector(), this);
        Bukkit.getPluginManager().registerEvents(new GlintUpdateListener(), this);
    }

    @Override
    public void onDisable() {
        EnchantmentRegistry.mechanics().stream()
                .filter(ParryMechanic.class::isInstance)
                .map(ParryMechanic.class::cast)
                .forEach(ParryMechanic::shutdown);
    }
}
