package dooms.simpleplayertrades;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ClickType;

public class TradeScreenHandler extends AbstractContainerMenu {

    private final SimpleContainer tradeInventory;
    private final MinecraftServer server;
    private final boolean isRequester;
    private final ActiveTrade trade;

    private static final int ACCEPT_SLOT = 46;
    private static final int DENY_SLOT = 52;

    public TradeScreenHandler(int syncId, Inventory playerInventory, SimpleContainer tradeInventory, MinecraftServer server, boolean isRequester, ActiveTrade trade) {
        super(MenuType.GENERIC_9x6, syncId);

        this.tradeInventory = tradeInventory;
        this.server = server;
        this.isRequester = isRequester;
        this.trade = trade;


        /*
            Register all 54 trade slots
            This annoying way of registering the slots is made so that each player sees their own
            part of the trade inventory on the left side.

            Hardcoding the requester and target inventory would have been much simpler but having your own
            trade window be on the same side regardless of if you are the requester or target
            feels more natural/consistent
        */
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                int visualIndex = row * 9 + col;

                // Build the inventory index based on who is looking
                int inventoryIndex;
                if (row < 5 && !isRequester) {
                    // Mirror columns for the acceptor on item rows only
                    if (col < 4)  inventoryIndex = row * 9 + (col + 5);
                    else if (col == 4) inventoryIndex = row * 9 + 4;
                    else inventoryIndex = row * 9 + (col - 5);
                } else {
                    // Requester sees identity mapping, bottom row same for both
                    inventoryIndex = visualIndex;
                }

                // Permissions are based on inventory index, not visual position
                // This way they stay correct regardless of which side you're looking at
                final int invCol = inventoryIndex % 9;
                final int invRow = inventoryIndex / 9;
                final boolean isBorderSlot = invCol == 4 || invRow == 5;
                final boolean isRequesterSlot = invCol < 4 && invRow < 5;
                final boolean isTargetSlot = invCol > 4 && invRow < 5;
                final boolean isMySlot = isRequester ? isRequesterSlot : isTargetSlot;

                this.addSlot(new Slot(tradeInventory, inventoryIndex, 0, 0) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        if (isBorderSlot) return false;
                        return isMySlot;
                    }

                    @Override
                    public boolean mayPickup(Player player) {
                        if (isBorderSlot) return false;
                        return isMySlot;
                    }
                });
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

    public static void setupLayout(SimpleContainer inventory) {
        // Named blank glass pane for borders
        ItemStack border = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        border.set(DataComponents.CUSTOM_NAME,
                Component.literal(" ").withStyle(s -> s.withItalic(false)));

        // Fill center divider column
        for (int row = 0; row < 6; row++) {
            inventory.setItem(4 + row * 9, border.copy());
        }

        // Fill entire bottom row with border
        for (int col = 0; col < 9; col++) {
            inventory.setItem(45 + col, border.copy());
        }

        // Overwrite slot 46 with accept button
        ItemStack accept = new ItemStack(Items.LIME_CONCRETE);
        accept.set(DataComponents.CUSTOM_NAME,
                Component.literal("✔ Accept Trade").withStyle(s -> s.withItalic(false).withColor(0x55FF55)));
        inventory.setItem(46, accept);

        // Overwrite slot 52 with deny button
        ItemStack deny = new ItemStack(Items.RED_CONCRETE);
        deny.set(DataComponents.CUSTOM_NAME,
                Component.literal("✘ Cancel Trade").withStyle(s -> s.withItalic(false).withColor(0xFF5555)));
        inventory.setItem(52, deny);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);

        // Nothing here, or this player isn't allowed to pick up from this slot
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack originalStack = stack.copy();

        trade.resetConfirm(isRequester);
        updateConfirmVisuals(tradeInventory, trade);

        if (slotIndex < 54) {
            // Shift-clicking from trade inventory → move to player inventory
            if (!moveItemStackTo(stack, 54, 90, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Shift-clicking from player inventory → move to trade inventory
            // mayPlace() on each slot enforces permissions automatically
            // so we can safely pass the full trade range 0-54
            if (!moveItemStackTo(stack, 0, 54, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        // Nothing actually moved
        if (stack.getCount() == originalStack.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, stack);
        return originalStack;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        TradeManager.getInstance().handleGuiClose((ServerPlayer) player, server);
    }

    @Override
    public void clicked(int slotIndex, int button, ClickType clickType, Player player) {
        // Intercept accept button
        if (slotIndex == ACCEPT_SLOT) {
            TradeManager.getInstance().handleConfirm(trade, (ServerPlayer) player, server, isRequester);
            return;
        }

        // Intercept deny button
        if (slotIndex == DENY_SLOT) {
            TradeManager.getInstance().handleDeny(trade, (ServerPlayer) player, server);
            return;
        }

        // For any click on the player's own trade item slots,
        // reset their confirmation — they are changing what they're offering
        if (slotIndex >= 0 && slotIndex < 54) {
            int col = slotIndex % 9;
            int row = slotIndex / 9;
            boolean isOwnItemSlot = col < 4 && row < 5; // always visual left side = own side
            if (isOwnItemSlot) {
                trade.resetConfirm(isRequester);
                updateConfirmVisuals(tradeInventory, trade);
            }
        }

        super.clicked(slotIndex, button, clickType, player);
    }

    public static void updateConfirmVisuals(SimpleContainer inventory, ActiveTrade trade) {
        boolean anyConfirmed = trade.isConfirmed(true) || trade.isConfirmed(false);

        ItemStack divider = new ItemStack(anyConfirmed ? Items.LIME_STAINED_GLASS_PANE : Items.BLACK_STAINED_GLASS_PANE);
        divider.set(DataComponents.CUSTOM_NAME, Component.literal(" ").withStyle(s -> s.withItalic(false)));

        // Only update rows 0-4 of the divider — row 5 stays black as part of the button row
        for (int row = 0; row < 5; row++) {
            inventory.setItem(4 + row * 9, divider.copy());
        }
    }

}