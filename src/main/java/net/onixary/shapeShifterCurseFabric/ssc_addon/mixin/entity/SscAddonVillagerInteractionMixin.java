package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.entity;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerEntity.class)
public class SscAddonVillagerInteractionMixin {

	@Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
	private void ssc_addon$preventTrade(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
		try {
			PlayerFormComponent component = RegPlayerFormComponent.PLAYER_FORM.get(player);
			if (component == null || component.nowForm == null) {
				return;
			}
			Identifier formId = component.nowForm.getFormID();
			// 进化使魔：完全禁止和村民交易（任何右键）
			if (FormIdentifiers.UPGRADE_FAMILIAR_FOX.equals(formId)) {
				cir.setReturnValue(ActionResult.PASS);
				return;
			}
			// 灵界之主：保持原有行为（潜行右键时阻止）
			if (player.isSneaking()
					&& new Identifier("my_addon", "form_familiar_fox_sp").equals(formId)) {
				cir.setReturnValue(ActionResult.PASS);
			}
		} catch (Exception ignored) {
		}
	}
}
