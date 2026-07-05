package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.onixary.shapeShifterCurseFabric.data.CodexData;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionNode;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.FamiliarFoxTree;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.RegEvolutionComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CodexData.class)
public class SscAddonCodexStatusMixin {

	@Inject(method = "getPlayerStatusText", at = @At("RETURN"), cancellable = true)
	private static void getPlayerStatusText(PlayerEntity player, CallbackInfoReturnable<Text> cir) {
		// 原版逻辑已完整执行。原版仅在「无任何特殊状态」时返回 codex.status.normal 的内容；
		// 感染中 / 诅咒之月日（白天 before_moon、夜晚 under_moon）等状态由原版正常返回，此处不接管，保留原版文案。
		// 仅当原版返回 normal（即无特殊状态）且玩家处于附属特定形态时，才替换为形态专属文案。
		Text originalReturn = cir.getReturnValue();
		if (originalReturn == null) {
			return;
		}
		String normalText = Text.translatable("codex.status.normal").getString();
		if (!normalText.equals(originalReturn.getString())) {
			// 原版命中了 infected / before_moon / under_moon 等状态，保留原版返回值不动
			return;
		}
		PlayerFormComponent component = RegPlayerFormComponent.PLAYER_FORM.get(player);
		if (component != null) {
			IForm currentForm = component.nowForm;
			if (currentForm != null && currentForm.getFormID() != null) {
				String formPath = currentForm.getFormID().getPath();
				switch (formPath) {
					case "axolotl_sp", "familiar_fox_sp" ->
							cir.setReturnValue(Text.translatable("codex.status.my_addon.SP_status"));
					case "fallen_allay_sp" ->
							cir.setReturnValue(Text.translatable("codex.status.my_addon.fallen_allay_sp_status"));
					case "wild_cat_sp" ->
							cir.setReturnValue(Text.translatable("codex.status.my_addon.wild_cat_sp_status"));
					case "snow_fox_sp" ->
							cir.setReturnValue(Text.translatable("codex.status.my_addon.snow_fox_sp_status"));
					case "familiar_fox_red" ->
							cir.setReturnValue(Text.translatable("codex.status.my_addon.familiar_fox_red_status"));
					// 其它形态（含原版形态）保持原版返回的 normal，不再强制覆盖
					default -> {}
				}
			}
		}
	}

	/**
	 * 进化使魔的 appearance 段动态化：在静态外观描述后追加已解锁加点节点的书页化叙述，
	 * 让书内 appearance 段随加点状态实时变化。其它形态 / 其它 ContentType 走原版逻辑。
	 */
	@Inject(method = "getContentText", at = @At("RETURN"), cancellable = true)
	private static void appendEvolutionToAppearance(CodexData.ContentType type, PlayerEntity player,
													 CallbackInfoReturnable<Text> cir) {
		if (type != CodexData.ContentType.APPEARANCE) {
			return;
		}
		PlayerFormComponent component = RegPlayerFormComponent.PLAYER_FORM.get(player);
		if (component == null) {
			return;
		}
		IForm currentForm = component.nowForm;
		if (currentForm == null || currentForm.getFormID() == null) {
			return;
		}
		if (!FormIdentifiers.UPGRADE_FAMILIAR_FOX.equals(currentForm.getFormID())) {
			return;
		}
		EvolutionComponent comp = RegEvolutionComponent.EVOLUTION.get(player);
		if (comp == null) {
			return;
		}
		Text base = cir.getReturnValue();
		if (base == null) {
			return;
		}
		// 统计已解锁的可加点节点数
		int unlockableCount = 0;
		int unlockedCount = 0;
		for (EvolutionNode node : FamiliarFoxTree.nodes()) {
			if (FamiliarFoxTree.NODE_BASE.equals(node.id)) {
				continue;
			}
			unlockableCount++;
			if (comp.isUnlocked(node.id)) {
				unlockedCount++;
			}
		}
		MutableText dynamic = Text.empty();
		dynamic.append(base);
		dynamic.append(Text.literal("\n\n"));
		dynamic.append(Text.translatable("text.ssc_addon.evolution.book.summary_title"));
		dynamic.append(Text.literal("\n"));
		dynamic.append(Text.translatable("text.ssc_addon.evolution.book.summary_stats",
				unlockedCount, unlockableCount, comp.getPoints(), player.experienceLevel));
		// 逐个已解锁节点追加书页化叙述
		for (EvolutionNode node : FamiliarFoxTree.nodes()) {
			if (FamiliarFoxTree.NODE_BASE.equals(node.id)) {
				continue;
			}
			if (!comp.isUnlocked(node.id)) {
				continue;
			}
			dynamic.append(Text.literal("\n\n\u2022 "));
			dynamic.append(Text.translatable(node.nameKey));
			dynamic.append(Text.literal("\n"));
			String bookKey = node.descKey.substring(0, node.descKey.length() - 5) + ".book";
			dynamic.append(Text.translatable(bookKey));
		}
		cir.setReturnValue(dynamic);
	}
}
