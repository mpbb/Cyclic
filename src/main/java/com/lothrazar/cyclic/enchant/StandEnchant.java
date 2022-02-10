/*******************************************************************************
 * The MIT License (MIT)
 *
 * Copyright (C) 2014-2018 Sam Bassett (aka Lothrazar)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 ******************************************************************************/
package com.lothrazar.cyclic.enchant;

import com.lothrazar.cyclic.ModCyclic;
import com.lothrazar.cyclic.registry.EnchantRegistry;
import com.lothrazar.cyclic.registry.SoundRegistry;
import com.lothrazar.cyclic.util.UtilChat;
import com.lothrazar.cyclic.util.UtilPlayer;
import com.lothrazar.cyclic.util.UtilSound;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class StandEnchant extends EnchantmentCyclic {

  public static final String ID = "laststand";
  public static BooleanValue CFG;

  public StandEnchant(Rarity rarityIn, EnchantmentCategory typeIn, EquipmentSlot... slots) {
    super(rarityIn, typeIn, slots);
    MinecraftForge.EVENT_BUS.register(this);
  }

  @Override
  public boolean checkCompatibility(Enchantment ench) {
    return super.checkCompatibility(ench) && ench != EnchantRegistry.LAUNCH && ench != EnchantRegistry.EXPERIENCE_BOOST
        && ench != EnchantRegistry.TRAVELLER && ench != Enchantments.MENDING && ench != Enchantments.THORNS;
  }

  @Override
  public boolean isEnabled() {
    return CFG.get();
  }

  @Override
  public int getMaxLevel() {
    return 1;
  }

  @Override
  public boolean canEnchant(ItemStack stack) {
    boolean yes = (stack.getItem() instanceof ArmorItem)
        && ((ArmorItem) stack.getItem()).getSlot() == EquipmentSlot.LEGS;
    return yes;
  }

  @Override
  public boolean canApplyAtEnchantingTable(ItemStack stack) {
    return this.canEnchant(stack);
  }

  @SubscribeEvent
  public void onEntityUpdate(LivingDamageEvent event) {
    int level = getCurrentArmorLevelSlot(event.getEntityLiving(), EquipmentSlot.LEGS);
    if (level <= 0) {
      return;
    }
    //prevent lethal damage only 
    if (event.getEntityLiving().getHealth() - event.getAmount() <= 0 && event.getEntityLiving() instanceof ServerPlayer player) {
      final int xpCost = 30 / level; // higher level gives a lower cost. level 1 is 30xp, lvl 3 is 10xp etc
      if (UtilPlayer.getExpTotal(player) < xpCost) {
        return;
      }
      //if would cause death, then 
      float toSurvive = event.getEntityLiving().getHealth() - 1;
      //      float diff = event.getAmount() - toSurvive;
      event.setAmount(toSurvive);//reduce damage so i survive
      //never fires on client side
      UtilSound.playSoundFromServer(player, SoundRegistry.CHAOS_REAPER, 1F, 0.4F);
      player.giveExperiencePoints(-1 * xpCost);
      UtilChat.sendStatusMessage(player, "enchantment." + ModCyclic.MODID + "." + ID + ".activated");
      player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 1));
    }
  }
}