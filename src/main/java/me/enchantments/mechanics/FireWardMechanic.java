package me.enchantments.mechanics;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import me.enchantments.main.Mechanic;

import java.util.List;
import java.util.Map;

public final class FireWardMechanic extends Mechanic {

    @Override public String getId() { return "fire_ward"; }
    @Override public String getName() { return "Пекельний оберіг"; }
    @Override public String getDescription() { return "При отриманні шкоди від вогню тимчасово дає вогнестійкість"; }
    @Override public int getMaxLevel() { return 3; }
    @Override public List<Material> getSupportedItems() { return List.of(Material.TOTEM_OF_UNDYING); }
    @Override public List<EnchantSlot> getSlots() { return List.of(EnchantSlot.MAINHAND, EnchantSlot.OFFHAND); }

    @Override
    public Map<String, Object> getDefaults() {
        return Map.of(
            "duration-ticks", 200,
            "duration-ticks-per-level", 40,
            "cooldown-ticks", 1200,
            "cooldown-reduction-per-level-ticks", 200
        );
    }

    @Override
    protected void onDamaged(EntityDamageEvent event, Player player, int level) {
        var cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.FIRE
                && cause != EntityDamageEvent.DamageCause.FIRE_TICK
                && cause != EntityDamageEvent.DamageCause.LAVA) return;

        ItemStack item = heldItem(player);
        if (!ready(player, item)) return;

        int duration = getInt("duration-ticks") + getInt("duration-ticks-per-level") * (level - 1);
        int cd = getInt("cooldown-ticks") - getInt("cooldown-reduction-per-level-ticks") * (level - 1);
        cooldown(player, item, cd);

        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, duration, 0, false, true, true));
        particles(player.getLocation().add(0, 1, 0), Particle.FLAME, 20, 0.3);
        soundFor(player, Sound.ITEM_FIRECHARGE_USE, 1f, 1f);
    }

    private ItemStack heldItem(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        return levelOf(main) > 0 ? main : player.getInventory().getItemInOffHand();
    }
}
