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
package de.geolykt.enchantments_plus.compatibility;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;

import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import static org.bukkit.potion.PotionEffectType.*;

import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.palmergames.bukkit.towny.object.TownyPermission;
import com.palmergames.bukkit.towny.utils.PlayerCacheUtil;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;

import de.geolykt.enchantments_plus.Storage;
import de.geolykt.enchantments_plus.util.ColUtil;
import de.geolykt.enchantments_plus.util.Tool;

public class CompatibilityAdapter {

    /**
     * Constructs the class and starts a Task on the next tick to initialise it further (scans methods from other plugins or spigot)
     * @param plugin The plugin that is used to initialise the task.
     */
    public CompatibilityAdapter(Plugin plugin) {
        Bukkit.getScheduler().runTaskLater(plugin, this::scanMethods, 0L);
    }

    private EnumSet<Material> grownCrops;
    private EnumSet<Material> melonCrops;
    private EnumSet<Material> airs;
    private EnumSet<Material> ores;
    private EnumSet<Material> unbreakable;
    private EnumSet<Material> laserDenylist;
    private EnumSet<Material> terraformerAllowlist;
    private EnumSet<Material> shredAllowlistPickaxes;
    private EnumSet<Material> shredAllowlistShovels;
    private EnumSet<Material> lumberTrunkBlocks;
    private EnumSet<Material> lumberAllowBlocks;

    private EnumSet<Biome> dryBiomes;

    private EnumMap<Material, Material> spectralMaterialConversion;
    private EnumMap<EntityType, EntityType> transformationMap;

    private EnumSet<Material> getMaterialSet(FileConfiguration config, String path) {
        EnumSet<Material> es = EnumSet.noneOf(Material.class);
        for (String materialName : config.getStringList(path)) {
             Material material = Material.matchMaterial(materialName);
             if (material != null) {
                es.add(material);
             }
        }
        return es;
    }

    /**
     * Load the magic compatibility file
     * 
     * @param config The appropriate FileConfiguration
     */
    public void loadValues(FileConfiguration config) {
        Storage.plugin.getLogger().info("Loading magic compatibillity file, if this step fails you should notify the devs about this.");
        grownCrops = getMaterialSet(config, "grownCrops");
        melonCrops = getMaterialSet(config, "melonCrops");
        airs = getMaterialSet(config, "airs");
        ores = getMaterialSet(config, "ores");
        unbreakable = getMaterialSet(config, "unbreakable");
        laserDenylist = getMaterialSet(config, "laserDenylist");
        terraformerAllowlist = getMaterialSet(config, "terraformerAllowlist");
        shredAllowlistPickaxes = getMaterialSet(config, "shredAllowlistPickaxes");
        shredAllowlistShovels = getMaterialSet(config, "shredAllowlistShovels");
        lumberTrunkBlocks = getMaterialSet(config, "lumberTrunks");
        lumberAllowBlocks = getMaterialSet(config, "lumberAllowlist");

        for (String s : config.getStringList("terraformerAllowlistTags")) {
            try {
                try {
                    Field f = Tag.class.getDeclaredField(s);
                    @SuppressWarnings("unchecked")
                    Tag<? extends Material> tag = (Tag<? extends Material>) f.get(null);
                    terraformerAllowlist.addAll(tag.getValues());
                } catch (NoSuchFieldException e) {
                    Storage.plugin.getLogger().warning(s + " is not a known tag (located within the terraformerAllowlistTags list); Skipping entry.");
                    continue;
                }
            } catch (IllegalArgumentException | IllegalAccessException | SecurityException e) {
                Bukkit.getLogger().severe("Looks like an issue occoured with the Enchantments+ plugin. Please report this to the devs as this is something severe!");
                e.printStackTrace();
            }
        }
        dryBiomes = EnumSet.noneOf(Biome.class);
        for (String s : config.getStringList("dryBiomes")) {
            try {
                dryBiomes.add(Biome.valueOf(s));
            } catch (IllegalArgumentException e) {
                Storage.plugin.getLogger().warning(s + " is not a known biome (located within the terraformerAllowlistTags list); Skipping entry.");
            }
        }
        spectralMaterialConversion = new EnumMap<>(Material.class);
        for (String s : config.getStringList("spectralConversions")) {
            Material mk = Material.matchMaterial(s.split(":")[0]);
            Material mv = Material.matchMaterial(s.split(":")[1]);
            if (mk == null) {
                if (mv == null) {
                    Storage.plugin.getLogger().warning("Both key and value of the entry \"" + s + "\" in the spectralConversions list are invalid; Skipping entry.");
                } else {
                    Storage.plugin.getLogger().warning("The key of the entry \"" + s + "\" in the spectralConversions list is invalid; Skipping entry.");
                }
            } else if (mv == null) {
                Storage.plugin.getLogger().warning("The value of the entry \"" + s + "\" in the spectralConversions list is invalid; Skipping entry.");
            } else {
                spectralMaterialConversion.put(mk, mv);
            }
        }
        transformationMap = new EnumMap<>(EntityType.class);
        for (String s : config.getStringList("transformation")) {
            EntityType mk = null;
            try {
                mk = EntityType.valueOf(s.split(":")[0]);
            } catch (IllegalArgumentException ignore) {} // Prevent Exceptions if the entity is not known

            EntityType mv = null;
            try {
                mv = EntityType.valueOf(s.split(":")[1]);
            } catch (IllegalArgumentException ignore) {} // Prevent Exceptions if the entity is not known

            if (mk == null) {
                if (mv == null) {
                    Storage.plugin.getLogger().warning("Both key and value of the entry \"" + s + "\" in the transformation list are invalid; Skipping entry.");
                } else {
                    Storage.plugin.getLogger().warning("The key of the entry \"" + s + "\" in the transformation list is invalid; Skipping entry.");
                }
            } else if (mv == null) {
                Storage.plugin.getLogger().warning("The value of the entry \"" + s + "\" in the transformation list is invalid; Skipping entry.");
            } else {
                transformationMap.put(mk, mv);
            }
        }

        Tool.ALL.setMaterials(getMaterialSet(config, "tools.all"));
        
        Tool.AXE.setMaterials(getMaterialSet(config, "tools.axe"));
        Tool.PICKAXE.setMaterials(getMaterialSet(config, "tools.pickaxe"));
        Tool.SHOVEL.setMaterials(getMaterialSet(config, "tools.shovel"));
        Tool.HOE.setMaterials(getMaterialSet(config, "tools.hoe"));

        Tool.HELMET.setMaterials(getMaterialSet(config, "tools.helmet"));
        Tool.CHESTPLATE.setMaterials(getMaterialSet(config, "tools.chestplate"));
        Tool.WINGS.setMaterials(getMaterialSet(config, "tools.wings"));
        Tool.LEGGINGS.setMaterials(getMaterialSet(config, "tools.leggings"));
        Tool.BOOTS.setMaterials(getMaterialSet(config, "tools.boots"));

        Tool.SWORD.setMaterials(getMaterialSet(config, "tools.sword"));
        Tool.BOW.setMaterials(getMaterialSet(config, "tools.bow"));

        Tool.ROD.setMaterials(getMaterialSet(config, "tools.rod"));
        Tool.SHEARS.setMaterials(getMaterialSet(config, "tools.shears"));
    }

