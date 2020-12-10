package de.geolykt.enchantments_plus;

import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * This is used to manage enchantment cooldowns of players on the server.
 *  The class might be removed in future development builds and integrated elsewhere
 * @since 1.0.0
 */
public class EnchantPlayer {

    /**
     * Returns true if the given enchantment name is disabled for the player,
     * otherwise false
     * @param player The player
     * @param ench The enchantment
     * @return True if the player was disabled
     * @since 3.0.0
     */
    public static boolean isDisabled(@NotNull Player player, @NotNull CustomEnchantment ench) {
        return player.getPersistentDataContainer().getOrDefault(ench.getKey(), PersistentDataType.LONG, (long) 0.0)
                == Long.MAX_VALUE;
    }

    /**
     * Returns the remaining cooldown for the given enchantment in milliseconds that is left, may be negative.
     * The function does not take care of when an enchantment is disabled, use {@link #isDisabled(Player, CustomEnchantment)}
     * instead.
     * 
     * @param player the player the query is valid for
     * @param ench the enchantment
     * @return the cooldown remaining for the given enchantment in milliseconds
     * @since 3.0.0
     */
    public static long getCooldown(@NotNull Player player, @NotNull CustomEnchantment ench) {
        return player.getPersistentDataContainer().getOrDefault(ench.getKey(), PersistentDataType.LONG, (long) 0.0)
                - System.currentTimeMillis();
    }

    /**
     * Returns the time when the cooldown has ended, this is presented by the amount of milliseconds that have passed since
     *  the epoch start, or Long.MAX_VALUE to represent that the enchantment is disabled
     * 
     * @param player the player the query is valid for
     * @param ench the enchantment
     * @return the time at which the cooldown ends, or Long.MAX_VALUE if it should never end.
     * @since 3.0.0
     */
    public static long getCooldownEnd(@NotNull Player player, @NotNull CustomEnchantment ench) {
        return player.getPersistentDataContainer().getOrDefault(ench.getKey(), PersistentDataType.LONG, (long) 0.0);
    }

    /**
     * Sets the given enchantment cooldown to the given amount of milliseconds; silently fails if the enchantment
     * is disabled
     * 
     * @param player The player
     * @param enchantment The enchantment
     * @param millis The milliseconds for the cooldown
     * @since 3.0.0
     */
    public static void setCooldown(@NotNull Player player, @NotNull CustomEnchantment enchantment, int millis) {
        if (!isDisabled(player, enchantment)) {
            player.getPersistentDataContainer().set(enchantment.getKey(), PersistentDataType.LONG, millis + System.currentTimeMillis());
        }
    }

    /**
     * Disables the given enchantment for the player
     * @param player The player that should be targeted in the operation
     * @param ench The targeted enchantment
     * @since 3.0.0
     */
    public static void disable(@NotNull Player player, @NotNull CustomEnchantment ench) {
        player.getPersistentDataContainer().set(ench.getKey(), PersistentDataType.LONG, Long.MAX_VALUE);
    }

    /**
     * Enables the given enchantment for the player and silently fails if the enchantment is not disabled.
     * @param player The player that should be targeted in the operation
     * @param ench The enchantment to enable for the player
     * @since 3.0.0
     */
    public static void enable(@NotNull Player player, @NotNull CustomEnchantment ench) {
        if (isDisabled(player, ench)) {
            player.getPersistentDataContainer().set(ench.getKey(), PersistentDataType.LONG, (long) 0);
        }
    }

    /**
     * Disables all enchantments for the player
     * @param player The player that is the target of the operation
     * @since 3.0.0 the cooldown remaining for the given enchantment in ticks
     */
    public static void disableAll(@NotNull Player player) {
        for (CustomEnchantment enchant : Config.allEnchants) {
            disable(player, enchant);
        }
    }

    /**
     * Enables all enchantments for the player
     * @param player
     * @since 3.0.0
     */
    public static void enableAll(@NotNull Player player) {
        for (CustomEnchantment enchant : Config.allEnchants) {
            enable(player, enchant);
        }
    }
}
