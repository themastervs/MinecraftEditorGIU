package com.themastervs.wallpaper.mixin;

import com.themastervs.wallpaper.Elements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Texto amarillo (splash) del menu principal. */
@Mixin(targets = "net.minecraft.client.gui.components.SplashRenderer")
public abstract class SplashRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void tmv$splash(CallbackInfo ci) {
        if (Elements.blocked("splash")) ci.cancel();
    }
}
