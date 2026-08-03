package me.enchantments.mechanics;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;

import me.enchantments.utils.EnchantMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DoubleJumpMechanic extends Mechanic {

    private record JumpState(long jumpedAt, double startY, boolean used, boolean spaceHeld, int level) {
        JumpState holding(boolean held) { return new JumpState(jumpedAt, startY, used, held, level); }
        JumpState consumed() { return new JumpState(jumpedAt, startY, true, spaceHeld, level); }
    }

    private static final long DOUBLE_TAP_WINDOW_MS = 350;

    private final Scaled velocityY;
    private final double forwardMult;
    private final double foodCost;
    private final int minFood;
    private final double safeFallHeight;

    private final Map<UUID, JumpState> jumps = new HashMap<>();

    public DoubleJumpMechanic(EnchantMeta meta) {
        super(meta);
        var params = meta.triggers().isEmpty() ? null : meta.triggers().get(0);
        velocityY = new Scaled(params != null ? params.getDouble("velocity_y", 0.6) : 0.6, 
                               params != null ? params.getDouble("velocity_y_per_level", 0.1) : 0.1);
        forwardMult = params != null ? params.getDouble("forward_mult", 0.5) : 0.5;
        foodCost = params != null ? params.getDouble("food_cost", 0.1) : 0.1;
        minFood = params != null ? params.getInt("min_food", 7) : 7;
        safeFallHeight = params != null ? params.getDouble("safe_fall_height", 3.0) : 3.0;
    }

    @EventHandler
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        if (!eligible(player)) return;
        int level = levelOnPlayer(player);
        if (level == 0) return;
        jumps.put(player.getUniqueId(),
                new JumpState(System.currentTimeMillis(), player.getLocation().getY(), false, true, level));
    }

    @EventHandler
    public void onInput(PlayerInputEvent event) {
        Player player = event.getPlayer();
        JumpState state = jumps.get(player.getUniqueId());
        if (state == null || state.used() || player.isOnGround()) return;

        if (!event.getInput().isJump()) {
            jumps.put(player.getUniqueId(), state.holding(false));
            return;
        }
        if (state.spaceHeld()) return;
        if (System.currentTimeMillis() - state.jumpedAt() > DOUBLE_TAP_WINDOW_MS) return;

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
        Vector direction = player.getLocation().getDirection();
        player.setVelocity(direction.multiply(forwardMult).setY(velocityY.at(state.level())));
        player.setSprinting(true);
        player.setFallDistance(0f);
        jumps.put(player.getUniqueId(), state.consumed());

        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 5, 0.3, 0.1, 0.3, 0.02);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.5f);
        player.setExhaustion(player.getExhaustion() + (float) foodCost);
    }
}
