package notme.axeofkratos;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class AxeOfKratos implements Listener {

	public void register() {
		Bukkit.getPluginManager().registerEvents(this, Brain.getInstance());
        Brain.getInstance().getLogger().info(String.valueOf(getClass().getSimpleName()) + " module has been initialized! (Perms: perks.axe.collect-items, perks.axe.callback-damage)");
	}
	
	@EventHandler
	public void onPlayerThrowAxe(PlayerInteractEvent event) {

	    Player player = event.getPlayer();
	    ItemStack axe = player.getInventory().getItemInMainHand();
	    
        if (!player.hasPermission("axe.throw")) {
        	return;
        }
		
        if (player.getGameMode() == GameMode.CREATIVE) {
        	return;
        }
        
        if (player.isBlocking()) {
        	return;
        }
        
        if (!(event.getAction() == Action.RIGHT_CLICK_AIR ||
        	(event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock().isPassable()))) {
        	return;
        }
        
	    if (axe.getType().toString().contains("_AXE")) {
	        World world = player.getWorld();
	        Location startLoc = player.getEyeLocation();
	        Vector direction = startLoc.getDirection().normalize().multiply(2);
	        player.getInventory().setItemInMainHand(null);
	        Item thrownAxe = world.dropItem(startLoc, axe);
	        thrownAxe.setPickupDelay(Integer.MAX_VALUE);
	        thrownAxe.setVelocity(direction);
	        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1, 1);
	        new BukkitRunnable() {
	            int ticks = 0;
	            @Override
	            public void run() {
	                if (ticks > 20) {
	                    this.cancel();
	                    returnAxe(player, thrownAxe, player.getInventory().getHeldItemSlot());
	                }
	                for (Entity entity : thrownAxe.getNearbyEntities(1, 1, 1)) {
	                    if (entity instanceof LivingEntity && entity != player) {
	                        applyAxeDamage(thrownAxe.getItemStack(), (LivingEntity) entity, player);
	                        world.playSound(entity.getLocation(), Sound.ENTITY_PLAYER_HURT, 1, 1);
	                        world.spawnParticle(Particle.CRIT, entity.getLocation(), 10);
	                        thrownAxe.teleport(entity.getLocation());
	                        returnAxe(player, thrownAxe, player.getInventory().getHeldItemSlot());
	                        this.cancel();
	                        return;
	                    }
	                }
	                world.spawnParticle(Particle.SWEEP_ATTACK, thrownAxe.getLocation(), 2);
	                ticks++;
	            }
	        }.runTaskTimer(Brain.getInstance(), 0, 1);
	    }
	}

	// Метод повернення сокири до гравця
	private void returnAxe(Player player, Item thrownAxe, int slot) {
	    new BukkitRunnable() {
	        int ticks = 0;

	        @Override
	        public void run() {
	            Location axeLoc = thrownAxe.getLocation();
	            Location playerLoc = player.getEyeLocation();
	            Vector direction = getDirection(axeLoc, playerLoc);

	            if (player.hasPermission("perks.axe.collect-items")) {
	                collectItemsAndXp(player, thrownAxe);
	            }

	            moveAxe(thrownAxe, axeLoc, direction);

	            if (isAxeCloseEnough(axeLoc, playerLoc) || ticks >= 60) {
	                returnAxeToPlayer(player, thrownAxe, slot, playerLoc, ticks);
	                this.cancel();
	            }

	            if (player.hasPermission("perks.axe.callback-damage")) {
	                applyDamageToEntities(thrownAxe, player);
	            }

	            ticks++;
	        }

	    }.runTaskTimer(Brain.getInstance(), 0, 1);
	}

	private Vector getDirection(Location axeLoc, Location playerLoc) {
	    Vector direction = playerLoc.toVector().subtract(axeLoc.toVector()).normalize();
	    direction.setY(direction.getY() + 0.2);
	    return direction;
	}

	private void moveAxe(Item thrownAxe, Location axeLoc, Vector direction) {
	    axeLoc.add(direction.multiply(0.5));
	    thrownAxe.setVelocity(direction.multiply(0.8));
	    thrownAxe.getWorld().spawnParticle(Particle.CLOUD, thrownAxe.getLocation(), 1);
	}

	private boolean isAxeCloseEnough(Location axeLoc, Location playerLoc) {
	    return axeLoc.distance(playerLoc) < 1.5;
	}

	private void returnAxeToPlayer(Player player, Item thrownAxe, int slot, Location playerLoc, int ticks) {
	    ItemStack axe = thrownAxe.getItemStack();
	    thrownAxe.remove();

	    BukkitRunnable returnTask = new BukkitRunnable() {
	        @Override
	        public void run() {
	            ItemStack item = player.getInventory().getItem(slot);
	            player.getInventory().setItem(slot, damageItem(player, axe, 1));
	            if (item != null) {
	                player.getInventory().addItem(item);
	            }
	            player.getWorld().playSound(playerLoc, Sound.ITEM_TRIDENT_THROW, 0.4f, 1);
	        }
	    };

	    if (ticks <= 20) {
	        returnTask.runTaskLater(Brain.getInstance(), 20 - ticks);
	    } else {
	        returnTask.run();
	    }
	}

	private void applyDamageToEntities(Item thrownAxe, Player player) {
	    for (Entity entity : thrownAxe.getNearbyEntities(1.5, 1.5, 1.5)) {
	        if (entity instanceof LivingEntity && entity != player) {
	            applyAxeDamage(thrownAxe.getItemStack(), (LivingEntity) entity, player);
	            thrownAxe.getWorld().spawnParticle(Particle.CRIT, entity.getLocation(), 1);
	        }
	    }
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
	
	private void collectItemsAndXp(Player player, Item thrownAxe) {
	    for (Entity entity : thrownAxe.getNearbyEntities(2, 2, 2)) {
	        if (entity instanceof ExperienceOrb) {
	            ExperienceOrb orb = (ExperienceOrb) entity;
	            Vector vector = player.getLocation().toVector().subtract(orb.getLocation().toVector()).normalize().multiply(0.8);
	            orb.setVelocity(vector);
	        }
	    }
	    for (Entity entity : thrownAxe.getNearbyEntities(2, 2, 2)) {
	        if (entity instanceof Item && entity != thrownAxe) {
	            Item droppedItem = (Item) entity;
	            Location itemLoc = droppedItem.getLocation();
	            Location playerLoc = player.getLocation();

	            Vector toPlayer = playerLoc.toVector().subtract(itemLoc.toVector()).normalize().multiply(0.8);
	            itemLoc.add(toPlayer);
	            
	            droppedItem.teleport(itemLoc);
	        }
	    }
	}

    public void applyAxeDamage(ItemStack axe, LivingEntity target, LivingEntity attacker) {
        double baseDamage = calculateBaseDamage(axe.getType());
        double enchantmentBonus = calculateEnchantmentBonus(axe);

        double totalDamage = baseDamage + enchantmentBonus;
        target.damage(totalDamage, attacker);
    }

    private double calculateBaseDamage(Material axeType) {
        switch (axeType) {
            case WOODEN_AXE:
                return 3;
            case STONE_AXE:
                return 4;
            case IRON_AXE:
                return 5;
            case DIAMOND_AXE:
                return 6;
            case NETHERITE_AXE:
                return 7;
            case GOLDEN_AXE:
                return 3;
            default:
                return 1;
        }
    }

    private double calculateEnchantmentBonus(ItemStack axe) {
        double enchantmentBonus = 0;

        if (axe.containsEnchantment(Enchantment.DAMAGE_ALL)) {
            int level = axe.getEnchantmentLevel(Enchantment.DAMAGE_ALL);
            enchantmentBonus += 1 + (level - 1) * 0.5;
        }

        if (axe.containsEnchantment(Enchantment.DAMAGE_UNDEAD)) { // Smite
            int level = axe.getEnchantmentLevel(Enchantment.DAMAGE_UNDEAD);
            enchantmentBonus += level * 2.5;
        }

        if (axe.containsEnchantment(Enchantment.DAMAGE_ARTHROPODS)) {
            int level = axe.getEnchantmentLevel(Enchantment.DAMAGE_ARTHROPODS);
            enchantmentBonus += level * 2.5;
        }

        return enchantmentBonus;
    }

	
}
