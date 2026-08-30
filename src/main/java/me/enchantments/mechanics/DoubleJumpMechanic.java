package me.enchantments.mechanics;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;

import me.enchantments.main.Mechanic;

import java.util.*;

public final class DoubleJumpMechanic extends Mechanic {

    private record JumpState(long jumpedAt, double startY, boolean used, boolean spaceHeld, int level) {
        JumpState holding(boolean held) { return new JumpState(jumpedAt, startY, used, held, level); }
        JumpState consumed() { return new JumpState(jumpedAt, startY, true, spaceHeld, level); }
    }

    private static final long DOUBLE_TAP_WINDOW_MS = 350;

    private final Map<UUID, JumpState> jumps = new HashMap<>();

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
    protected boolean hideEnchantmentGlintDefault() {
        return false;
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

    @EventHandler(ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player) || !event.isGliding()) return;
        JumpState state = jumps.get(player.getUniqueId());
        if (state == null) return;
        if (System.currentTimeMillis() - state.jumpedAt() > DOUBLE_TAP_WINDOW_MS) return;
        event.setCancelled(true);
    }
    
    @EventHandler
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        if (player.isGliding()) player.setGliding(false);
        if (!eligible(player)) return;
        int level = levelOnPlayer(player);
        if (level == 0) return;
        jumps.put(player.getUniqueId(),
                new JumpState(System.currentTimeMillis(), player.getLocation().getY(), false, true, level));
    }

    @EventHandler
    public void onInput(PlayerInputEvent event) {
        if (!isMechanicEnabled()) return;
        Player player = event.getPlayer();
        JumpState state = jumps.get(player.getUniqueId());
        if (state == null || state.used() || player.isOnGround()) return;

        if (!event.getInput().isJump()) {
            jumps.put(player.getUniqueId(), state.holding(false));
            return;
        }
        if (state.spaceHeld()) return;
        if (System.currentTimeMillis() - state.jumpedAt() > DOUBLE_TAP_WINDOW_MS) return;

        int minFood = getInt("min-food");
        if (player.getFoodLevel() <= minFood) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent("§cВи надто зголодніли для подвійного стрибка!"));
            return;
        }
        jump(player, state);
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        JumpState state = jumps.remove(player.getUniqueId());
        double safeFallHeight = getDouble("safe-fall-height");
        if (state != null && state.used() && state.startY() - player.getLocation().getY() < safeFallHeight) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        jumps.remove(event.getPlayer().getUniqueId());
    }

    private boolean eligible(Player player) {
        GameMode mode = player.getGameMode();
        return (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                && !player.isGliding() && !player.getAllowFlight();
    }

    private void jump(Player player, JumpState state) {
        double velocityY = getDouble("velocity-y") + (getDouble("velocity-y-per-level") * (state.level() - 1));
        double forwardMult = getDouble("forward-mult");
        double foodCost = getDouble("food-cost");

        Vector direction = player.getLocation().getDirection();
        player.setVelocity(direction.multiply(forwardMult).setY(velocityY));
        player.setSprinting(true);
        player.setFallDistance(0f);
        jumps.put(player.getUniqueId(), state.consumed());

        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 5, 0.3, 0.1, 0.3, 0.02);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.5f);
        player.setExhaustion(player.getExhaustion() + (float) foodCost);
    }
}
