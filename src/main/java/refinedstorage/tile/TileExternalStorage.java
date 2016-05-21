package refinedstorage.tile;

import io.netty.buffer.ByteBuf;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.items.IItemHandler;
import powercrystals.minefactoryreloaded.api.IDeepStorageUnit;
import refinedstorage.RefinedStorage;
import refinedstorage.RefinedStorageUtils;
import refinedstorage.container.ContainerStorage;
import refinedstorage.inventory.InventorySimple;
import refinedstorage.network.MessagePriorityUpdate;
import refinedstorage.storage.IStorage;
import refinedstorage.storage.IStorageGui;
import refinedstorage.storage.IStorageProvider;
import refinedstorage.storage.ItemGroup;
import refinedstorage.tile.config.ICompareConfig;
import refinedstorage.tile.config.IModeConfig;
import refinedstorage.tile.config.IRedstoneModeConfig;
import refinedstorage.tile.config.ModeFilter;

import java.util.List;

public class TileExternalStorage extends TileMachine implements IStorageProvider, IStorage, IStorageGui, ICompareConfig, IModeConfig {
    public static final String NBT_PRIORITY = "Priority";
    public static final String NBT_COMPARE = "Compare";
    public static final String NBT_MODE = "Mode";

    private InventorySimple inventory = new InventorySimple("external_storage", 9, this);

    private int priority = 0;
    private int compare = 0;
    private int mode = 0;

    private int stored = 0;

    @Override
    public int getEnergyUsage() {
        return 2;
    }

    @Override
    public void updateMachine() {
    }

    @Override
    public void addItems(List<ItemGroup> items) {
        IDeepStorageUnit storageUnit = getStorageUnit();

        if (storageUnit != null) {
            if (storageUnit.getStoredItemType() != null) {
                items.add(new ItemGroup(storageUnit.getStoredItemType().copy()));
            }
        } else {
            IItemHandler handler = getItemHandler();

            if (handler != null) {
                for (int i = 0; i < handler.getSlots(); ++i) {
                    if (handler.getStackInSlot(i) != null) {
                        items.add(new ItemGroup(handler.getStackInSlot(i).copy()));
                    }
                }
            }
        }
    }

    @Override
    public void push(ItemStack stack) {
        IDeepStorageUnit storageUnit = getStorageUnit();

        if (storageUnit != null) {
            if (storageUnit.getStoredItemType() == null) {
                storageUnit.setStoredItemType(stack, stack.stackSize);
            } else {
                storageUnit.setStoredItemCount(storageUnit.getStoredItemType().stackSize + stack.stackSize);
            }
        } else {
            IItemHandler handler = getItemHandler();

            if (handler != null) {
                for (int i = 0; i < handler.getSlots(); ++i) {
                    if (handler.insertItem(i, stack, false) == null) {
                        break;
                    }
                }
            }
        }
    }

    @Override
    public ItemStack take(ItemStack stack, int flags) {
        int quantity = stack.stackSize;

        IDeepStorageUnit storageUnit = getStorageUnit();

        if (storageUnit != null) {
            if (storageUnit.getStoredItemType() != null && RefinedStorageUtils.compareStackNoQuantity(storageUnit.getStoredItemType(), stack)) {
                if (quantity > storageUnit.getStoredItemType().stackSize) {
                    quantity = storageUnit.getStoredItemType().stackSize;
                }

                ItemStack took = storageUnit.getStoredItemType().copy();
                took.stackSize = quantity;

                storageUnit.setStoredItemCount(storageUnit.getStoredItemType().stackSize - quantity);

                return took;
            }
        } else {
            IItemHandler handler = getItemHandler();

            if (handler != null) {
                for (int i = 0; i < handler.getSlots(); ++i) {
                    ItemStack slot = handler.getStackInSlot(i);

                    if (slot != null && RefinedStorageUtils.compareStack(slot, stack, flags)) {
                        if (quantity > slot.stackSize) {
                            quantity = slot.stackSize;
                        }

                        handler.extractItem(i, quantity, false);

                        ItemStack took = slot.copy();
                        took.stackSize = quantity;

                        return took;
                    }
                }
            }
        }

        return null;
    }

