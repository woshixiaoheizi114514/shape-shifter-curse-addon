package net.onixary.shapeShifterCurseFabric.ssc_addon.client.evolution;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.custom_ui.BookOfShapeShifterScreenV2_P1;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;

/**
 * SSCA 进化加点系统 - 在幻形者之书界面内注入「进化加点」入口按钮。
 * 纯客户端，用 Fabric ScreenEvents 注入，不修改原版源码。
 *
 * 框架阶段：仅当玩家当前为使魔（familiar_fox）系列形态时显示。
 * 「待后续设计」：显示条件细化（仅走 SSCA 路线 / 仅 Form_3 起步后）、按钮位置与贴图。
 */
public final class EvolutionBookHook {
    private EvolutionBookHook() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof BookOfShapeShifterScreenV2_P1)) {
                return;
            }
            if (client.player == null) {
                return;
            }
            IForm currentForm = RegPlayerFormComponent.PLAYER_FORM.get(client.player).nowForm;
            Identifier formId = (currentForm == null) ? null : currentForm.getFormID();
            // 框架阶段：仅使魔系列形态显示（FormID path 含 familiar_fox）
            if (formId == null || !formId.getPath().contains("familiar_fox")) {
                return;
            }
            ButtonWidget button = ButtonWidget.builder(
                    Text.literal("进化加点"),
                    b -> client.setScreen(new EvolutionScreen(screen))
            ).dimensions(8, 8, 70, 20).build();
            Screens.getButtons(screen).add(button);
        });
    }
}
