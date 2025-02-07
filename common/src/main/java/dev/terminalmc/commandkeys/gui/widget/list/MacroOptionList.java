/*
 * Copyright 2024 TerminalMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.terminalmc.commandkeys.gui.widget.list;

import com.mojang.blaze3d.platform.InputConstants;
import dev.terminalmc.commandkeys.config.*;
import dev.terminalmc.commandkeys.util.KeybindUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static dev.terminalmc.commandkeys.config.Macro.ConflictStrategy.*;
import static dev.terminalmc.commandkeys.config.Macro.SendMode.*;
import static dev.terminalmc.commandkeys.util.Localization.localized;

public class MacroOptionList extends MacroBindList {
    private final Macro macro;
    private int dragSourceSlot = -1;

    public MacroOptionList(Minecraft mc, int width, int height, int y,
                           int itemHeight, int entryWidth, int entryHeight,
                           Profile profile, Macro macro) {
        super(mc, width, height, y, itemHeight, entryWidth, entryHeight, profile);
        this.macro = macro;

        addEntry(new Entry.BindAndControlsEntry(entryX, entryWidth, entryHeight, this, profile, macro));

        if (profile.getShowHudMessage().equals(Profile.Control.DEFER)
                || profile.getAddToHistory().equals(Profile.Control.DEFER)) {
            addEntry(new Entry.HudAndHistoryEntry(entryX, entryWidth, entryHeight, profile, macro));
        }

        addEntry(new Entry.StrategyAndModeEntry(entryX, entryWidth, entryHeight, this, profile, macro));

        addEntry(new OptionList.Entry.TextEntry(entryX, entryWidth, entryHeight,
                localized("option", "key.messages"), null, -1));

        int i = 0;
        for (Message msg : macro.getMessages()) {
            Entry msgEntry = new Entry.MessageEntry(dynEntryX, dynEntryWidth, entryHeight,
                    this, macro, msg, i++);
            addEntry(msgEntry);
            addEntry(new OptionList.Entry.SpaceEntry(msgEntry));
        }
        addEntry(new OptionList.Entry.ActionButtonEntry(entryX, entryWidth, entryHeight,
                Component.literal("+"), null, -1,
                (button) -> {
                    macro.addMessage(new Message());
                    reload();
                }));
    }

    @Override
    protected OptionList reload(int width, int height, double scrollAmount) {
        MacroOptionList newListWidget = new MacroOptionList(minecraft, width, height,
                getY(), itemHeight, entryWidth, entryHeight, profile, macro);
        newListWidget.setScrollAmount(scrollAmount);
        return newListWidget;
    }

    void focusDelayField() {
        for (OptionList.Entry e : children()) {
            if (e instanceof Entry.StrategyAndModeEntry entry) {
                entry.focusDelayField();
                entry.setFocused(true);
                setFocused(entry);
                screen.setFocused(this);
                return;
            }
        }
    }

    // Message dragging

    @Override
    public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderWidget(graphics, mouseX, mouseY, delta);
        if (dragSourceSlot != -1) {
            super.renderItem(graphics, mouseX, mouseY, delta, dragSourceSlot,
                    mouseX, mouseY, entryWidth, entryHeight);
        }
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (dragSourceSlot != -1 && button == InputConstants.MOUSE_BUTTON_LEFT) {
            dropDragged(x, y);
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    private void dropDragged(double mouseX, double mouseY) {
        OptionList.Entry hoveredEntry = getEntryAtPosition(mouseX, mouseY);
        int hoveredSlot = children().indexOf(hoveredEntry);
        // Check whether the drop location is valid
        if (hoveredEntry instanceof Entry.MessageEntry || hoveredSlot == messageListOffset() - 1) {
            // pass
        } else if (hoveredEntry instanceof OptionList.Entry.SpaceEntry) {
            hoveredSlot -= 1; // Reference the 'parent' Entry
        } else {
            this.dragSourceSlot = -1;
            return;
        }
        // Check whether the move operation would actually change anything
        if (hoveredSlot > dragSourceSlot || hoveredSlot < dragSourceSlot - 1) {
            // Account for the list not starting at slot 0
            int sourceIndex = dragSourceSlot - messageEntryOffset(dragSourceSlot);
            int destIndex = hoveredSlot - messageEntryOffset(hoveredSlot);
            // I can't really explain why
            if (sourceIndex > destIndex) destIndex += 1;
            // Move
            macro.moveMessage(sourceIndex, destIndex);
            reload();
        }
        this.dragSourceSlot = -1;
    }

    /**
     * @return The index of the first {@link Entry.MessageEntry} in the
     * {@link OptionList}.
     */
    private int messageListOffset() {
        int i = 0;
        for (OptionList.Entry entry : children()) {
            if (entry instanceof Entry.MessageEntry) return i;
            i++;
        }
        throw new IllegalStateException("Response list not found");
    }

    /**
     * @return The number of non-{@link Entry.MessageEntry} entries in the
     * {@link OptionList} before (and including) the specified index.
     */
    private int messageEntryOffset(int index) {
        int i = 0;
        int offset = 0;
        for (OptionList.Entry entry : children()) {
            if (!(entry instanceof Entry.MessageEntry)) offset++;
            if (i++ == index) return offset;
        }
        throw new IllegalStateException("Response index out of range");
    }

    private abstract static class Entry extends OptionList.Entry {
        private static class HudAndHistoryEntry extends Entry {
            HudAndHistoryEntry(int x, int width, int height, Profile profile, Macro macro) {
                super();
                int buttonWidth = (width - SPACING) / 2;

                CycleButton<Boolean> hudButton = CycleButton.booleanBuilder(
                                CommonComponents.OPTION_ON.copy().withStyle(ChatFormatting.GREEN),
                                CommonComponents.OPTION_OFF.copy().withStyle(ChatFormatting.RED))
                        .withInitialValue(macro.getShowHudMessage())
                        .withTooltip((status) -> Tooltip.create(
                                localized("option", "macro.hud.tooltip")))
                        .create(x, 0, buttonWidth, height,
                                localized("option", "macro.hud"),
                                (button, status) -> profile.setShowHudMessage(macro, status));
                hudButton.setTooltipDelay(Duration.ofMillis(500));
                hudButton.active = profile.getShowHudMessage().equals(Profile.Control.DEFER);
                elements.add(hudButton);

                CycleButton<Boolean> historyButton = CycleButton.booleanBuilder(
                                CommonComponents.OPTION_ON.copy().withStyle(ChatFormatting.GREEN),
                                CommonComponents.OPTION_OFF.copy().withStyle(ChatFormatting.RED))
                        .withInitialValue(macro.getAddToHistory())
                        .withTooltip((status) -> Tooltip.create(
                                localized("option", "macro.history.tooltip")))
                        .create(x + width - buttonWidth, 0, buttonWidth, height,
                                localized("option", "macro.history"),
                                (button, status) -> profile.setAddToHistory(macro, status));
                historyButton.setTooltipDelay(Duration.ofMillis(500));
                historyButton.active = profile.getAddToHistory().equals(Profile.Control.DEFER);
                elements.add(historyButton);
            }
        }

        private static class BindAndControlsEntry extends Entry {
            BindAndControlsEntry(int x, int width, int height, MacroOptionList list,
                                 Profile profile, Macro macro) {
                super();
                int buttonWidth = (width - SPACING) / 2;

                KeybindUtil.KeybindInfo info = 
                        new KeybindUtil.KeybindInfo(profile, macro, macro.getKeybind());
                elements.add(Button.builder(info.conflictLabel,
                                (button) -> {
                                    list.setSelected(macro, macro.getKeybind());
                                    button.setMessage(Component.literal("> ")
                                            .append(info.label.withStyle(ChatFormatting.WHITE)
                                                    .withStyle(ChatFormatting.UNDERLINE))
                                            .append(" <").withStyle(ChatFormatting.YELLOW));
                                })
                        .tooltip(Tooltip.create(info.tooltip))
                        .pos(x, 0)
                        .size(buttonWidth, height)
                        .build());

                if (macro.getMode().equals(CYCLE)) {
                    KeybindUtil.KeybindInfo altInfo = 
                            new KeybindUtil.KeybindInfo(profile, macro, macro.getAltKeybind());
                    elements.add(Button.builder(altInfo.conflictLabel,
                                    (button) -> {
                                        list.setSelected(macro, macro.getAltKeybind());
                                        button.setMessage(Component.literal("> ")
                                                .append(altInfo.label.withStyle(ChatFormatting.WHITE)
                                                        .withStyle(ChatFormatting.UNDERLINE))
                                                .append(" <").withStyle(ChatFormatting.YELLOW));
                                    })
                            .tooltip(Tooltip.create(altInfo.tooltip.getString().isBlank() 
                                    ? localized("option", "key.alt.tooltip") : altInfo.tooltip))
                            .pos(x + width - buttonWidth, 0)
                            .size(buttonWidth, height)
                            .build());
                } else {
                    elements.add(CycleButton.booleanBuilder(
                                    CommonComponents.OPTION_ON.copy().withStyle(ChatFormatting.GREEN),
                                    CommonComponents.OPTION_OFF.copy().withStyle(ChatFormatting.RED))
                            .withInitialValue(macro.ignoreRatelimit)
                            .withTooltip((status) -> Tooltip.create(
                                    localized("option", "macro.ignoreRatelimit.tooltip")))
                            .create(x + width - buttonWidth, 0, buttonWidth, height,
                                    localized("option", "macro.ignoreRatelimit"),
                                    (button, status) -> macro.ignoreRatelimit = status));
                }
            }
        }

        private static class StrategyAndModeEntry extends Entry {
            private EditBox delayField;

            StrategyAndModeEntry(int x, int width, int height, MacroOptionList list, 
                                 Profile profile, Macro macro) {
                super();
                Font font = Minecraft.getInstance().font;
                int buttonWidth = (width - SPACING) / 2;
                int minDelayFieldWidth = font.width("0_") + 8;
                int stopButtonWidth = font.width("Stop") + 8;
                int modeButtonWidth = switch(macro.getMode()) {
                    case SEND -> buttonWidth - minDelayFieldWidth;
                    case TYPE, RANDOM -> buttonWidth;
                    case CYCLE -> buttonWidth - list.smallButtonWidth;
                    case REPEAT -> buttonWidth -
                            (macro.hasRepeating() ? stopButtonWidth : minDelayFieldWidth);
                };

                // Conflict strategy button
                CycleButton<Macro.ConflictStrategy> conflictButton = CycleButton.builder(
                        KeybindUtil::localizeStrategy)
                        .withValues(Macro.ConflictStrategy.values())
                        .withInitialValue(macro.getStrategy())
                        .withTooltip((status) -> Tooltip.create(
                                KeybindUtil.localizeStrategyTooltip(status)))
                        .create(x, 0, buttonWidth, height,
                                localized("option", "key.conflict"),
                                (button, status) -> {
                                    profile.setConflictStrategy(macro, status);
                                    list.reload();
                                });
                elements.add(conflictButton);

                // Send mode button
                CycleButton<Macro.SendMode> modeButton = CycleButton.builder(
                        KeybindUtil::localizeMode)
                        .withValues(Macro.SendMode.values())
                        .withInitialValue(macro.getMode())
                        .withTooltip((status) -> Tooltip.create(
                                KeybindUtil.localizeModeTooltip(status)))
                        .create(x + width - buttonWidth, 0, modeButtonWidth, height,
                                localized("option", "key.mode"),
                                (button, status) -> {
                                    profile.setSendMode(macro, status);
                                    list.reload();
                                });
                elements.add(modeButton);

                if (macro.getMode().equals(SEND)
                        || (macro.getMode().equals(REPEAT) && !macro.hasRepeating())) {
                    // Delay field
                    delayField = new EditBox(font, x + width - minDelayFieldWidth, 0,
                            minDelayFieldWidth, height, Component.empty());
                    delayField.setMaxLength(8);
                    delayField.setResponder((val) -> {
                        // Resize
                        int newWidth = Math.max(minDelayFieldWidth,
                                font.width(val) + font.width("_") + 8);
                        int deltaWidth = delayField.getWidth() - newWidth;
                        modeButton.setWidth(modeButton.getWidth() + deltaWidth);
                        delayField.setX(delayField.getX() + deltaWidth);
                        delayField.setWidth(delayField.getWidth() - deltaWidth);
                        // Actual responder
                        try {
                            int space = Integer.parseInt(val.strip());
                            if (space < 0) throw new NumberFormatException();
                            int oldSpace = macro.spaceTicks;
                            macro.spaceTicks = space;
                            // Show/hide per-message delay fields
                            if (macro.getMode() == SEND
                                    && ((space == 0 && oldSpace != 0) || (space != 0 && oldSpace == 0))) {
                                ((MacroOptionList)list.reload()).focusDelayField();
                            } else {
                                delayField.setTextColor(16777215);
                            }
                        } catch (NumberFormatException ignored) {
                            delayField.setTextColor(16711680);
                        }
                    });
                    delayField.setValue(String.valueOf(macro.spaceTicks));
                    // Workaround to prevent the value sliding off to the left
                    delayField.setCursorPosition(0);
                    delayField.setHighlightPos(0);
                    delayField.setTooltip(Tooltip.create(localized("option",
                            "key.delay.tooltip" + (macro.getMode().equals(REPEAT) ? ".repeat" : ""))));

                    elements.add(delayField);
                }
                else if (macro.getMode().equals(REPEAT)) {
                    // Has repeating messages, provide stop button
                    elements.add(Button.builder(localized("option", "key.repeat.stop"),
                            (button) -> {
                                macro.stopRepeating();
                                list.reload();
                            })
                            .tooltip(Tooltip.create(
                                    localized("option", "key.repeat.stop.tooltip")))
                            .pos(x + width - stopButtonWidth, 0)
                            .size(stopButtonWidth, height)
                            .build());
                }
                else if (macro.getMode().equals(CYCLE)) {
                    // Cycle index button
                    List<Integer> values = new ArrayList<>();
                    for (int i = 0; i < macro.getMessages().size(); i++) values.add(i);
                    if (values.isEmpty()) values.add(0);
                    if (macro.cycleIndex > values.getLast()) macro.cycleIndex = 0;
                    elements.add(CycleButton.<Integer>builder(
                                    (status) -> Component.literal(status.toString()))
                            .withValues(values)
                            .withInitialValue(macro.cycleIndex)
                            .displayOnlyValue()
                            .withTooltip((status) -> Tooltip.create(
                                    localized("option", "key.cycle.index.tooltip")))
                            .create(x + width - list.smallButtonWidth, 0,
                                    list.smallButtonWidth, height, Component.empty(),
                                    (button, status) -> macro.cycleIndex = status));
                }
            }

            void focusDelayField() {
                delayField.setFocused(true);
                this.setFocused(delayField);
                int pos = delayField.getValue().length();
                delayField.setCursorPosition(pos);
                delayField.setHighlightPos(pos);
            }
        }

        private static class MessageEntry extends Entry {
            MessageEntry(int x, int width, int height, MacroOptionList list,
                         Macro macro, Message msg, int index) {
                super();
                Font font = Minecraft.getInstance().font;
                boolean showDelayField = (macro.getStrategy() == AVOID
                        || (macro.getMode() == SEND && macro.spaceTicks == 0)
                        || macro.getMode() == REPEAT);
                int minDelayFieldWidth = font.width("0__") + 8;
                int msgFieldWidth = width - list.smallButtonWidth * 2 - SPACING * 2
                        - (showDelayField ? minDelayFieldWidth + SPACING : 0);

                // Drag reorder button
                elements.add(Button.builder(Component.literal("\u2191\u2193"),
                                (button) -> {
                                    this.setDragging(true);
                                    list.dragSourceSlot = list.children().indexOf(this);
                                })
                        .pos(x, 0)
                        .size(list.smallButtonWidth, height)
                        .build());

                // Message field
                MultiLineEditBox messageField = new MultiLineEditBox(font,
                        x + list.smallButtonWidth + SPACING, 0, msgFieldWidth, height * 2,
                        Component.empty(), Component.empty());
                messageField.setCharacterLimit(256);
                messageField.setValue(msg.string);
                messageField.setValueListener((val) -> msg.string = val.stripLeading());
                elements.add(messageField);

                // Delay field
                if (showDelayField) {
                    EditBox delayField = new EditBox(font,
                            x + list.smallButtonWidth + msgFieldWidth + SPACING * 2, 0,
                            minDelayFieldWidth, height, Component.empty());
                    delayField.setTooltip(Tooltip.create(
                            localized("option", "key.delay.individual.tooltip"
                                    + (index == 0 ? ".first" : ".subsequent"))));
                    delayField.setTooltipDelay(Duration.ofMillis(500));
                    delayField.setMaxLength(8);
                    delayField.setResponder((val) -> {
                        // Resize
                        int newWidth = Math.max(minDelayFieldWidth,
                                font.width(val) + font.width("__") + 8);
                        int deltaWidth = delayField.getWidth() - newWidth;
                        messageField.setWidth(messageField.getWidth() + deltaWidth);
                        delayField.setX(delayField.getX() + deltaWidth);
                        delayField.setWidth(delayField.getWidth() - deltaWidth);
                        // Actual responder
                        try {
                            int delay = Integer.parseInt(val.strip());
                            if (delay < 0) throw new NumberFormatException();
                            msg.delayTicks = delay;
                            delayField.setTextColor(16777215);
                        } catch (NumberFormatException ignored) {
                            delayField.setTextColor(16711680);
                        }
                    });
                    delayField.setValue(String.valueOf(msg.delayTicks));
                    // Workaround to prevent the value sliding off to the left
                    delayField.setCursorPosition(0);
                    delayField.setHighlightPos(0);
                    elements.add(delayField);
                }

                // Delete button
                elements.add(Button.builder(Component.literal("\u274C")
                                        .withStyle(ChatFormatting.RED),
                                (button) -> {
                                    macro.removeMessage(index);
                                    list.reload();
                                })
                        .pos(x + width - list.smallButtonWidth, 0)
                        .size(list.smallButtonWidth, height)
                        .build());
            }
        }
    }
}
