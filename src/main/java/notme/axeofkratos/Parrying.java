package notme.axeofkratos;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class Parrying implements Listener {

	private Plugin plugin = Brain.getInstance();
	
    public void register() {
        Bukkit.getPluginManager().registerEvents(this, Brain.getInstance());
        Brain.getInstance().getLogger().info(String.valueOf(getClass().getSimpleName()) + " module has been initialized! (Perms: perks.parrying)");
    }

    @EventHandler
    public void onMobAttack(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getDamager() instanceof LivingEntity) && !(event.getDamager() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!player.hasPermission("perks.parrying")) return;

        LivingEntity damager = (LivingEntity) Bukkit.getEntity(event.getDamager().getUniqueId());
        if (!player.getInventory().getItemInMainHand().getType().equals(Material.SHIELD) && !player.getInventory().getItemInOffHand().getType().equals(Material.SHIELD)) return;
        if (player.isBlocking()) return;
        displayParryEffects(player);
        event.setCancelled(true);
        
        new BukkitRunnable() {
        	private int ticks = 0;
            @Override
            public void run() {
                if (player.isBlocking()) {
                    Location location = damager.getLocation();
                    
	                if (location.distance(player.getLocation()) <= 4) {
	                    location.setY(damager.getLocation().getY() + 3);
	                    Vector knockback = location.getDirection().multiply(-1).normalize().multiply(2);
	                    damager.setVelocity(knockback);
	                    damager.damage(1);
	                }

                    player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.5f, 0.8f);
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 1.5f, 0.8f);
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SHULKER_BULLET_HIT, 1.0f, 1.2f);

                    player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, damager.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);
                    player.getWorld().spawnParticle(Particle.CRIT_MAGIC, player.getLocation().add(0, 1, 0), 100, 0.5, 0.5, 0.5, 0.1);

                    ((LivingEntity) damager).addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1, false, false, false));
                    
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.of("#e9ffab") + "Атаку відбито!"));
                    
                    if (player.getInventory().getItemInMainHand().getType().equals(Material.SHIELD)) {
                    	player.getInventory().setItemInMainHand(damageItem(player, player.getInventory().getItemInMainHand(), 1));
                    }
                    if (player.getInventory().getItemInOffHand().getType().equals(Material.SHIELD)) {
                    	player.getInventory().setItemInOffHand(damageItem(player, player.getInventory().getItemInOffHand(), 1));
                    }
                    
                    this.cancel();

                } else if (ticks >= 10) {
                    player.getWorld().spawnParticle(Particle.SMOKE_LARGE, player.getLocation(), 30, 0.5, 0.5, 0.5, 0.1);
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f);
                    player.damage(event.getDamage());
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.of("#ffabb2") + "Невдале парирування!"));
                    this.cancel();
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

	public ItemStack damageItem(Player player, ItemStack item, int damage) {
	    if (item != null && item.getItemMeta() instanceof Damageable && player.getGameMode() != GameMode.CREATIVE) {
	        Damageable damageable = (Damageable) item.getItemMeta();
	        int newDamage = damageable.getDamage() + damage;
	        damageable.setDamage(newDamage);
	        item.setItemMeta(damageable);
	        if (newDamage >= item.getType().getMaxDurability()) {
	            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
	            return null;
	        }
	    }
	    return item;
	}
    
    private void displayParryEffects(Player player) {
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
    //    player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 0.5f);
    }
}