    private static final Random RND = new Random();

    public EnumSet<Material> grownCrops() {
        return grownCrops;
    }

    public EnumSet<Material> grownMelon() {
        return melonCrops;
    }

    public EnumSet<Material> airs() {
        return airs;
    }

    public EnumSet<Material> ores() {
        return ores;
    }

    public EnumSet<Biome> dryBiomes() {
        return dryBiomes;
    }

    public EnumSet<Material> unbreakableBlocks() {
        return unbreakable;
    }

    public EnumSet<Material> laserDenylist() {
        return laserDenylist;
    }

    public EnumSet<Material> terraformerMaterials() {
        return terraformerAllowlist;
    }

    /**
     * Returns the Maps used for the Transformation enchantment, while ideally the key-value pairs will point towards each other directly
     * or indirectly, this may not always guaranteed to be the case. Additionally, if both Key and Value is either CREEPER or RABBIT, then
     * their charged/killer bunny state should be inverted.
     * @return A mapping used by the transformation enchantment
     * @since 2.1.0
     */
    public EnumMap<EntityType, EntityType> getTransformationMap() {
        return transformationMap;
    }

    public LivingEntity transformationCycle(LivingEntity ent, Random rnd) {
        EntityType newType = transformationMap.get(ent.getType());
        if (newType == null)
            return null;
        LivingEntity newEnt = (LivingEntity) ent.getWorld().spawnEntity(ent.getLocation(), newType);

        switch (newType) {
        case HORSE:
            ((Horse) newEnt).setColor(Horse.Color.values()[rnd.nextInt(Horse.Color.values().length)]);
            ((Horse) newEnt).setStyle(Horse.Style.values()[rnd.nextInt(Horse.Style.values().length)]);
            break;
        case RABBIT:
            if (((Rabbit) ent).getRabbitType().equals(Rabbit.Type.THE_KILLER_BUNNY)) {
                ((Rabbit) newEnt).setRabbitType(Rabbit.Type.values()[rnd.nextInt(Rabbit.Type.values().length - 1)]);
            } else {
                ((Rabbit) newEnt).setRabbitType(Rabbit.Type.THE_KILLER_BUNNY);
            }
            break;
        case VILLAGER:
            Villager.Profession career = Villager.Profession.values()[rnd.nextInt(Villager.Profession.values().length)];
            ((Villager) newEnt).setProfession(career);
            break;
        case LLAMA:
            ((Llama) newEnt).setColor(Llama.Color.values()[rnd.nextInt(Llama.Color.values().length)]);
            break;
        case TROPICAL_FISH:
            ((TropicalFish) newEnt).setBodyColor(DyeColor.values()[rnd.nextInt(DyeColor.values().length)]);
            ((TropicalFish) newEnt).setPatternColor(DyeColor.values()[rnd.nextInt(DyeColor.values().length)]);
            ((TropicalFish) newEnt).setPattern(TropicalFish.Pattern.values()[rnd.nextInt(TropicalFish.Pattern.values().length)]);
            break;
        case PARROT:
            ((Parrot) newEnt).setVariant(Parrot.Variant.values()[rnd.nextInt(Parrot.Variant.values().length)]);
            break;
        case SHEEP:
            ((Sheep) newEnt).setColor(DyeColor.values()[rnd.nextInt(DyeColor.values().length)]);
            break;
        case CREEPER:
            if (ent.getType() == EntityType.CREEPER)
                ((Creeper) newEnt).setPowered(!((Creeper) ent).isPowered());
            break;
        case MUSHROOM_COW:
            ((MushroomCow) newEnt).setVariant(MushroomCow.Variant.values()[rnd.nextInt(MushroomCow.Variant.values().length)]);
            break;
        default:
            break;
        }
        newEnt.setCustomName(ent.getCustomName());
        newEnt.setCustomNameVisible(ent.isCustomNameVisible());
        return ent;
    }

