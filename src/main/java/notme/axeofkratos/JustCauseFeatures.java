package notme.axeofkratos;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

public class JustCauseFeatures implements Listener {

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, Brain.getInstance());
        
        ItemStack grapplingHook = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = grapplingHook.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE+"Гак-Кішка");
        meta.setCustomModelData(7);
        grapplingHook.setItemMeta(meta);
        
        NamespacedKey key = new NamespacedKey(Brain.getInstance(), "grappling_hook");
        ShapelessRecipe recipe = new ShapelessRecipe(key, grapplingHook);
        
        recipe.addIngredient(Material.FISHING_ROD);
        recipe.addIngredient(Material.IRON_INGOT);
        
        if (Bukkit.getRecipe(key) == null) {
        //	Bukkit.addRecipe(recipe);
        }
        
        Brain.getInstance().getLogger().info(String.valueOf(getClass().getSimpleName()) + " module has been initialized!");
    }
    
    @EventHandler
    public void onPlayerCraftItem(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        if (result != null && result.getType() == Material.FISHING_ROD) {
            ItemMeta meta = result.getItemMeta();
            if (meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == 7) {
                if (!event.getWhoClicked().hasPermission("perks.grappling_hook")) {
                    event.getWhoClicked().sendMessage(ChatColor.RED + "Придбайте перк, щоб скрафтити гак-кішку.");
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerCraftItem(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result != null && result.getType() == Material.FISHING_ROD) {
            ItemMeta meta = result.getItemMeta();
            if (meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == 7) {
                if (!event.getView().getPlayer().hasPermission("perks.grappling_hook")) {
                    event.getView().getPlayer().sendMessage(ChatColor.RED + "Придбайте перк, щоб скрафтити гак-кішку.");
                    event.getInventory().setResult(null);
                }
            }
        }
    }
    
    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL && player.hasMetadata("hook")) {
                player.removeMetadata("hook", Brain.getInstance());
                if (player.getFallDistance()-3 <= 3) {
                	event.setCancelled(true);
                }
                
            }
        }
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getHand() == null) {
        	return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getHand());
        ItemMeta meta =	item.getItemMeta();
        FishHook hook = event.getHook();
        
        if (!meta.hasCustomModelData() || meta.getCustomModelData() != 7) {
            return;
        }

        if (!player.hasPermission("perks.grappling_hook")) {
        	player.sendMessage(ChatColor.RED + "Придбайте перк, щоб користуватись гаком-кішкою.");
            return;
        }
        
        event.getHook().setCustomName("grappling_hook");
        event.getHook().setCustomNameVisible(false);
        
        hook.setVelocity(event.getHook().getVelocity().multiply(2.5));

        Bukkit.getScheduler().runTaskTimer(Brain.getInstance(), () -> {
            if (hook.isDead() || !hook.isValid()) {
                return;
            }
            if (hook.getLocation().distance(player.getLocation()) >= 20) {
                hook.remove();
            }
            if (hook.getLocation().getBlock().getType() == Material.WATER) {
                hook.remove();
            }
            if (hook.isOnGround()) {
            	grapplePlayer(player, hook);
            	player.getInventory().setItem(event.getHand(), damageItem(player, item, 1));
            }
            
            boolean hookOnSolidBlock =  !hook.getLocation().clone().add(1, 0, 0).getBlock().isPassable() ||
					                    !hook.getLocation().clone().add(-1, 0, 0).getBlock().isPassable() ||
					                    !hook.getLocation().clone().add(0, 1, 0).getBlock().isPassable() ||
					                    !hook.getLocation().clone().add(0, -1, 0).getBlock().isPassable() ||
					                    !hook.getLocation().clone().add(0, 0, 1).getBlock().isPassable() ||
					                    !hook.getLocation().clone().add(0, 0, -1).getBlock().isPassable();
            
            if (hookOnSolidBlock) {
            	grapplePlayer(player, hook);
            	player.getInventory().setItem(event.getHand(), damageItem(player, item, 1));
            }
            
        }, 0L, 1L);
        

    }
    
    public void grapplePlayer(Player player, FishHook hook) {
        Location hookLocation = hook.getLocation().clone();
        Location playerLocation = player.getLocation();

        hookLocation.add(0, 1.5, 0);

        Vector direction = hookLocation.toVector().subtract(playerLocation.toVector());
        double distance = direction.length();
        direction.normalize();

        double gravity = 0.08;
        double flightTime = distance / 1.5;

        double verticalVelocity = (hookLocation.getY() - playerLocation.getY()) / flightTime + 0.5 * gravity * flightTime;

        Vector velocity = direction.multiply(distance / flightTime);
        velocity.setY(verticalVelocity);

        player.getWorld().spawnParticle(Particle.CRIT_MAGIC, playerLocation, 5);
        player.getWorld().playSound(playerLocation, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.0f);

        player.setVelocity(velocity);

        player.setMetadata("hook", new FixedMetadataValue(Brain.getInstance(), true));

        hook.remove();
    }

    
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
    	
        if (!(event.getEntity() instanceof FishHook)) {
            return;
        }
        
        FishHook hook = (FishHook) event.getEntity();
        Player player = (Player) event.getEntity().getShooter();
        
        if (!"grappling_hook".equals(hook.getCustomName())) {
            return;
        }
        grapplePlayer(player, hook);

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
    
}
