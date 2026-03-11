package dooms.simpleplayertrades;

import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class TradeScreenHandler extends AbstractContainerMenu {

    // The shared 54-slot inventory that both players look at
    private final SimpleContainer tradeInventory;

    public TradeScreenHandler(int syncId, Inventory playerInventory, SimpleContainer tradeInventory) {
        super(MenuType.GENERIC_9x6, syncId);

        this.tradeInventory = tradeInventory;

        // Register all 54 trade slots (6 rows of 9)
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(tradeInventory, col + row * 9, 0, 0));
            }
        }

        // Register the player's own inventory slots (3 rows of 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 0, 0));
            }
        }

        // Register the player's hotbar (9 slots)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 0, 0));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        // Disable shift-clicking entirely for now
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}