    public EnumSet<Material> shredPicks() {
        return shredAllowlistPickaxes;
    }

    public EnumSet<Material> shredShovels() {
        return shredAllowlistShovels;
    }

    /**
     * Returns the base lumber trunk blocks, which are usually logs but can be user-modified to returns arbitrary Materials. <br>
     * More specifically, this list is used to calculate which blocks will be removed when the lumber enchantment is used.
     * @return An EnumSet with arbitrary values, usually logs.
     * @since 1.2.0
     */
    public EnumSet<Material> lumberTrunk() {
        return lumberTrunkBlocks;
    }

    /**
     * Returns the additional lumber allowlist blocks, which usually are an extension of the {@link CompatibilityAdapter#lumberTrunk()}
     * set. <br> The internal usage is to calculate where the BFS Algorithm should not stop and as such all lumberTrunk Materials should
     * exist in this set. <br> It is useful to do big-tree calculations as some bigger tree won't be purely be made out of logs. <br>
     * However may also contain blocks that should not be removed (e.g. leaves as per default)
     * @return An EnumSet with arbitrary values
     * @since 1.2.0
     */
    public EnumSet<Material> lumberAllow() {
        return lumberAllowBlocks;
    }

    public List<Material> persephoneCrops() {
        return Arrays.asList(Material.WHEAT, Material.POTATO, Material.CARROT, Material.BEETROOT, Material.NETHER_WART,
                Material.SOUL_SAND, Material.FARMLAND);
    }

    public List<PotionEffectType> potionPotions() {
        return Arrays.asList(ABSORPTION,
                DAMAGE_RESISTANCE, FIRE_RESISTANCE, SPEED, JUMP, INVISIBILITY, INCREASE_DAMAGE, HEALTH_BOOST, HEAL,
                REGENERATION, NIGHT_VISION, SATURATION, FAST_DIGGING, WATER_BREATHING, DOLPHINS_GRACE);
    }

    // TODO what do these values even mean?
    private static final int[] GLUTTONY_FOOD_LEVELS = {4, 5, 1, 6, 5, 3, 1, 6, 5, 6, 8, 5, 6, 2, 1, 2, 6, 8, 10, 8}; 

    public int[] gluttonyFoodLevels() {
        return GLUTTONY_FOOD_LEVELS;
    }

    private static final double[] GLUTTONY_SATURATIONS = {2.4, 6, 1.2, 7.2, 6, 3.6, 0.2, 7.2, 6, 9.6, 12.8, 6, 9.6, 0.4, 0.6,
            1.2, 7.2, 4.8, 12, 12.8};

    public double[] gluttonySaturations() {
        return GLUTTONY_SATURATIONS;
    }

