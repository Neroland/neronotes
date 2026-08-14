package za.co.neroland.neronotes.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import za.co.neroland.neronotes.score.Score;

/**
 * A pressed custom disk: carries a {@link DiskContents} component (score,
 * title, authorship, palette). Created only by the Disk Press
 * ({@link #createStack}) — the title becomes the item name, coloured by the
 * dominant voice family, and the label glows via the vanilla glint override.
 *
 * <p><strong>Anonymous authorship</strong>: when the composer opted out of
 * credit, the tooltip shows the translated "Anonymous" line. The author UUID
 * inside the component exists solely for data erasure and appears on no
 * display surface — not here, not anywhere.</p>
 */
public class CustomDiskItem extends Item {

    public CustomDiskItem(Item.Properties properties) {
        super(properties);
    }

    /** Build the pressed disk stack: component + palette-coloured name + glowing label. */
    public static ItemStack createStack(Item diskItem, DiskContents contents) {
        ItemStack stack = new ItemStack(diskItem);
        stack.set(NeroNotesDataComponents.DISK_CONTENTS.get(), contents);
        int rgb = contents.family().accentColour() & 0xFFFFFF;
        stack.set(DataComponents.ITEM_NAME, Component.literal(contents.title())
                .withStyle(style -> style.withColor(TextColor.fromRgb(rgb)).withItalic(false)));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    /** The disk's contents, or {@code null} for a component-less stack. */
    public static DiskContents contentsOf(ItemStack stack) {
        return stack.get(NeroNotesDataComponents.DISK_CONTENTS.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        DiskContents contents = contentsOf(stack);
        if (contents == null) {
            tooltip.accept(Component.translatable("neronotes.tooltip.custom_disk.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        // Author line: display name when credited, the translated "Anonymous"
        // line otherwise. The author UUID is never shown.
        Component author = contents.authorDisplay()
                .map(name -> Component.translatable("neronotes.disk.author", name)
                        .withStyle(ChatFormatting.GRAY))
                .orElseGet(() -> Component.translatable("neronotes.disk.author_anonymous")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.accept(author);
        Score score = contents.score();
        tooltip.accept(Component.translatable("neronotes.disk.stats",
                        score.layers().size(), score.noteCount(), score.tempoBpm())
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable(contents.family().translationKey())
                .withStyle(style -> style.withColor(TextColor.fromRgb(contents.family().accentColour() & 0xFFFFFF))));
    }
}
