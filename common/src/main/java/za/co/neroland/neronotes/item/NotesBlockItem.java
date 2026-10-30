package za.co.neroland.neronotes.item;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
//? if >=1.21.5 {
import net.minecraft.world.item.component.TooltipDisplay;
//?}
import net.minecraft.world.level.block.Block;

/**
 * NeroNotes' {@link BlockItem} with translated tooltip lines. Tooltips live
 * HERE by convention — {@code Block} has no hover text in 26.x, so any
 * per-block description belongs on the item.
 */
public class NotesBlockItem extends BlockItem {

    private final List<String> tooltipKeys;

    public NotesBlockItem(Block block, Item.Properties properties, String... tooltipKeys) {
        super(block, properties);
        this.tooltipKeys = List.of(tooltipKeys);
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
        for (String key : tooltipKeys) {
            tooltip.accept(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
    }
}
