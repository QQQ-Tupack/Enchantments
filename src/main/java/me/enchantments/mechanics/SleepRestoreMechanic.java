package me.enchantments.mechanics;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class SleepRestoreMechanic extends Mechanic {

    @Override
    public String getId() {
        return "sleep_restore";
    }

    @Override
    public String getName() {
        return "Солодких снів";
    }

    @Override
    public String getDescription() {
        return "Повністю відновлює здоров'я та ситість, коли гравець лягає спати";
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public List<Material> getSupportedItems() {
        return List.of(Material.TOTEM_OF_UNDYING);
    }

    @Override
    public List<EnchantSlot> getSlots() {
        return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND);
    }

    private ItemStack totem(Player p) {
        if (p.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING) return p.getInventory().getItemInMainHand();
        if (p.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING) return p.getInventory().getItemInOffHand();
        return null;
    }

    @EventHandler
    public void onSleep(PlayerBedEnterEvent e) {
        if (!isMechanicEnabled()) return;
        if (e.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;

        Player player = e.getPlayer();
        if (levelOf(totem(player)) == 0) return;

        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(maxHealth);
        player.setFoodLevel(20);
        player.setSaturation(20f);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 8, 0.4, 0.4, 0.4, 0.02);
    }
}
