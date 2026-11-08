package com.themastervs.wallpaper.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.themastervs.wallpaper.Elements;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Intercepta texturas y textos para poder ocultar elementos concretos de la GUI. */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {

    @Inject(method = "blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void tmv$blit10(RenderPipeline p, Identifier id, int x, int y, float u, float v, int w, int h,
                            int tw, int th, CallbackInfo ci) {
        if (Elements.active() && Elements.blocked("tex:" + id)) ci.cancel();
    }

    @Inject(method = "blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIII)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void tmv$blit12(RenderPipeline p, Identifier id, int x, int y, float u, float v, int w, int h,
                            int rw, int rh, int tw, int th, CallbackInfo ci) {
        if (Elements.active() && Elements.blocked("tex:" + id)) ci.cancel();
    }

    @Inject(method = "blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void tmv$blit13(RenderPipeline p, Identifier id, int x, int y, float u, float v, int w, int h,
                            int rw, int rh, int tw, int th, int color, CallbackInfo ci) {
        if (Elements.active() && Elements.blocked("tex:" + id)) ci.cancel();
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void tmv$textSeq(Font f, FormattedCharSequence t, int x, int y, int c, boolean s, CallbackInfo ci) {
        if (!Elements.active() || t == null) return;
        StringBuilder sb = new StringBuilder();
        t.accept((i, st, cp) -> { sb.appendCodePoint(cp); return true; });
        if (Elements.blockedText(sb.toString())) ci.cancel();
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void tmv$textStr(Font f, String t, int x, int y, int c, boolean s, CallbackInfo ci) {
        if (Elements.active() && Elements.blockedText(t)) ci.cancel();
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void tmv$textComp(Font f, Component t, int x, int y, int c, boolean s, CallbackInfo ci) {
        if (Elements.active() && t != null && Elements.blockedText(t.getString())) ci.cancel();
    }
}
