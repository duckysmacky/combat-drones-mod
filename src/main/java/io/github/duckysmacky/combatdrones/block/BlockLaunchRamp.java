package io.github.duckysmacky.combatdrones.block;

import io.github.duckysmacky.combatdrones.ModContent;
import io.github.duckysmacky.combatdrones.ModSounds;
import io.github.duckysmacky.combatdrones.item.ItemDrone;
import io.github.duckysmacky.combatdrones.type.DroneType;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;

/**
 * Launch Ramp: holds one ramp-launched unit, which is fuelled here, planned with the Mission Planner and launched
 * along the ramp's facing (away from the player who placed it).
 */
public class BlockLaunchRamp extends BlockHorizontal {
    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(0, 0, 0, 1, 0.5, 1);

    public BlockLaunchRamp() {
        super(Material.IRON);
        setHardness(3.0F);
        setResistance(10.0F);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing());
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileLaunchRamp();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side,
                                    float hitX, float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileLaunchRamp)) {
            return false;
        }
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            return stack.getItem() instanceof ItemDrone || stack.getItem() == ModContent.fuelCan
                    || stack.getItem() == ModContent.missionPlanner || (stack.isEmpty() && player.isSneaking());
        }
        TileLaunchRamp ramp = (TileLaunchRamp) tile;
        if (stack.getItem() instanceof ItemDrone) {
            install(ramp, player, stack);
            return true;
        }
        if (stack.getItem() == ModContent.fuelCan) {
            refuel(ramp, player, stack, world, pos);
            return true;
        }
        if (stack.getItem() == ModContent.missionPlanner) {
            return RampActions.openPlanner(ramp, player);
        }
        if (stack.isEmpty() && player.isSneaking() && ramp.isLoaded()) {
            if (!ramp.isOwnedBy(player)) {
                status(player, "message.combat_drones.ramp.not_owner");
                return true;
            }
            ItemHandlerHelper.giveItemToPlayer(player, ramp.takeUnit());
            return true;
        }
        return false;
    }

    private static void install(TileLaunchRamp ramp, EntityPlayer player, ItemStack stack) {
        DroneType type = ((ItemDrone) stack.getItem()).getDroneType();
        if (type == null || type.getCategory() != DroneType.Category.RAMP_AUTOPILOT) {
            status(player, "message.combat_drones.ramp.not_ramp_unit");
            return;
        }
        if (ramp.isLoaded()) {
            status(player, "message.combat_drones.ramp.occupied");
            return;
        }
        ramp.install(type, ItemDrone.getFuel(stack), player);
        if (!player.capabilities.isCreativeMode) {
            stack.shrink(1);
        }
        status(player, "message.combat_drones.ramp.installed");
    }

    private static void refuel(TileLaunchRamp ramp, EntityPlayer player, ItemStack stack, World world, BlockPos pos) {
        if (!ramp.isLoaded()) {
            status(player, "message.combat_drones.ramp.empty");
            return;
        }
        if (ramp.getFuelFraction() >= 1) {
            status(player, "message.combat_drones.ramp.full");
            return;
        }
        ramp.refuel();
        if (!player.capabilities.isCreativeMode) {
            stack.shrink(1);
        }
        world.playSound(null, pos, ModSounds.DRONE_ATTACH, SoundCategory.BLOCKS, 1.0F, 0.6F);
        status(player, "message.combat_drones.ramp.refuelled");
    }

    private static void status(EntityPlayer player, String key) {
        player.sendStatusMessage(new TextComponentTranslation(key), true);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileLaunchRamp) {
            ItemStack unit = ((TileLaunchRamp) tile).takeUnit();
            if (!unit.isEmpty()) {
                InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), unit);
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return SHAPE;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return face == EnumFacing.DOWN ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
    }
}
