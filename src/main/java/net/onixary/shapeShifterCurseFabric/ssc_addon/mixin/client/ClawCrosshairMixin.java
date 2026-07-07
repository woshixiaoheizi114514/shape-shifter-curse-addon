package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.client;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.ClawClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 风灵「疾风连爪」准星攻击冷却条接管。
 *
 * 仅重定向 {@code InGameHud.renderCrosshair} 里那一次 {@code getAttackCooldownProgress} 调用（=渲染准星
 * 攻击充能指示器用的进度）：风灵爪击/过热时返回自定义进度（爪击间隔充能 / 过热回复），否则返回原值。
 *
 * ⚠ 关键：不改 {@code PlayerEntity.attack()} 里那处 getAttackCooldownProgress → MC 原版攻击冷却减伤照常
 * 生效（渲染与减伤分离）。只用这一个原版准星条，不额外画自定义横条。
 */
@Mixin(InGameHud.class)
public class ClawCrosshairMixin {

    @Redirect(
            method = "renderCrosshair",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;getAttackCooldownProgress(F)F"))
    private float ssc_addon$clawCrosshairProgress(PlayerEntity player, float tickDelta) {
        if (ClawClientState.isActive()) {
            return ClawClientState.getCrosshairProgress();
        }
        return player.getAttackCooldownProgress(tickDelta);
    }
}