    @Override
    public boolean mayPush(ItemStack stack) {
        if (ModeFilter.violatesMode(inventory, this, compare, stack)) {
            IDeepStorageUnit storageUnit = getStorageUnit();

            if (storageUnit != null) {
                if (storageUnit.getStoredItemType() == null) {
                    return stack.stackSize < storageUnit.getMaxStoredCount();
                }

                return RefinedStorageUtils.compareStackNoQuantity(storageUnit.getStoredItemType(), stack) && (storageUnit.getStoredItemType().stackSize + stack.stackSize) < storageUnit.getMaxStoredCount();
            } else {
                IItemHandler handler = getItemHandler();

                if (handler != null) {
                    for (int i = 0; i < handler.getSlots(); ++i) {
                        if (handler.insertItem(i, stack, true) == null) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    public TileEntity getFront() {
        return worldObj.getTileEntity(pos.offset(getDirection()));
    }

    public IDeepStorageUnit getStorageUnit() {
        return getFront() instanceof IDeepStorageUnit ? (IDeepStorageUnit) getFront() : null;
    }

    public IItemHandler getItemHandler() {
        return RefinedStorageUtils.getItemHandler(getFront(), getDirection().getOpposite());
    }

    @Override
    public void sendContainerData(ByteBuf buf) {
        super.sendContainerData(buf);

        buf.writeInt(priority);

        IDeepStorageUnit storageUnit = getStorageUnit();

        if (storageUnit != null) {
            buf.writeInt(storageUnit.getStoredItemType() == null ? 0 : storageUnit.getStoredItemType().stackSize);
        } else {
            IItemHandler handler = getItemHandler();

            if (handler != null) {
                int amount = 0;

                for (int i = 0; i < handler.getSlots(); ++i) {
                    if (handler.getStackInSlot(i) != null) {
                        amount += handler.getStackInSlot(i).stackSize;
                    }
                }

                buf.writeInt(amount);
            } else {
                buf.writeInt(0);
            }
        }

        buf.writeInt(compare);
        buf.writeInt(mode);
    }

    @Override
    public void receiveContainerData(ByteBuf buf) {
        super.receiveContainerData(buf);

        priority = buf.readInt();
        stored = buf.readInt();
        compare = buf.readInt();
        mode = buf.readInt();
    }

    @Override
    public Class<? extends Container> getContainer() {
        return ContainerStorage.class;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);

        RefinedStorageUtils.restoreInventory(inventory, 0, nbt);

        if (nbt.hasKey(NBT_PRIORITY)) {
            priority = nbt.getInteger(NBT_PRIORITY);
        }

        if (nbt.hasKey(NBT_COMPARE)) {
            compare = nbt.getInteger(NBT_COMPARE);
        }

        if (nbt.hasKey(NBT_MODE)) {
            mode = nbt.getInteger(NBT_MODE);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);

        RefinedStorageUtils.saveInventory(inventory, 0, nbt);

        nbt.setInteger(NBT_PRIORITY, priority);
        nbt.setInteger(NBT_COMPARE, compare);
        nbt.setInteger(NBT_MODE, mode);
    }

    @Override
    public int getCompare() {
        return compare;
    }

    @Override
    public void setCompare(int compare) {
        markDirty();

        this.compare = compare;
    }

    @Override
    public boolean isWhitelist() {
        return mode == 0;
    }

    @Override
    public boolean isBlacklist() {
        return mode == 1;
    }

    @Override
    public void setToWhitelist() {
        markDirty();

        this.mode = 0;
    }

    @Override
    public void setToBlacklist() {
        markDirty();

        this.mode = 1;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        markDirty();

        this.priority = priority;
    }

    @Override
    public void provide(List<IStorage> storages) {
        storages.add(this);
    }

    @Override
    public String getGuiTitle() {
        return "gui.refinedstorage:external_storage";
    }

    @Override
    public IRedstoneModeConfig getRedstoneModeConfig() {
        return this;
    }

    @Override
    public ICompareConfig getCompareConfig() {
        return this;
    }

    @Override
    public IModeConfig getModeConfig() {
        return this;
    }

    @Override
    public int getStored() {
        return stored;
    }

    @Override
    public int getCapacity() {
        IDeepStorageUnit storageUnit = getStorageUnit();

        if (storageUnit != null) {
            return storageUnit.getMaxStoredCount();
        } else {
            IItemHandler handler = getItemHandler();

            if (handler != null) {
                return handler.getSlots() * 64;
            }
        }

        return 0;
    }

    @Override
    public void onPriorityChanged(int priority) {
        RefinedStorage.NETWORK.sendToServer(new MessagePriorityUpdate(pos, priority));
    }

    @Override
    public IInventory getInventory() {
        return inventory;
    }
}