    private final Material[] GLUTTONY_FOOD_ITEMS = new Material[]{
            Material.APPLE, Material.BAKED_POTATO, Material.BEETROOT, 
            Material.BEETROOT_SOUP, Material.BREAD, Material.CARROT, Material.TROPICAL_FISH, Material.COOKED_CHICKEN, 
            Material.COOKED_COD, Material.COOKED_MUTTON, Material.COOKED_PORKCHOP, Material.COOKED_RABBIT,
            Material.COOKED_SALMON, Material.COOKIE, Material.DRIED_KELP, Material.MELON_SLICE, 
            Material.MUSHROOM_STEW, Material.PUMPKIN_PIE, Material.RABBIT_STEW, Material.COOKED_BEEF};

    public Material[] gluttonyFoodItems() {
        return GLUTTONY_FOOD_ITEMS;
    }

    /**
     * The spectral conversion map provides mappings from the source material to the target material, those are meant to represent
     * (former) blockstate changes, which however was now adapted to convert between Materials since the flattening. <br>
     * As such, the Materials may not be totally related to each other, but with the MagicCompat.yml file everything can be changed now<br>
     * The reason an Enum Map is being used over the "traditional" dynamic approach with Tags.
     * @return An Enum Map used by the Spectral enchantment, using it outside of it may have little use. The exact values are user-generated.
     * @since 1.2.0
     */
    public EnumMap<Material, Material> spectralConversionMap() {
        return spectralMaterialConversion;
    }

    /**
     * Damages the itemMeta and returns a signal on whether the item should be broken or not.
     * @param itemMeta the Item meta that should be damaged
     * @param damage The amount of damage that should be applied
     * @param mat The material of the item that should be broken, used to get the maximum health of the item
     * @return true if the item should be removed, false otherwise
     * @since 3.0.0-rc.3
     */
    public static boolean damageMeta(@Nullable ItemMeta itemMeta, short damage, @NotNull Material mat) {
        if (itemMeta instanceof Damageable) {
            damage += ((Damageable) itemMeta).getDamage();
            ((Damageable) itemMeta).setDamage(damage);
            return damage > mat.getMaxDurability();
        }
        return false;
    }

    /**
     * Damages the tool that is stored in a given index of a player's inventory with 
     * a given amount of damage (deducting the amount of unbreaking).
     * If the durability of the result item is below 0, then the item will break.
     * This has the side effect that items that do not have durability will break instantly.
     * @param player The player that should be targeted
     * @param damage The amount of damage that should be applied
     * @param handUsed True if the mainhand should be damaged, false if the offhand should be damaged
     * @since 1.0
     */
    public static void damageTool(Player player, int damage, boolean handUsed) {
        if (handUsed) {
            damageToolInSlot(player, damage, player.getInventory().getHeldItemSlot());
        } else if (damageItem2(player.getInventory().getItemInOffHand(), damage)) {
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        }
    }

    /**
     * Damages the tool that is stored in a given index of a player's inventory with 
     * a given amount of damage (deducting the amount of unbreaking).
     * If the durability of the result item is below 0, then the item will break.
     * This has the side effect that items that do not have durability will break instantly.
     * @param player The player that should be targeted
     * @param damage The amount of damage that should be applied
     * @param slotIndex The index of the item within the inventory
     * @since 1.0
     */
    public static void damageToolInSlot(Player player, int damage, int slotIndex) {
        if (damageItem2(player.getInventory().getItem(slotIndex), damage)) {
            player.getInventory().clear(slotIndex);
        }
    }

    /**
     * Damages a given itemstack with a given amount of damage (deducting the amount of unbreaking).
     * Optionally if the item is damageable and is not unbreakable but the durability is under 0, then item type is set to air.
     * @param stack The stack that should be targeted
     * @param damage The amount of damage that should be applied
     * @since 2.1.6
     * @return true if the item should be removed, false otherwise
     */
    public static boolean damageItem2(@Nullable ItemStack stack, int damage) {
        if (stack == null || stack.getType() == Material.AIR)
            return false;
        ItemMeta im = stack.getItemMeta();
        if (!(im instanceof Damageable) || im.isUnbreakable()) {
            return false;
        }
        // chance that the item is broken is 1/(level+1)
        // So at level = 2 it's 33%, at level = 0 it's 100%, at level 1 it's 50%, at level = 3 it's 25%
        if (ThreadLocalRandom.current().nextInt(1000) <= (1000/(stack.getEnchantmentLevel(Enchantment.DURABILITY)+1))) {
            ((Damageable)im).setDamage(((Damageable) im).getDamage() + damage);
            stack.setItemMeta(im);
        }
        return ((Damageable) im).getDamage() >= stack.getType().getMaxDurability();
    }

