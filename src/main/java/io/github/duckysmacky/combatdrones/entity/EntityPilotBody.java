package io.github.duckysmacky.combatdrones.entity;

import com.google.common.base.Optional;
import io.github.duckysmacky.combatdrones.CombatDrones;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Stands in for a player whose real entity is away riding a ramp unit (remote view / Take control). Looks like them
 * (skin, armour, held items, name) and takes their hits: any damage is passed on to the real player. It only exists
 * during a session: never saved, and it removes itself if its session is gone.
 */
public class EntityPilotBody extends EntityLivingBase {
    private static final DataParameter<Optional<UUID>> OWNER = EntityDataManager.createKey(EntityPilotBody.class, DataSerializers.OPTIONAL_UNIQUE_ID);

    private final NonNullList<ItemStack> handItems = NonNullList.withSize(2, ItemStack.EMPTY);
    private final NonNullList<ItemStack> armorItems = NonNullList.withSize(4, ItemStack.EMPTY);
    /** Keeps the body's chunk loaded while its player is elsewhere, so it stays in the world (and hittable). */
    @Nullable
    private ForgeChunkManager.Ticket chunkTicket;

    public EntityPilotBody(World world) {
        super(world);
        setSize(0.6F, 1.8F);
    }

    /** A body standing exactly where {@code player} is, dressed like them. */
    public EntityPilotBody(World world, EntityPlayer player) {
        this(world);
        dataManager.set(OWNER, Optional.of(player.getUniqueID()));
        setLocationAndAngles(player.posX, player.posY, player.posZ, player.rotationYaw, player.rotationPitch);
        rotationYawHead = player.rotationYawHead;
        renderYawOffset = player.renderYawOffset;
        setCustomNameTag(player.getName());
        setAlwaysRenderNameTag(true);
        for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
            setItemStackToSlot(slot, player.getItemStackFromSlot(slot).copy());
        }
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(OWNER, Optional.absent());
    }

    @Nullable
    public UUID getOwner() {
        return dataManager.get(OWNER).orNull();
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (world.isRemote) {
            return;
        }
        if (!RemoteSessions.hasBody(this)) {
            setDead();
            return;
        }
        if (chunkTicket == null) {
            chunkTicket = ForgeChunkManager.requestTicket(CombatDrones.instance, world, ForgeChunkManager.Type.ENTITY);
            if (chunkTicket != null) {
                chunkTicket.bindEntity(this);
                ForgeChunkManager.forceChunk(chunkTicket, new ChunkPos(MathHelper.floor(posX) >> 4, MathHelper.floor(posZ) >> 4));
            }
        }
    }

    @Override
    public void setDead() {
        super.setDead();
        if (!world.isRemote && chunkTicket != null) {
            ForgeChunkManager.releaseTicket(chunkTicket);
            chunkTicket = null;
        }
    }

    /** Every hit lands on the real player; the body itself never loses health. */
    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (world.isRemote || isEntityInvulnerable(source) || isDead) {
            return false;
        }
        if (RemoteSessions.hurtThroughBody(this, source, amount)) {
            world.setEntityState(this, (byte) 2); // red hurt flash for everyone watching
            return true;
        }
        return false;
    }

    @Override
    public Iterable<ItemStack> getArmorInventoryList() {
        return armorItems;
    }

    @Override
    public ItemStack getItemStackFromSlot(EntityEquipmentSlot slot) {
        return slot.getSlotType() == EntityEquipmentSlot.Type.HAND ? handItems.get(slot.getIndex()) : armorItems.get(slot.getIndex());
    }

    @Override
    public void setItemStackToSlot(EntityEquipmentSlot slot, ItemStack stack) {
        if (slot.getSlotType() == EntityEquipmentSlot.Type.HAND) {
            handItems.set(slot.getIndex(), stack);
        } else {
            armorItems.set(slot.getIndex(), stack);
        }
    }

    @Override
    public EnumHandSide getPrimaryHand() {
        return EnumHandSide.RIGHT;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    protected void collideWithEntity(Entity entity) {
    }

    @Override
    public boolean writeToNBTOptional(NBTTagCompound compound) {
        return false; // only meaningful during a live session
    }
}
