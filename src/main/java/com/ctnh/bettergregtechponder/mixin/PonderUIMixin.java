// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 mmyddd

package com.ctnh.bettergregtechponder.mixin;

import com.ctnh.bettergregtechponder.client.ponder.PonderUiButtons;

import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在思索界面里挂「查看 UI 详情」按钮：按钮加进 PonderUI 自己的控件表，所以它跟底部那一排一样被渲染、
 * 被派发点击、按场景进度淡入淡出；排布与图标文字则每帧在 {@link PonderUiButtons} 里更新。
 *
 * <p>用 mixin 而不是事件：这样按钮是 PonderUI 的一等控件（拿得到布局、吃得到 {@code getRenderables()}
 * 那一轮的 fade），也不用赌 {@code Screen#addRenderableWidget} 的可访问性。
 */
@Mixin(PonderUI.class)
public abstract class PonderUIMixin {

    @Inject(method = "init", at = @At("RETURN"), remap = false)
    private void bettergregtechponder$addUiDetailsButton(CallbackInfo ci) {
        PonderUiButtons.attach((PonderUI) (Object) this);
    }

    @Inject(method = "renderWindow", at = @At("HEAD"), remap = false)
    private void bettergregtechponder$beginFrame(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks,
                                                 CallbackInfo ci) {
        PonderUiButtons.beforeRender((PonderUI) (Object) this);
    }

    @Inject(method = "renderWindow", at = @At("RETURN"), remap = false)
    private void bettergregtechponder$drawLabels(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks,
                                                 CallbackInfo ci) {
        PonderUiButtons.afterRender((PonderUI) (Object) this, graphics, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void bettergregtechponder$clickPanel(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        if (PonderUiButtons.clickPanel(x, y, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "tick", at = @At("RETURN"), remap = false)
    private void bettergregtechponder$tickButton(CallbackInfo ci) {
        PonderUiButtons.tick();
    }
}
