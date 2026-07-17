package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.render;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.util.Hand;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.UpgradeAxolotlSpearRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 进化美西螈「投掷水矛」蓄力期：把主手臂姿势设为原版「投掷长矛」姿势（THROW_SPEAR），
 * 即与手持三叉戟/水矛按住右键蓄力时完全一致的举矛过肩姿态（第一/三人称通用）。
 */
@Mixin(PlayerEntityRenderer.class)
public class UpgradeAxolotlSpearArmPoseMixin {

	@Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
	private static void ssc_addon$spearThrowPose(AbstractClientPlayerEntity player, Hand hand,
			CallbackInfoReturnable<BipedEntityModel.ArmPose> cir) {
		if (hand == Hand.MAIN_HAND && UpgradeAxolotlSpearRenderState.isCharging(player.getUuid())) {
			cir.setReturnValue(BipedEntityModel.ArmPose.THROW_SPEAR);
		}
	}
}
