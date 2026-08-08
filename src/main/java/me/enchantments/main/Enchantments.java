package me.enchantments.main;

import me.enchantments.mechanics.ParryMechanic;
import me.enchantments.mechanics.PhantomVeilMechanic;
import me.enchantments.mechanics.RadiantAuraMechanic;
import me.enchantments.mechanics.SaturationBonusMechanic;
import me.enchantments.mechanics.SculkHarpoonMechanic;
import me.enchantments.mechanics.SleepRestoreMechanic;
import me.enchantments.mechanics.SoaringBoundMechanic;
import me.enchantments.mechanics.SoulStormMechanic;
import me.enchantments.mechanics.SoulboundMechanic;
import me.enchantments.mechanics.StoneSkinMechanic;
import me.enchantments.mechanics.SwiftLaborMechanic;
import me.enchantments.mechanics.TitanMightMechanic;
import me.enchantments.mechanics.UmbralSightMechanic;
import me.enchantments.mechanics.VitalSatietyMechanic;
import me.enchantments.mechanics.VoidRescueMechanic;
import me.enchantments.mechanics.WitherBarrageMechanic;
import me.enchantments.mechanics.WitherShadowMechanic;
import me.enchantments.mechanics.WolfCompanionMechanic;
import me.enchantments.mechanics.WolfFetchMechanic;
import me.enchantments.mechanics.WolfSniffMechanic;
import me.enchantments.listeners.GlintUpdateListener;
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
import me.enchantments.mechanics.HorseCompanionMechanic;
import me.enchantments.mechanics.InfernalBlastMechanic;
import me.enchantments.mechanics.InfernalShieldMechanic;
import me.enchantments.mechanics.Mechanic;
import me.enchantments.mechanics.OceanGraceMechanic;
import me.enchantments.mechanics.ParachuteMechanic;

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

        new InfernalShieldMechanic().register();
        new StoneSkinMechanic().register();

        new SaturationBonusMechanic().register();
        new VitalSatietyMechanic().register();

        new FortunesFavorMechanic().register();
        new SwiftLaborMechanic().register();

        new TitanMightMechanic().register();
        new GaleStrideMechanic().register();
        new GaleDescentMechanic().register();
        new RadiantAuraMechanic().register();

        new AbyssalBreathMechanic().register();
        new OceanGraceMechanic().register();

        new UmbralSightMechanic().register();
        new PhantomVeilMechanic().register();
        new SoaringBoundMechanic().register();
        
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
        
        DatapackGenerator.install();

        Bukkit.getPluginManager().registerEvents(new SourceInjector(), this);
        Bukkit.getPluginManager().registerEvents(new GlintUpdateListener(), this);
    }

    @Override
    public void onDisable() {
        Mechanic.getAll().stream()
                .filter(ParryMechanic.class::isInstance)
                .map(ParryMechanic.class::cast)
                .forEach(ParryMechanic::shutdown);
    }
}