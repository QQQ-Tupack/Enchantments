package me.enchantments.main;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Triggers extends MechanicUtils implements Listener {

    public abstract boolean isMechanicEnabled();
    public abstract int levelOnPlayer(Player player);

    protected long doubleSneakWindow() {
        return 400L;
    }

    private final Map<UUID, Long> lastSneak = new ConcurrentHashMap<>();

    private int activeLevel(Player player) {
        if (player == null || !isMechanicEnabled()) return 0;
        return levelOnPlayer(player);
    }

    protected void onLeftClick(PlayerInteractEvent event, Player player, int level) {}
    protected void onRightClick(PlayerInteractEvent event, Player player, int level) {}

    protected void onSneakLeftClick(PlayerInteractEvent event, Player player, int level) {
        onLeftClick(event, player, level);
    }

    protected void onSneakRightClick(PlayerInteractEvent event, Player player, int level) {
        onRightClick(event, player, level);
    }

    protected void onDoubleSneak(Player player, int level) {}
    protected void onSneakToggle(Player player, int level, boolean sneaking) {}

    protected void onSwapHands(PlayerSwapHandItemsEvent event, Player player, int level) {}

    protected void onSneakSwapHands(PlayerSwapHandItemsEvent event, Player player, int level) {
        onSwapHands(event, player, level);
    }

    protected void onDropItem(PlayerDropItemEvent event, Player player, int level) {}

    protected void onSneakDropItem(PlayerDropItemEvent event, Player player, int level) {
        onDropItem(event, player, level);
    }

    protected void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity target, int level) {}

    protected void onDamaged(EntityDamageEvent event, Player player, int level) {}

    protected void onKill(EntityDeathEvent event, Player killer, LivingEntity victim, int level) {}

    protected void onBlockBreak(BlockBreakEvent event, Player player, int level) {}

    @EventHandler
    public void handleInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        int level = activeLevel(player);
        if (level <= 0) return;

        boolean sneaking = player.isSneaking();
        switch (event.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> {
                if (sneaking) onSneakRightClick(event, player, level);
                else onRightClick(event, player, level);
            }
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> {
                if (sneaking) onSneakLeftClick(event, player, level);
                else onLeftClick(event, player, level);
            }
            default -> {}
        }
    }

    @EventHandler
    public void handleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        int level = activeLevel(player);
        if (level <= 0) return;

        onSneakToggle(player, level, event.isSneaking());
        if (!event.isSneaking()) return;

        long now = System.currentTimeMillis();
        Long last = lastSneak.put(player.getUniqueId(), now);
        if (last != null && now - last <= doubleSneakWindow()) {
            lastSneak.remove(player.getUniqueId());
            onDoubleSneak(player, level);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void handleSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        int level = activeLevel(player);
        if (level <= 0) return;

        if (player.isSneaking()) onSneakSwapHands(event, player, level);
        else onSwapHands(event, player, level);
    }

    @EventHandler(ignoreCancelled = true)
    public void handleDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        int level = activeLevel(player);
        if (level <= 0) return;

        if (player.isSneaking()) onSneakDropItem(event, player, level);
        else onDropItem(event, player, level);
    }

    @EventHandler(ignoreCancelled = true)
    public void handleAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        int level = activeLevel(player);
        if (level <= 0) return;

        onAttack(event, player, target, level);
    }

    @EventHandler(ignoreCancelled = true)
    public void handleDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        int level = activeLevel(player);
        if (level <= 0) return;

        onDamaged(event, player, level);
    }

    @EventHandler
    public void handleKill(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;

        int level = activeLevel(killer);
        if (level <= 0) return;

        onKill(event, killer, victim, level);
    }

    @EventHandler(ignoreCancelled = true)
    public void handleBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        int level = activeLevel(player);
        if (level <= 0) return;

        onBlockBreak(event, player, level);
    }

    @EventHandler
    public void handleQuit(PlayerQuitEvent event) {
        lastSneak.remove(event.getPlayer().getUniqueId());
    }
}