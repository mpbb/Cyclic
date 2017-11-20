package com.lothrazar.cyclicmagic.component.forester;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.lothrazar.cyclicmagic.ModCyclic;
import com.lothrazar.cyclicmagic.block.base.TileEntityBaseMachineInvo;
import com.lothrazar.cyclicmagic.gui.ITilePreviewToggle;
import com.lothrazar.cyclicmagic.gui.ITileRedstoneToggle;
import com.lothrazar.cyclicmagic.gui.ITileSizeToggle;
import com.lothrazar.cyclicmagic.util.UtilFakePlayer;
import com.lothrazar.cyclicmagic.util.UtilItemStack;
import com.lothrazar.cyclicmagic.util.UtilParticle;
import com.lothrazar.cyclicmagic.util.UtilShape;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.oredict.OreDictionary;

/**
 * 
 * SEE TileMachineMiner
 * 
 */
public class TileEntityForester extends TileEntityBaseMachineInvo implements ITileRedstoneToggle, ITilePreviewToggle, ITickable {
  private static final String[] validTargetsOreDict = new String[] { "logWood" };
  //vazkii wanted simple block breaker and block placer. already have the BlockBuilder for placing :D
  //of course this isnt standalone and hes probably found some other mod by now but doing it anyway https://twitter.com/Vazkii/status/767569090483552256
  // fake player idea ??? https://gitlab.prok.pw/Mirrors/minecraftforge/commit/f6ca556a380440ededce567f719d7a3301676ed0
  private static final String NBT_REDST = "redstone";
  private static final String NBTMINING = "mining";
  private static final String NBTDAMAGE = "curBlockDamage";
  private static final String NBTPLAYERID = "uuid";
  private static final String NBTTARGET = "target";
  public static final int INVENTORY_SIZE = 19;
  private static final int FUEL_SLOT = INVENTORY_SIZE - 1;
  public final static int TIMER_FULL = 22;
  private static final int HEIGHT = 32;
  private boolean isCurrentlyMining;
  private float curBlockDamage;
  private BlockPos targetPos = null;
  private int size = 9;
  private int needsRedstone = 1;
  private int renderParticles = 0;
  private WeakReference<FakePlayer> fakePlayer;
  private UUID uuid;
  public static enum Fields {
    REDSTONE, TYPEHARVEST, RENDERPARTICLES, TIMER, FUEL, FUELMAX;
  }
  public TileEntityForester() {
    super(INVENTORY_SIZE);
    this.setFuelSlot(FUEL_SLOT);
  }
  @Override
  public int[] getFieldOrdinals() {
    return super.getFieldArray(Fields.values().length);
  }
  private void verifyFakePlayer(WorldServer w) {
    if (fakePlayer == null) {
      fakePlayer = UtilFakePlayer.initFakePlayer(w, this.uuid);
      if (fakePlayer == null) {
        ModCyclic.logger.error("Fake player failed to init ");
      }
    }
  }
  @Override
  public void update() {
    if (isRunning()) {
      this.spawnParticlesAbove();
    }
    if (world instanceof WorldServer) {
      verifyUuid(world);
      verifyFakePlayer((WorldServer) world);
      tryEquipItem();
      if (targetPos == null) {
        targetPos = this.getTargetCenter(); //not sure if this is needed
      }
      if (isRunning()) {
        if (this.updateFuelIsBurning() && this.updateTimerIsZero()) {
          if (updateMiningProgress()) {
            this.timer = TIMER_FULL;
          }
        }
      }
      else { // we do not have power
        if (isCurrentlyMining) {
          isCurrentlyMining = false;
          resetProgress(targetPos);
        }
      }
    }
  }
  /**
   * return true if block is harvested/broken
   */
  private boolean updateMiningProgress() {
//    if (isCurrentlyMining == false) { //we can mine but are not currently. so try moving to a new position
//      updateTargetPos();
//    }
    if (this.isPreviewVisible()) {
      UtilParticle.spawnParticlePacket(EnumParticleTypes.DRAGON_BREATH, this.targetPos);
    }
    if (isTargetValid()) { //if target is valid, allow mining (no air, no blacklist, etc)
      isCurrentlyMining = true;
    }
    else { // no valid target, back out
      isCurrentlyMining = false;
      updateTargetPos();
      resetProgress(targetPos);
    }
    //currentlyMining may have changed, and we are still turned on:
    if (isCurrentlyMining) {
      IBlockState targetState = world.getBlockState(targetPos);
      curBlockDamage += UtilItemStack.getPlayerRelativeBlockHardness(targetState.getBlock(), targetState, fakePlayer.get(), world, targetPos);
      if (curBlockDamage >= 1.0f) {
        isCurrentlyMining = false;
        resetProgress(targetPos);
        if (fakePlayer.get() != null) {
          return fakePlayer.get().interactionManager.tryHarvestBlock(targetPos);
        }
      }
      else {
        world.sendBlockBreakProgress(uuid.hashCode(), targetPos, (int) (curBlockDamage * 10.0F) - 1);
      }
    }
    return false;
  }
  private void tryEquipItem() {
    if (fakePlayer.get().getHeldItem(EnumHand.MAIN_HAND).isEmpty()) {
      ItemStack unbreakingPickaxe = new ItemStack(Items.DIAMOND_PICKAXE, 1);
      unbreakingPickaxe.addEnchantment(Enchantments.LOOTING, 3);
      unbreakingPickaxe.setTagCompound(new NBTTagCompound());
      unbreakingPickaxe.getTagCompound().setBoolean("Unbreakable", true);
      fakePlayer.get().setHeldItem(EnumHand.MAIN_HAND, unbreakingPickaxe);
    }
  }
  private void verifyUuid(World world) {
    if (uuid == null) {
      uuid = UUID.randomUUID();
      IBlockState state = world.getBlockState(this.pos);
      world.notifyBlockUpdate(pos, state, state, 3);
    }
  }
  private boolean isTargetValid() {
    World world = getWorld();
    if (world.isAirBlock(targetPos) || world.getBlockState(targetPos) == null) {
      return false;
    }
    IBlockState targetState = world.getBlockState(targetPos);
    Block target = targetState.getBlock();
    for (String oreId : validTargetsOreDict) {
      if (OreDictionary.doesOreNameExist(oreId)) {
        for (ItemStack s : OreDictionary.getOres(oreId)) {
          if (OreDictionary.itemMatches(s, new ItemStack(target), false)) {
            return true;
          }
        }
      }
    }
    return false;
  }
  public BlockPos getTargetCenter() {
    //move center over that much, not including exact horizontal
    //so the rand range is basically [0,8], then we left shift into [-4,+4]
    return getPos();
  }
  private void updateTargetPos() {
    //spiraling outward from center
    //first are we out of bounds? if so start at center + 1
    int minX = this.pos.getX() - this.size;
    int maxX = this.pos.getX() + this.size;
    int minY = this.pos.getY();
    int maxY = this.pos.getY() + HEIGHT;
    int minZ = this.pos.getZ() - this.size;
    int maxZ = this.pos.getZ() + this.size;
    //first we see if this column is done by going bottom to top
    this.targetPos = this.targetPos.add(0, 1, 0);
    if (this.targetPos.getY() <= maxY) {
      return;//next position is valid
    }
    //when we are at the top, only THEN we move to a new horizontal x,z coordinate
    //starting from the base. first move X left to right only
    targetPos = new BlockPos(targetPos.getX() + 1, minY, targetPos.getZ());
    if (targetPos.getX() <= maxX) {
      return;
    }
    //end of the line
    //so start over like a typewriter, moving up one Z row
    targetPos = new BlockPos(minX, targetPos.getY(), targetPos.getZ() + 1);
    if (targetPos.getZ() <= maxZ) {
      return;
    }
    //this means we have passed over the threshold of ALL coordinates
    targetPos = new BlockPos(minX, minY, minZ);
    //    curBlockDamage = 0;
  }
  @Override
  public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
    tagCompound.setInteger(NBT_REDST, this.needsRedstone);
    if (uuid != null) {
      tagCompound.setString(NBTPLAYERID, uuid.toString());
    }
    if (targetPos != null) {
      tagCompound.setIntArray(NBTTARGET, new int[] { targetPos.getX(), targetPos.getY(), targetPos.getZ() });
    }
    tagCompound.setBoolean(NBTMINING, isCurrentlyMining);
    tagCompound.setFloat(NBTDAMAGE, curBlockDamage);
    tagCompound.setInteger(NBT_SIZE, size);
    tagCompound.setInteger(NBT_RENDER, renderParticles);
    return super.writeToNBT(tagCompound);
  }
  @Override
  public void readFromNBT(NBTTagCompound tagCompound) {
    super.readFromNBT(tagCompound);
    this.needsRedstone = tagCompound.getInteger(NBT_REDST);
    this.size = tagCompound.getInteger(NBT_SIZE);
    if (tagCompound.hasKey(NBTPLAYERID)) {
      uuid = UUID.fromString(tagCompound.getString(NBTPLAYERID));
    }
    if (tagCompound.hasKey(NBTTARGET)) {
      int[] coords = tagCompound.getIntArray(NBTTARGET);
      if (coords.length >= 3) {
        targetPos = new BlockPos(coords[0], coords[1], coords[2]);
      }
    }
    isCurrentlyMining = tagCompound.getBoolean(NBTMINING);
    curBlockDamage = tagCompound.getFloat(NBTDAMAGE);
    this.renderParticles = tagCompound.getInteger(NBT_RENDER);
  }
  public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
    if (isCurrentlyMining && uuid != null) {
      resetProgress(pos);
    }
  }
  private void resetProgress(BlockPos targetPos) {
    if (uuid != null) {
      //BlockPos targetPos = pos.offset(state.getValue(BlockMiner.PROPERTYFACING));
      getWorld().sendBlockBreakProgress(uuid.hashCode(), targetPos, -1);
      curBlockDamage = 0;
    }
  }
  @Override
  public int[] getSlotsForFace(EnumFacing side) {
    if (EnumFacing.UP == side) {
      return new int[] { 0, 1, 2, 3, 4, 5 };
    }
    return new int[] { FUEL_SLOT };
  }
  @Override
  public int getField(int id) {
    switch (Fields.values()[id]) {
      case REDSTONE:
        return this.needsRedstone;
      case RENDERPARTICLES:
        return this.renderParticles;
      case FUEL:
        return this.getFuelCurrent();
      case FUELMAX:
        return this.getFuelMax();
      case TIMER:
        return this.timer;
      case TYPEHARVEST:
      break;
      default:
      break;
    }
    return 0;
  }
  @Override
  public void setField(int id, int value) {
    switch (Fields.values()[id]) {
      case REDSTONE:
        needsRedstone = value;
      break;
      case RENDERPARTICLES:
        this.renderParticles = value % 2;
      break;
      case FUEL:
        this.setFuelCurrent(value);
      break;
      case FUELMAX:
        this.setFuelMax(value);
      break;
      case TIMER:
        this.timer = value;
      break;
      case TYPEHARVEST:
      break;
      default:
      break;
    }
  }
  @Override
  public boolean receiveClientEvent(int id, int value) {
    if (id >= 0 && id < this.getFieldCount()) {
      this.setField(id, value);
      return true;
    }
    else {
      return super.receiveClientEvent(id, value);
    }
  }
  @Override
  public int getFieldCount() {
    return Fields.values().length;
  }
  @Override
  public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
    // Extracts data from a packet (S35PacketUpdateTileEntity) that was sent
    // from the server. Called on client only.
    this.readFromNBT(pkt.getNbtCompound());
    super.onDataPacket(net, pkt);
  }
  @Override
  public void toggleNeedsRedstone() {
    int val = this.needsRedstone + 1;
    if (val > 1) {
      val = 0;//hacky lazy way
    }
    this.setField(Fields.REDSTONE.ordinal(), val);
  }
  public boolean onlyRunIfPowered() {
    return this.needsRedstone == 1;
  }
  @Override
  public void togglePreview() {
    this.renderParticles = (renderParticles + 1) % 2;
  }
  @Override
  public List<BlockPos> getShape() {
    List<BlockPos> allPos = new ArrayList<BlockPos>();
    for (int i = 0; i < 2; i++) {
      allPos.addAll(UtilShape.squareHorizontalHollow(getTargetCenter().up(i), size));
    }
    return allPos;
  }
  @Override
  public boolean isPreviewVisible() {
    return this.getField(Fields.RENDERPARTICLES.ordinal()) == 1;
  }
}
