package me.enchantments.main;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import me.enchantments.listeners.EnchantBookInfoHandler;
import me.enchantments.listeners.GlintUpdateListener;
import me.enchantments.listeners.GrindstoneListener;
import me.enchantments.listeners.SourceInjector;
import me.enchantments.mechanics.AbyssalBreathMechanic;
import me.enchantments.mechanics.AbyssalGazeMechanic;
import me.enchantments.mechanics.AxeCleaveMechanic;
import me.enchantments.mechanics.AxePullMechanic;
import me.enchantments.mechanics.AxeThrowMechanic;
import me.enchantments.mechanics.BoneLegionMechanic;
import me.enchantments.mechanics.DoubleJumpMechanic;
import me.enchantments.mechanics.EarthshatterMechanic;
import me.enchantments.mechanics.EndBreathMechanic;
import me.enchantments.mechanics.FireWardMechanic;
import me.enchantments.mechanics.FortunesFavorMechanic;
import me.enchantments.mechanics.GaleDescentMechanic;
import me.enchantments.mechanics.GaleStrideMechanic;
import me.enchantments.mechanics.GrapplingHookMechanic;
import me.enchantments.mechanics.HaloWardMechanic;
import me.enchantments.mechanics.HorseCompanionMechanic;
import me.enchantments.mechanics.InfernalBlastMechanic;
import me.enchantments.mechanics.JesterMechanic;
import me.enchantments.mechanics.OceanGraceMechanic;
import me.enchantments.mechanics.ParachuteMechanic;
import me.enchantments.mechanics.ParryMechanic;
import me.enchantments.mechanics.PhantomVeilMechanic;
import me.enchantments.mechanics.QuickdrawMechanic;
import me.enchantments.mechanics.RadiantAuraMechanic;
import me.enchantments.mechanics.SculkHarpoonMechanic;
import me.enchantments.mechanics.SleepRestoreMechanic;
import me.enchantments.mechanics.SlipstreamMechanic;
import me.enchantments.mechanics.SoulStormMechanic;
import me.enchantments.mechanics.SoulboundMechanic;
import me.enchantments.mechanics.SwiftLaborMechanic;
import me.enchantments.mechanics.ThornGraspMechanic;
import me.enchantments.mechanics.TreasureSenseMechanic;
import me.enchantments.mechanics.UmbralSightMechanic;
import me.enchantments.mechanics.VitalSatietyMechanic;
import me.enchantments.mechanics.VoidRescueMechanic;
import me.enchantments.mechanics.WealthMechanic;
import me.enchantments.mechanics.WitchBrewMechanic;
import me.enchantments.mechanics.WitherBarrageMechanic;
import me.enchantments.mechanics.WitherShadowMechanic;
import me.enchantments.mechanics.WolfCompanionMechanic;
import me.enchantments.mechanics.WolfFetchMechanic;
import me.enchantments.mechanics.WolfSniffMechanic;

public final class Enchantments extends JavaPlugin {

    private static Enchantments instance;

    public static Enchantments getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        new Config().register();

        new DoubleJumpMechanic().register();
        new AxeThrowMechanic().register();
        new AxePullMechanic().register();
        new AxeCleaveMechanic().register();
        new ParryMechanic().register();
        new ParachuteMechanic().register();
        new GrapplingHookMechanic().register();
        new WolfCompanionMechanic().register();
        new WolfSniffMechanic().register();
        new WolfFetchMechanic().register();
        new HorseCompanionMechanic().register();
        new VoidRescueMechanic().register();
        new SleepRestoreMechanic().register();
        new FireWardMechanic().register();

        new VitalSatietyMechanic().register();

        new FortunesFavorMechanic().register();
        new SwiftLaborMechanic().register();

        new GaleStrideMechanic().register();
        new GaleDescentMechanic().register();
        new RadiantAuraMechanic().register();

        new AbyssalBreathMechanic().register();
        new OceanGraceMechanic().register();

        new UmbralSightMechanic().register();
        new PhantomVeilMechanic().register();
        
        new SoulboundMechanic().register();

        new EndBreathMechanic().register();
        new EarthshatterMechanic().register();
        new SoulStormMechanic().register();
        new WitherBarrageMechanic().register();
        new InfernalBlastMechanic().register();
        new SculkHarpoonMechanic().register();
        new BoneLegionMechanic().register();
        

        new WitherShadowMechanic().register();
        new AbyssalGazeMechanic().register();
        
        new WitchBrewMechanic().register();
        new SlipstreamMechanic().register();
        new TreasureSenseMechanic().register();
        new QuickdrawMechanic().register();
        new JesterMechanic().register();
        new ThornGraspMechanic().register();
        new HaloWardMechanic().register();
        new WealthMechanic().register();
        
        DatapackGenerator.install();

        Bukkit.getPluginManager().registerEvents(new SourceInjector(), this);
        Bukkit.getPluginManager().registerEvents(new GlintUpdateListener(), this);
        Bukkit.getPluginManager().registerEvents(new GrindstoneListener(), this);
        Bukkit.getPluginManager().registerEvents(new EnchantBookInfoHandler(), this);
    }

    @Override
    public void onDisable() {
        Mechanic.getAll().stream()
                .filter(ParryMechanic.class::isInstance)
                .map(ParryMechanic.class::cast)
                .forEach(ParryMechanic::shutdown);
    }
}