    // Displays a particle with the given data
    public static void display(Location loc, Particle particle, int amount, double speed, double xO, double yO,
            double zO) {
        loc.getWorld().spawnParticle(particle, loc.getX(), loc.getY(), loc.getZ(), amount, (float) xO, (float) yO,
                (float) zO, (float) speed);
    }

    /**
     * Sets the amount of Damage that a given ItemStack has (which is the inverse of the remaining durability). <br>
     * Does not perform anything if the ItemMeta is not a {@link org.bukkit.inventory.meta.Damageable} instance. <br>
     * Does not check whether the itemstack has the unbreakable flag set, caution is advised.
     * @param is The target itemstack
     * @param damage The value that the damage should now have
     * @since 1.0.0
     */
    public static void setDamage(@NotNull ItemStack is, int damage) {
        ItemMeta im = is.getItemMeta();
        if (im instanceof org.bukkit.inventory.meta.Damageable) {
            ((Damageable) im).setDamage(damage);
            is.setItemMeta(im);
        }
    }

    /**
     * Gets the amount of Damage that a given ItemStack has (which is the inverse of the remaining durability). <br>
     * If the ItemMeta of the ItemStack is not a {@link org.bukkit.inventory.meta.Damageable} instance then 0 will be returned. <br>
     * Does not check whether the itemstack has the unbreakable flag set, caution is advised.
     * @param is The target itemstack
     * @return The amount of damage an ItemStack has.
     * @since 1.0
     */
    public static int getDamage(ItemStack is) {
        ItemMeta im = is.getItemMeta();
        if (im instanceof org.bukkit.inventory.meta.Damageable) {
            return ((Damageable) im).getDamage();
        }
        return 0;
    }

    /**
     * Calls the appropriate event, breaks the block naturally (particles are emitted) and damages the tool in hand.
     * @param block The targeted block
     * @param player The player that breaks the block.
     * @return Whether the operation was performed successfully.
     * @since 1.0
     */
    public boolean breakBlockNMS(Block block, Player player) {
        BlockBreakEvent evt = new BlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(evt);
        if (!evt.isCancelled()) {
            block.breakNaturally(player.getInventory().getItemInMainHand());
            damageTool(player, 1, true);
            return true;
        }
        return false;
    }

    /**
     * Places a block on the given player's behalf. Fires a BlockPlaceEvent with
     * (nearly) appropriate parameters to probe the legitimacy (permissions etc)
     * of the action and to communicate to other plugins where the block is
     * coming from. <br>
     * The method always assumes that the block is placed against the lower block, 
     * unless it's not possible otherwise.
     *
     * @param blockPlaced the block to be changed
     * @param player the player whose identity to use
     * @param mat the material to set the block to, if allowed
     * @param data the block data to set for the block, if allowed
     *
     * @return true if the block placement has been successful
     * @since 1.0
     */
    public boolean placeBlock(Block blockPlaced, Player player, Material mat, BlockData data) {
        Block blockAgainst = blockPlaced.getRelative((blockPlaced.getY() == 0) ? BlockFace.UP : BlockFace.DOWN);
        ItemStack itemHeld = new ItemStack(mat);
        BlockPlaceEvent placeEvent
        = new BlockPlaceEvent(blockPlaced, blockPlaced.getState(), blockAgainst, itemHeld, player, true,
                EquipmentSlot.HAND);

        Bukkit.getPluginManager().callEvent(placeEvent);
        if (!placeEvent.isCancelled()) {
            blockPlaced.setType(mat);
            if (data != null) {
                blockPlaced.setBlockData(data);
            }
            return true;
        }
        return false;
    }

    public boolean attackEntity(LivingEntity target, Player attacker, double damage, boolean performEquipmentDamage) {
        EntityDamageByEntityEvent damageEvent = new EntityDamageByEntityEvent(attacker, target, DamageCause.ENTITY_ATTACK, damage);
        Bukkit.getPluginManager().callEvent(damageEvent);
        if (damage == 0) {
            return !damageEvent.isCancelled();
        } else if (!damageEvent.isCancelled()) {
            target.damage(damage, attacker);
            target.setLastDamageCause(damageEvent);
            if (performEquipmentDamage) {
                damageTool(attacker, 1, true);
            }
            return true;
        }
        return false;
    }

