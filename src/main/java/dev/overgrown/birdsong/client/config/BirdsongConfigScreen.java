package dev.overgrown.birdsong.client.config;

import dev.overgrown.birdsong.client.BirdsongClientConfig;
import dev.overgrown.birdsong.client.speech.SpeechClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class BirdsongConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int WIDGET_WIDTH = 260;

    private final @Nullable Screen parent;
    private @Nullable EditBox inputDevice;
    private @Nullable EditBox model;

    public BirdsongConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("birdsong.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        BirdsongClientConfig config = BirdsongClientConfig.get();
        int x = this.width / 2 - WIDGET_WIDTH / 2;
        int y = Math.max(32, this.height / 2 - ROW_HEIGHT * 4);

        this.addRenderableWidget(CycleButton.<BirdsongClientConfig.Speech>builder(value ->
                Component.translatable("birdsong.config.speech." + value.getSerializedName()))
            .withValues(BirdsongClientConfig.Speech.values())
            .withInitialValue(config.speech())
            .withTooltip(value -> Tooltip.create(Component.translatable("birdsong.config.speech.tooltip")))
            .create(x, y, WIDGET_WIDTH, 20, Component.translatable("birdsong.config.speech"),
                (button, value) -> config.setSpeech(value)));
        y += ROW_HEIGHT;
        this.addRenderableWidget(CycleButton.<BirdsongClientConfig.Source>builder(value ->
                Component.translatable("birdsong.config.source." + value.getSerializedName()))
            .withValues(BirdsongClientConfig.Source.values())
            .withInitialValue(config.source())
            .withTooltip(value -> Tooltip.create(Component.translatable("birdsong.config.source.tooltip")))
            .create(x, y, WIDGET_WIDTH, 20, Component.translatable("birdsong.config.source"),
                (button, value) -> config.setSource(value)));
        y += ROW_HEIGHT;
        this.addRenderableWidget(toggle("push_to_talk", config.pushToTalk(), x, y, config::setPushToTalk));
        y += ROW_HEIGHT;
        this.addRenderableWidget(toggle("instant", config.instant(), x, y, config::setInstant));
        y += ROW_HEIGHT;
        this.addRenderableWidget(toggle("echo", config.echo(), x, y, config::setEcho));
        y += ROW_HEIGHT;
        this.inputDevice = box(x, y, "input_device", config.inputDevice());
        y += ROW_HEIGHT;
        this.model = box(x, y, "model", config.model());
        y += ROW_HEIGHT + 8;

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
            .bounds(this.width / 2 - 100, y, 200, 20)
            .build());
    }

    private EditBox box(int x, int y, String key, String value) {
        EditBox box = new EditBox(this.font, x, y, WIDGET_WIDTH, 20, Component.translatable("birdsong.config." + key));
        box.setMaxLength(256);
        box.setValue(value);
        box.setHint(Component.translatable("birdsong.config." + key + ".hint"));
        box.setTooltip(Tooltip.create(Component.translatable("birdsong.config." + key + ".tooltip")));
        return this.addRenderableWidget(box);
    }

    private CycleButton<Boolean> toggle(String key, boolean initial, int x, int y, BooleanSetter setter) {
        return CycleButton.onOffBuilder(initial)
            .withTooltip(value -> Tooltip.create(Component.translatable("birdsong.config." + key + ".tooltip")))
            .create(x, y, WIDGET_WIDTH, 20, Component.translatable("birdsong.config." + key),
                (button, value) -> setter.set(value));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        BirdsongClientConfig config = BirdsongClientConfig.get();
        if (this.inputDevice != null) config.setInputDevice(this.inputDevice.getValue());
        if (this.model != null) config.setModel(this.model.getValue());
        SpeechClient.restart();
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    @FunctionalInterface
    private interface BooleanSetter {
        void set(boolean value);
    }
}
