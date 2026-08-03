package me.enchantments.mechanics;

import me.enchantments.main.Enchantments;
import me.enchantments.utils.EnchantMeta;
import me.enchantments.utils.TriggerConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.TradeSelectEvent;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class EffectMechanic extends Mechanic {

    private static final long WORN_REFRESH_TICKS = 20L;

    private enum Trigger {
        TAKE_DAMAGE, DEAL_DAMAGE, KILL, FISH, TRADE, SLEEP, HARVEST, BREED,
        ENCHANT, CRAFT, EAT, PORTAL, DEATH, RESPAWN, LEVEL_UP, BLOCK_BREAK, TAME, WORN;
        boolean isDamage() { return this == TAKE_DAMAGE || this == DEAL_DAMAGE; }
    }

    private record PotionSpec(PotionEffectType type, Scaled amplifier, Scaled duration) {
        PotionEffect at(int level) {
            return new PotionEffect(type, (int) duration.at(level), (int) amplifier.at(level), false, true, true);
        }
    }

    private record TriggerEntry(Trigger trigger, Set<DamageCause> causes, List<PotionSpec> effects, int cooldownTicks) {}

    private final List<TriggerEntry> entries;
    private final Map<UUID, long[]> cooldowns = new ConcurrentHashMap<>();

    public EffectMechanic(EnchantMeta meta) {
        super(meta);
        int defaultCooldown = 1200;
        
        entries = meta.triggers().isEmpty()
                ? List.of()
                : meta.triggers().stream().map(t -> readEntry(t, defaultCooldown)).toList();

        if (entries.stream().anyMatch(e -> e.trigger() == Trigger.WORN)) startWornTask();
    }

    private static TriggerEntry readEntry(TriggerConfig t, int defaultCooldown) {
        Trigger trigger = Trigger.valueOf(t.getString("trigger", "take_damage").toUpperCase());
        Set<DamageCause> causes = readCauses(t.getStringList("causes"));
        List<PotionSpec> effects = readEffectsFromConfig(t);
        int cooldownTicks = t.getInt("cooldown_ticks", defaultCooldown);
        return new TriggerEntry(trigger, causes, effects, cooldownTicks);
    }

    private static List<PotionSpec> readEffectsFromConfig(TriggerConfig t) {
        List<Map<?, ?>> raw = t.getMapList("effects");
        if (!raw.isEmpty()) return raw.stream().map(EffectMechanic::readSpec).toList();
        return List.of(singleSpec(t.getString("effect_type", ""), t.getInt("amp", 0),
                t.getInt("amp_per_level", 0), t.getInt("duration", 200), t.getInt("duration_per_level", 0)));
    }

    private static Set<DamageCause> readCauses(List<String> raw) {
        return raw.stream().map(v -> DamageCause.valueOf(String.valueOf(v).toUpperCase()))
                .collect(HashSet::new, Set::add, Set::addAll);
    }

    private static PotionSpec singleSpec(String effectType, int amp, int ampPerLevel, int duration, int durationPerLevel) {
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(effectType));
        if (type == null) throw new IllegalStateException("mechanic 'effect' requires effect_type or effects");
        return new PotionSpec(type, new Scaled(amp, ampPerLevel), new Scaled(duration, durationPerLevel));
    }

    private static PotionSpec readSpec(Map<?, ?> m) {
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(String.valueOf(m.get("type"))));
        return new PotionSpec(type,
                new Scaled(number(m.get("amplification")), number(m.get("amplification_per_level"))),
                new Scaled(m.get("duration") == null ? 200 : number(m.get("duration")), number(m.get("duration_per_level"))));
    }

    private static double number(Object o) {
        return o instanceof Number ? ((Number) o).doubleValue() : 0;
    }

    private void startWornTask() {
        Bukkit.getScheduler().runTaskTimer(Enchantments.getInstance(), () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                int level = levelOnPlayer(p);
                if (level == 0) continue;
                entries.stream().filter(e -> e.trigger() == Trigger.WORN)
                        .forEach(e -> e.effects().forEach(spec -> p.addPotionEffect(spec.at(level))));
            }
        }, 0L, WORN_REFRESH_TICKS);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) { if (e.getEntity() instanceof Player p) trigger(p, Trigger.TAKE_DAMAGE, e.getCause()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) { attacker(e).ifPresent(p -> trigger(p, Trigger.DEAL_DAMAGE, e.getCause())); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) { if (e.getEntity().getKiller() instanceof Player p) trigger(p, Trigger.KILL, null); }
    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) { if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) trigger(e.getPlayer(), Trigger.FISH, null); }
    @EventHandler(ignoreCancelled = true)
    public void onTrade(TradeSelectEvent e) { if (e.getWhoClicked() instanceof Player p) trigger(p, Trigger.TRADE, null); }
    @EventHandler(ignoreCancelled = true)
    public void onSleep(PlayerBedEnterEvent e) { if (e.getBedEnterResult() == null) trigger(e.getPlayer(), Trigger.SLEEP, null); }
    @EventHandler(ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent e) { trigger(e.getPlayer(), Trigger.HARVEST, null); }
    @EventHandler(ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) { if (e.getBreeder() instanceof Player p) trigger(p, Trigger.BREED, null); }
    @EventHandler(ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) { trigger(e.getEnchanter(), Trigger.ENCHANT, null); }
    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) { if (e.getWhoClicked() instanceof Player p) trigger(p, Trigger.CRAFT, null); }
    @EventHandler(ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) { trigger(e.getPlayer(), Trigger.EAT, null); }
    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) { trigger(e.getPlayer(), Trigger.PORTAL, null); }
    @EventHandler
    public void onDeath(PlayerDeathEvent e) { trigger(e.getEntity(), Trigger.DEATH, null); }
    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) { trigger(e.getPlayer(), Trigger.RESPAWN, null); }
    @EventHandler(ignoreCancelled = true)
    public void onLevelUp(PlayerLevelChangeEvent e) { if (e.getNewLevel() > e.getOldLevel()) trigger(e.getPlayer(), Trigger.LEVEL_UP, null); }
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) { if (e.getBlock().isSolid()) trigger(e.getPlayer(), Trigger.BLOCK_BREAK, null); }
    @EventHandler(ignoreCancelled = true)
    public void onTame(EntityTameEvent e) { if (e.getOwner() instanceof Player p) trigger(p, Trigger.TAME, null); }

    private void trigger(Player player, Trigger fired, DamageCause cause) {
        long tick = Bukkit.getServer().getCurrentTick();
        long[] readyAt = cooldowns.computeIfAbsent(player.getUniqueId(), k -> new long[entries.size()]);

        for (int i = 0; i < entries.size(); i++) {
            TriggerEntry entry = entries.get(i);
            if (entry.trigger() != fired) continue;
            if (fired.isDamage() && !entry.causes().isEmpty() && cause != null && !entry.causes().contains(cause)) continue;
            if (readyAt[i] > tick) continue;

            int level = levelOnPlayer(player);
            if (level == 0) continue;

            entry.effects().forEach(spec -> player.addPotionEffect(spec.at(level)));
            player.sendActionBar(Component.text(meta.name() + " ✦ " + level, NamedTextColor.GOLD));
            readyAt[i] = tick + entry.cooldownTicks();
        }
    }

    private Optional<Player> attacker(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) return Optional.of(p);
        if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p) return Optional.of(p);
        return Optional.empty();
    }
}