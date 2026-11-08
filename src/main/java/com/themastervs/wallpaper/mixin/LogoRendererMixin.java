package com.themastervs.wallpaper.mixin;

import com.themastervs.wallpaper.Elements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Logo de Minecraft del menu principal. */
@Mixin(targets = "net.minecraft.client.gui.components.LogoRenderer")
public abstract class LogoRendererMixin {
    @Inject(method = "renderLogo", at = @At("HEAD"), cancellable = true, require = 0)
    private void tmv$logo(CallbackInfo ci) {
        if (Elements.blocked("logo")) ci.cancel();
    }
}
