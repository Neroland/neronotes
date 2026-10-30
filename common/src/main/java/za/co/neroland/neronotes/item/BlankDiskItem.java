package za.co.neroland.neronotes.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
//? if >=1.21.5 {
import net.minecraft.world.item.component.TooltipDisplay;
//?}

/**
 * A blank resonant disk: the Disk Press writes a Soundforge session score
 * onto it, turning it into a {@link CustomDiskItem}. Craftable in survival
 * (see {@code data/neronotes/recipe/blank_disk.json}); carries no data.
 */
public class BlankDiskItem extends Item {

    public BlankDiskItem(Item.Properties properties) {
        super(properties);
    }

    //? if >=1.21.5 {
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
    //?} else {
    /*@Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                java.util.List<Component> tooltipLines, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltipLines, flag);
        Consumer<Component> tooltip = tooltipLines::add;
    *///?}
        tooltip.accept(Component.translatable("neronotes.tooltip.blank_disk").withStyle(ChatFormatting.GRAY));
    }
}
