package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.render;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import net.onixary.shapeShifterCurseFabric.ssc_addon.client.UpgradeAxolotlSpearRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 进化美西螈「投掷水矛」蓄力期：第一人称把主手水矛（真实物品）举成「过肩、准备投掷」的姿势
 * （矩阵变换包裹渲染）。不替换物品（真水矛已在手，held 谓词自动 3D）。
 */
@Mixin(HeldItemRenderer.class)
public class UpgradeAxolotlFirstPersonSpearMixin {

	@Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
	private void ssc_addon$raiseFpPush(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
			float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (hand == Hand.MAIN_HAND && player != null
				&& UpgradeAxolotlSpearRenderState.isCharging(player.getUuid())) {
			matrices.push();
			// 举矛过肩、准备投掷（第一人称，温和上抬避免转出视野；数值可实机微调）
			matrices.translate(0.0, 0.22, -0.06);
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-30.0F));
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(4.0F));
		}
	}

	@Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
	private void ssc_addon$raiseFpPop(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
			float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (hand == Hand.MAIN_HAND && player != null
				&& UpgradeAxolotlSpearRenderState.isCharging(player.getUuid())) {
			matrices.pop();
		}
	}
}
