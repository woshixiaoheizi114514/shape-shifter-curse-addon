package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;
import net.onixary.shapeShifterCurseFabric.player_form.utils.TransformManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 在主包私有 setForm 末尾注入：每当玩家变身到附属形态时触发统一成就触发器。
// setForm 是所有变身路径的共同终点——immediatelyTransform（瞬变）与 startTransform
// 的计时流程（middlePlayerTransform → setForm）都会调用它，故注入此处可统一覆盖
// 瞬变与黑屏动画两种变身，避免遗漏成就（此前仅注入 immediatelyTransform，导致
// 月髓环/进化石改用 startTransform 后形态成就不触发）。
@Mixin(TransformManager.class)
public class TransformManagerAdvancementMixin {
    @Inject(method = "setForm", at = @At("TAIL"))
    private static void ssc_addon$onAddonFormTransform(PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity sp)) return;
        IForm toForm = FormUtils.getPlayerForm(player);
        if (toForm == null || toForm.getFormID() == null) return;
        Identifier id = toForm.getFormID();
        // 仅对 my_addon 命名空间生效，避免误触发主包/其它附属
        if (!"my_addon".equals(id.getNamespace())) return;
        SscAddon.ON_TRANSFORM_ADDON_FORM.trigger(sp, id);
    }
}
