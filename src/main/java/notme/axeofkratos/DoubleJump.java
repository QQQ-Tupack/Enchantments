package notme.axeofkratos;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class DoubleJump implements Listener {

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, Brain.getInstance());

        new BukkitRunnable() {
            @SuppressWarnings("deprecation")
			@Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!player.hasPermission("perks.doublejump") || player.getGameMode() != GameMode.SURVIVAL) {
                        continue;
                    }
                    if (player.isGliding()) {
                        continue;
                    }
                    if (player.getFoodLevel() <= 6) {
                    	player.setAllowFlight(false);
                    } else
                    if (/*!player.getLocation().subtract(0, 1, 0).getBlock().isPassable()
                    	|| !player.getLocation().subtract(0, 2, 0).getBlock().isPassable() ||*/ player.isOnGround()) {
                        player.setAllowFlight(true);
                        resetJumpCount(player);
                        player.removeMetadata("doublejump", Brain.getInstance());
                    }
                }
            }
        }.runTaskTimer(Brain.getInstance(), 0L, 5L);

        Brain.getInstance().getLogger().info(getClass().getSimpleName() + " module has been initialized! (Perm: perks.doublejump)");
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();

        if (!player.hasPermission("perks.doublejump") || player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        if (player.isGliding()) {
            return;
        }
        
        event.setCancelled(true);

        if (player.getFoodLevel() <= 7) {
        	player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("Ви надто зголодніли, щоб робити подвійні стрибки!"));
        }

        int jumpCount = getJumpCount(player);

        if (jumpCount < 2) {
            Vector jumpVelocity = player.getLocation().getDirection().multiply(0.5).setY(0.5);
            player.setVelocity(jumpVelocity);
            player.setMetadata("doublejump", new FixedMetadataValue(Brain.getInstance(), true));
            
            setJumpCount(player, jumpCount + 1);
            player.setAllowFlight(false);
            
            // Ефекти
            player.getWorld().spawnParticle(Particle.CRIT_MAGIC, player.getLocation(), 5);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.0f);
            
            // Віднімання голоду, емулюючи біг
            float exhaustion = player.getExhaustion();
            exhaustion += 0.1f;
            player.setExhaustion(exhaustion);
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                if (player.hasMetadata("doublejump")) {
                    if (player.getFallDistance()-3 <= 3) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    private int getJumpCount(Player player) {
        if (player.hasMetadata("jumpCount")) {
            MetadataValue value = player.getMetadata("jumpCount").stream()
                .filter(meta -> meta.getOwningPlugin().equals(Brain.getInstance()))
                .findFirst().orElse(null);
            if (value != null) {
                return value.asInt();
            }
        }
        return 0;
    }

    private void setJumpCount(Player player, int count) {
        player.setMetadata("jumpCount", new FixedMetadataValue(Brain.getInstance(), count));
    }

    private void resetJumpCount(Player player) {
        player.removeMetadata("jumpCount", Brain.getInstance());
    }
}
