/*
 * This file is part of EnchantmentsPlus, a bukkit plugin.
 * Copyright (c) 2015 - 2020 Zedly and Zenchantments contributors.
 * Copyright (c) 2020 - 2021 Geolykt and EnchantmentsPlus contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package de.geolykt.enchantments_plus.enchantments;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;

import de.geolykt.enchantments_plus.Config;
import de.geolykt.enchantments_plus.CustomEnchantment;
import de.geolykt.enchantments_plus.Storage;
import de.geolykt.enchantments_plus.compatibility.CompatibilityAdapter;
import de.geolykt.enchantments_plus.enums.BaseEnchantments;
import de.geolykt.enchantments_plus.enums.Hand;
import de.geolykt.enchantments_plus.util.Tool;

public class Weight extends CustomEnchantment {

    public static final int ID = 67;

    /**
     * Is put on the PDC of a player to mark that the player has the Enchantment active (the slowness was made by the plugin).
     * Used to prevent abuse so players cannot remove permanent slowness effects.
     * @since 2.1.3
     */
    public static final NamespacedKey ACTIVE = new NamespacedKey(Storage.plugin, "weight_active");

    @Override
    public Builder<Weight> defaults() {
        return new Builder<>(Weight::new, ID)
            .all("Slows the player down but makes them stronger and more resistant to knockback",
                    new Tool[]{Tool.BOOTS},
                    "Weight",
                    4,
                    Hand.NONE,
                    BaseEnchantments.MEADOR, BaseEnchantments.SPEED);
    }

    public Weight() {
        super(BaseEnchantments.WEIGHT);
    }

    private static final EquipmentSlot[] SLOTS = 
            new EquipmentSlot[] {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    @Override
    public boolean onBeingHit(EntityDamageByEntityEvent evt, int level, boolean usedHand) {
        if (!(evt.getEntity() instanceof Player) || //check if victim is a player
            !(evt.getDamager() instanceof LivingEntity) || //check if the damager is alive
            //check if the victim (player) can damage the attacker
            !ADAPTER.attackEntity((LivingEntity) evt.getDamager(), (Player) evt.getEntity(), 0, false)) {
            return true;
        }
        Player player = (Player) evt.getEntity();
        if (evt.getDamage() < player.getHealth()) {
            // FIXME this looks like bad practice - plugins may not have a say in this and
            // this would prevent the MONITOR priority of being used.
            // This should be changed into something better sometime in the future
            evt.setCancelled(true);
            player.damage(evt.getDamage());
            player.setVelocity(player.getLocation().subtract(evt.getDamager().getLocation()).toVector()
                                         .multiply((float) (1 / (level * power + 1.5))));
            for (EquipmentSlot slot : SLOTS) {
                final ItemStack s = player.getInventory().getItem(slot);
                if (CustomEnchantment.hasEnchantment(Config.get(player.getWorld()), s, BaseEnchantments.WEIGHT) && CompatibilityAdapter.damageItem2(s, level)) {
                    player.getInventory().setItem(slot, new ItemStack(Material.AIR));
                }
            }
        }
        return true;
    }

    @Override
    public boolean onScan(Player player, int level, boolean usedHand) {
        if (player.hasPotionEffect(PotionEffectType.SLOWNESS)) {
            return false;
        } else {
            player.addPotionEffect(PotionEffectType.SLOWNESS.createEffect(Integer.MAX_VALUE, (int) (level*power)));
            player.addPotionEffect(PotionEffectType.STRENGTH.createEffect(Integer.MAX_VALUE, (int) (level*power)));
            player.getPersistentDataContainer().set(ACTIVE, PersistentDataType.BYTE, (byte) 1);
            return true;
        }
    }
}