    /**
     * Shears the target entity and performs the necessary checks beforehand. The correct (guessed) drops are dropped afterwards.
     * Currently only works for Mushroom cow and sheep, other entities aren't yet supported and will return false.
     * Does not damage the item.
     * @param target The target entity
     * @param player The player that shears the entity, used for world protection
     * @param mainHand True if the mainhand was used to shear the event, false otherwise. Used for the event construction.
     * @since 1.0
     */
    public boolean shearEntityNMS(Entity target, Player player, boolean mainHand) {
        if (target instanceof Sheep || target instanceof MushroomCow) {
            EquipmentSlot slot = mainHand ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;
            PlayerShearEntityEvent evt = new PlayerShearEntityEvent(player, target, player.getInventory().getItem(slot), slot);
            Bukkit.getPluginManager().callEvent(evt);
            if (!evt.isCancelled()) {
                if (target instanceof Sheep) {
                    target.getWorld().dropItemNaturally(target.getLocation(), 
                            new ItemStack(ColUtil.getWoolCol(((Sheep)target).getColor()), ThreadLocalRandom.current().nextInt(1, 4)));
                    ((Sheep) target).setSheared(true);
                } else {
                    // Warning: this may fail if Javadocs are to be believed
                    Cow newCow = (Cow) target.getWorld().spawnEntity(target.getLocation(), EntityType.COW);
                    // Transfer old data to new cow
                    newCow.setFallDistance(target.getFallDistance());
                    newCow.setCustomName(target.getCustomName());
                    newCow.setCustomNameVisible(target.isCustomNameVisible());
                    newCow.setFireTicks(target.getFireTicks());
                    newCow.setGlowing(target.isGlowing());
                    newCow.setTicksLived(target.getTicksLived());
                    newCow.setInvulnerable(target.isInvulnerable());
                    newCow.setPersistent(target.isPersistent());
                    newCow.setSilent(target.isSilent());
                    newCow.setAbsorptionAmount(((MushroomCow) target).getAbsorptionAmount());
                    newCow.setArrowsInBody(((MushroomCow) target).getArrowCooldown());
                    newCow.setLastDamage(((MushroomCow) target).getLastDamage());
                    newCow.setAgeLock(((MushroomCow) target).getAgeLock());
                    newCow.setBreed(((MushroomCow) target).canBreed());
                    newCow.setLoveModeTicks(((MushroomCow) target).getLoveModeTicks());
                    newCow.setAge(((MushroomCow) target).getAge());
                    target.remove();
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Ignites the target entity and calls the according events.
     * @param target The target entity that should be ignited
     * @param player The player that is the cause of the ignition
     * @param duration The duration of the ignition
     * @return True if the entity was ignited, false otherwise
     * @since 1.0
     */
    public boolean igniteEntity(Entity target, Player player, int duration) {
        EntityCombustByEntityEvent evt = new EntityCombustByEntityEvent(target, player, duration);
        Bukkit.getPluginManager().callEvent(evt);
        if (!evt.isCancelled()) {
            target.setFireTicks(duration);
            return true;
        }
        return false;
    }

    /**
     * Damages the player by creating the proper event
     * and returns true if the event was not cancelled and as
     * such the player was damaged. The damage will not be negated
     * through amour or any other circumstances. the player will actually
     * not be damaged if the damage will be 0 and as such can be used as a query for any protections.
     * 
     * @param player The player that should be damaged
     * @param damage The amount of damage that the player should receive
     * @param cause the Damage cause that should be used to create the event and damage the player
     * @return The inverse of the cancellation state of the event.
     * @since 1.0
     */
    public boolean damagePlayer(Player player, double damage, DamageCause cause) {
        EntityDamageEvent evt = new EntityDamageEvent(player, cause, damage);
        Bukkit.getPluginManager().callEvent(evt);
        if (damage == 0) {
            return !evt.isCancelled();
        }
        if (!evt.isCancelled()) {
            player.setLastDamageCause(evt);
            player.damage(damage);
            return true;
        }
        return false;
    }

    /**
     * Explodes a Creeper, removes it and deals the correct damage when the creeper is charged and if it's not.
     * The explosion always performs entity damage. The creeper is marked for removal afterwards.
     * The explosion never generates fire.
     * 
     * @param creeper The creeper to explode
     * @param doWorldDamage True if blocks should be broken, false otherwise.
     * @return true if the explosion wasn't cancelled
     * @since 1.0
     */
    public boolean explodeCreeper(Creeper creeper, boolean doWorldDamage) {
        float power;
        if (creeper.isPowered()) {
            power = 6f;
        } else {
            power = 3.1f;
        }
        boolean performed = creeper.getWorld().createExplosion(creeper.getLocation(), power, false, doWorldDamage, creeper);
        creeper.remove();
        return performed;
    }

    public boolean formBlock(Block block, Material mat, Player player) {
        BlockState bs = block.getState();
        bs.setType(mat);
        EntityBlockFormEvent evt = new EntityBlockFormEvent(player, block, bs);
        Bukkit.getPluginManager().callEvent(evt);
        if (!evt.isCancelled()) {
            block.setType(mat);
            return true;
        }
        return false;
    }

    public boolean isBlockSafeToBreak(Block b) {
        Material mat = b.getType();
        return mat.isSolid() && !mat.isInteractable() && !unbreakableBlocks().contains(mat);
    }

    public boolean grow(Block cropBlock, Player player) {
        Material mat = cropBlock.getType();
        BlockData data = cropBlock.getBlockData();

        switch (mat) {
        case PUMPKIN_STEM:
        case MELON_STEM:
        case CARROTS:
        case WHEAT:
        case POTATOES:
        case COCOA:
        case NETHER_WART:
        case BEETROOTS:

            BlockData cropState = cropBlock.getBlockData();
            if (cropState instanceof Ageable) {
                Ageable ag = (Ageable) cropState;
                if (ag.getAge() >= ag.getMaximumAge()) {
                    return false;
                }
                ag.setAge(ag.getAge() + 1);
                data = ag;
            }
            break;
        case CACTUS:
        case SUGAR_CANE:
            int height = 1;
            if (cropBlock.getRelative(BlockFace.DOWN).getType() == mat) { // Only grow if argument is the base
                // block
                return false;
            }
            while ((cropBlock = cropBlock.getRelative(BlockFace.UP)).getType() == mat) {
                if (++height >= 3) { // Cancel if cactus/cane is fully grown
                    return false;
                }
            }
            if (!airs().contains(cropBlock.getType())) { // Only grow if argument is the base block
                return false;
            }

            break;
        default:
            return false;
        }

        if (player != null) {
            return placeBlock(cropBlock, player, mat, data);
        }

        BlockState bs = cropBlock.getState();
        bs.setType(mat);
        BlockGrowEvent evt = new BlockGrowEvent(cropBlock, bs);
        Bukkit.getPluginManager().callEvent(evt);
        if (!evt.isCancelled()) {
            cropBlock.setType(mat);
            cropBlock.setBlockData(data);
            return true;
        }
        return false;
    }

    /**
     * Resets the growth of a berry bush and drops the expected drops (doesn't drop the actual drops - only estimations) and calls the appropriate events.
     * @param berryBlock The block that was broken. The material of the block is expected to be a berry bush, otherwise bad things may happen!
     * @param player The player used for the Event feedback
     * @return Whether the berry was picked successfully
     * @since 1.0
     */
    public boolean pickBerries(Block berryBlock, Player player) {
        BlockData data = berryBlock.getBlockData();
        Ageable a = (Ageable) data;
        if (a.getAge() > 1) { // Age of ripe Berries
            PlayerInteractEvent pie = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, player.getInventory().getItemInMainHand(), berryBlock, player.getFacing());
            Bukkit.getPluginManager().callEvent(pie);
            if (pie.useInteractedBlock() != Result.DENY) {
                int numDropped = a.getAge() + (RND.nextBoolean() ? 0 : -1); // Natural drop rate. Age 2 -> 1-2 berries, Age 3 -> 2-3 berries
                a.setAge(1); // Picked adult berry bush
                berryBlock.setBlockData(a);
                berryBlock.getWorld().dropItem(berryBlock.getLocation(),
                        new ItemStack(Material.SWEET_BERRIES, numDropped));
                return true;
            }
        }
        return false;
    }

    private boolean legacyEntityShootBowEvent = false;
    /**
     * Whether or not the {@link CompatibilityAdapter#nativeBlockPermissionQueryingSystem(Player, Block) native permission query}
     *  should target Towny, in most cases this is just a boolean that is true if towny was found, false otherwise.
     */
    private boolean perm_useTowny = false;

    /**
     * Whether or not the {@link CompatibilityAdapter#nativeBlockPermissionQueryingSystem(Player, Block) native permission query}
     *  should target WorldGuard, in most cases this is just a boolean that is true if WorldGuard was found, false otherwise. <br>
     *  Note that this may not represent the actual state due to method not found issues.
     */
    private boolean perm_useWG = false;

    /**
     * Method that scans whether API methods can be used. It also checks whether plugin integrations are possible and enabled
     */
    private void scanMethods() {
        // Test for java.lang.NoSuchMethodError in the Spigot API with the EntityShootBowEvent.
        try {
            EntityShootBowEvent.class.getConstructor(LivingEntity.class, ItemStack.class, ItemStack.class, Entity.class,
                    EquipmentSlot.class, float.class, boolean.class);
        } catch (NoSuchMethodException excepted) {
            Bukkit.getLogger().warning(Storage.MINILOGO + ChatColor.YELLOW + " Enabling potentially untested legacy mode"
                    + " for the EntityShootBowEvent. Handle with care and update to a newer Spigot (or Paper) version.");
            legacyEntityShootBowEvent = true;
        } catch (SecurityException e) {
            e.printStackTrace();
            Bukkit.getLogger().warning(Storage.MINILOGO + ChatColor.YELLOW + " Enabling potentially untested legacy mode"
                    + " for the EntityShootBowEvent. Handle with care and update to a newer Spigot (or Paper) version.");
            legacyEntityShootBowEvent = true;
        }

        Bukkit.getLogger().info(Storage.MINILOGO + ChatColor.RESET + ": Loading permission integrations. Please note that"
                + " depending on the server lots of things will fail, don't worry much about that though "
                + "(unless it fails even though it shouldn't)");
        try {
            Class.forName("com.palmergames.bukkit.towny.utils.PlayerCacheUtil");
            perm_useTowny = true;
            Bukkit.getLogger().info(Storage.MINILOGO + ChatColor.GREEN + ": Towny runtime found.");
        } catch (ClassNotFoundException excepted) {
            Bukkit.getLogger().info(Storage.MINILOGO + ChatColor.YELLOW + ": Towny runtime not found.");
        }
        try {
            Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
            perm_useWG = true;
            Bukkit.getLogger().info(Storage.MINILOGO + ChatColor.GREEN + ": Worldguard runtime found.");
        } catch (ClassNotFoundException excepted) {
            Bukkit.getLogger().info(Storage.MINILOGO + ChatColor.YELLOW + ": Worldguard runtime not found.");
        }
    }

    /**
     * Dynamically constructs an EntityShootBowEvent, whose specification has changed lately. As such, this method will use
     *  the correct constructor without throwing a java.lang.NoSuchMethodError.
     * @param shooter The shooter
     * @param bow The used bow
     * @param consumable Not used in legacy mode. The item that was consumed
     * @param projectile The spawned projectile/arrow
     * @param hand  Not used in legacy mode. The used hand
     * @param force The force at which the bow is drawn
     * @param consumeItem  Not used in legacy mode.  Whether or not to consume the item
     * @return The constructed EntityShootBowEvent
     */
    public EntityShootBowEvent ConstructEntityShootBowEvent (@NotNull LivingEntity shooter, @Nullable ItemStack bow,
            @Nullable ItemStack consumable, @NotNull Entity projectile, @NotNull EquipmentSlot hand, float force, boolean consumeItem) {
        if (legacyEntityShootBowEvent) {
            try {
                return EntityShootBowEvent.class.getConstructor(LivingEntity.class, ItemStack.class, Entity.class, float.class)
                .newInstance(shooter, bow, projectile, force);
            } catch (NoSuchMethodException | SecurityException e) {
                e.printStackTrace();
                Bukkit.getLogger().severe(Storage.LOGO + ChatColor.RED + " Unable to construct EntityShootBowEvent as the method was not found.");
                return null;
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                e.printStackTrace();
                Bukkit.getLogger().severe(Storage.LOGO + ChatColor.RED + " Unable to construct EntityShootBowEvent as argument errors"
                        + " occured (report this to the Issue page).");
                return null;
            }
        } else {
            return new EntityShootBowEvent(shooter, bow, consumable, projectile, hand, force, consumeItem);
        }
    }

    /**
     * This method queries the correct Permission interfaces, which are plugins. 
     * If the plugin is not loaded the method will ignore it gracefully. <br>
     * Plugins are detected during the {@link CompatibilityAdapter#scanMethods()} private function which is called shortly after the 
     *constructor. <br>
     * @param source The player, from where the Query originates from.
     * @param target The Block which should be tested whether the player may break/alter.
     * @return True if the player may break/alter the block, false otherwise
     * @since 1.2.0
     */
    public boolean nativeBlockPermissionQueryingSystem (@NotNull Player source, @NotNull Block target) {
        
        if (perm_useTowny && !(PlayerCacheUtil.getCachePermission(source, target.getLocation(), target.getType(), TownyPermission.ActionType.BUILD)
                || PlayerCacheUtil.getCachePermission(source, target.getLocation(), target.getType(), TownyPermission.ActionType.DESTROY))) {
            return false;
        }
        return !perm_useWG || (WorldGuardPlugin.inst().createProtectionQuery().testBlockBreak(source, target) ||
                WorldGuardPlugin.inst().createProtectionQuery().testBlockInteract(source, target));
        // TODO Other plugins (factions, Grief protects, etc...) - Just create an issue to create priority if you need one in specific
    }
}
