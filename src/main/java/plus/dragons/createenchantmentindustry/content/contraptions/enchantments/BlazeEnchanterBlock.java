package plus.dragons.createenchantmentindustry.content.contraptions.enchantments;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.contraptions.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.contraptions.relays.belt.transport.TransportedItemStack;
import com.simibubi.create.content.contraptions.wrench.IWrenchable;
import com.simibubi.create.foundation.block.ITE;
import com.simibubi.create.foundation.tileEntity.ComparatorUtil;
import com.simibubi.create.foundation.utility.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import plus.dragons.createenchantmentindustry.entry.ModBlockEntities;
import plus.dragons.createenchantmentindustry.entry.ModFluids;
import plus.dragons.createenchantmentindustry.entry.ModItems;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation")
public class BlazeEnchanterBlock extends HorizontalDirectionalBlock implements IWrenchable, ITE<BlazeEnchanterBlockEntity> {

    public static final EnumProperty<HeatLevel> HEAT_LEVEL = EnumProperty.create("blaze", HeatLevel.class);
    public BlazeEnchanterBlock(Properties pProperties) {
        super(pProperties);
        registerDefaultState(defaultBlockState().setValue(HEAT_LEVEL, HeatLevel.SMOULDERING));
    }

    @Override
    public Class<BlazeEnchanterBlockEntity> getTileEntityClass() {
        return BlazeEnchanterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BlazeEnchanterBlockEntity> getTileEntityType() {
        return ModBlockEntities.BLAZE_ENCHANTING_ALTER.get();
    }
    
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HEAT_LEVEL, FACING);
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos blockPos, CollisionContext pContext) {
        return AllShapes.HEATER_BLOCK_SHAPE;
    }

    @Override
    public void onRemove(BlockState state, Level worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.hasBlockEntity() || state.getBlock() == newState.getBlock())
            return;
        withTileEntityDo(worldIn, pos, te -> {
            ItemStack heldItemStack = te.getHeldItemStack();
            if (!heldItemStack.isEmpty())
                Containers.dropItemStack(worldIn, pos.getX(), pos.getY(), pos.getZ(), heldItemStack);
            Containers.dropItemStack(worldIn, pos.getX(), pos.getY(), pos.getZ(), te.targetItem);
            var tank = te.internalTank.getPrimaryHandler();
            if(tank.getFluid().getFluid().isSame(ModFluids.EXPERIENCE.get().getSource())){
                var expBall = new ExperienceOrb(worldIn, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, tank.getFluid().getAmount());
                worldIn.addFreshEntity(expBall);
            }
        });
        worldIn.removeBlockEntity(pos);
    }

    @Override
    public InteractionResult use(BlockState state, Level worldIn, BlockPos pos, Player player, InteractionHand handIn, BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(handIn);
        if (!heldItem.isEmpty()){
            return onTileEntityUse(worldIn, pos, te -> {
                if(heldItem.is(ModItems.ENCHANTING_GUIDE.get()) && EnchantingGuideItem.getEnchantment(heldItem) != null){
                    if (!worldIn.isClientSide) {
                        var target = te.targetItem.copy();
                        te.targetItem = heldItem;
                        if(!player.getAbilities().instabuild)
                            player.setItemInHand(handIn, target);
                        te.notifyUpdate();
                    }
                    return InteractionResult.SUCCESS;
                } else if(Enchanting.valid(heldItem, te.targetItem, te.hyper())) {
                    ItemStack heldItemStack = te.getHeldItemStack();
                    if (heldItemStack.isEmpty()) {
                        if (!worldIn.isClientSide) {
                            te.heldItem = new TransportedItemStack(heldItem);
                            if(!player.getAbilities().instabuild)
                                player.setItemInHand(handIn, ItemStack.EMPTY);
                            te.notifyUpdate();
                        }
                        return InteractionResult.SUCCESS;
                    }
                    return InteractionResult.FAIL;
                } else if (AllItems.GOGGLES.isIn(heldItem)) {
                    if (te.goggles)
                        return InteractionResult.PASS;
                    te.goggles = true;
                    te.notifyUpdate();
                    return InteractionResult.SUCCESS;
                }
                else return InteractionResult.PASS;
            });
        } else {
            if(player.isShiftKeyDown()){
                if(!player.level.isClientSide()){
                    worldIn.setBlockAndUpdate(pos, AllBlocks.BLAZE_BURNER.getDefaultState()
                            .setValue(BlazeBurnerBlock.FACING, state.getValue(FACING))
                            .setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
                }
                return InteractionResult.SUCCESS;
            } else {
                return onTileEntityUse(worldIn, pos, te -> {
                    ItemStack heldItemStack = te.getHeldItemStack();
                    if (!heldItemStack.isEmpty()) {
                        if (!worldIn.isClientSide) {
                            te.heldItem = null;
                            player.setItemInHand(handIn, heldItemStack);
                            te.notifyUpdate();
                        }
                        return InteractionResult.SUCCESS;
                    } if (!te.goggles)
                        return InteractionResult.PASS;
                    te.goggles = false;
                    te.notifyUpdate();
                    return InteractionResult.SUCCESS;
                });
            }
        }
    }


    @Override
    public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer, ItemStack pStack) {
        super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
        // TODO Advancement need more investigate
        // It should be trigger at somewhere else
        // AdvancementBehaviour.setPlacedBy(pLevel, pPos, pPlacer);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter pLevel, BlockPos pPos, BlockState pState) {
        return new ItemStack(AllBlocks.BLAZE_BURNER.get());
    }

    // TODO: When create itself change it, change it.
    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }


    @Override
    public List<ItemStack> getDrops(BlockState pState, LootContext.Builder pBuilder) {
        var ret = new ArrayList<ItemStack>();
        ret.add(new ItemStack(AllBlocks.BLAZE_BURNER.get()));
        return ret;
    }

    // TODO: When create itself change it, change it.
    @Override
    public int getAnalogOutputSignal(BlockState blockState, Level worldIn, BlockPos pos) {
        return ComparatorUtil.levelOfSmartFluidTank(worldIn, pos);
    }

    // TODO: When create itself change it, change it.
    @Override
    public boolean isPathfindable(BlockState state, BlockGetter reader, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        var te = level.getBlockEntity(pos);
        if(te instanceof BlazeEnchanterBlockEntity blazeEnchanterBlockEntity){
            if(blazeEnchanterBlockEntity.hyper()) return 15;
            else if(blazeEnchanterBlockEntity.internalTank.isEmpty()) return 7;
            else return 11;
        } else return 0;
    }

    public enum HeatLevel implements StringRepresentable {
        SMOULDERING, KINDLED, SEETHING,;

        public static HeatLevel byIndex(int index) {
            return values()[index];
        }

        public HeatLevel nextActiveLevel() {
            return byIndex(ordinal() % (values().length - 1) + 1);
        }

        public boolean isAtLeast(HeatLevel heatLevel) {
            return this.ordinal() >= heatLevel.ordinal();
        }

        @Override
        public String getSerializedName() {
            return Lang.asId(name());
        }
    }
}
