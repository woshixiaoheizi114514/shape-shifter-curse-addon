package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin;

import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;

/**
 * 形态组件同步包超限 —— 自清理 + 兜底防护。
 *
 * <p><b>背景</b>：原版 {@link PlayerFormComponent#sync()} → {@code COMPONENT.sync(player)} →
 * {@code ComponentProvider.toComponentPacket()} 会把单个 CCA 组件 NBT 序列化成网络包。
 * 当某存档玩家运行时组件数据超过 MC 硬上限 {@code 1048576} 字节时，{@code PacketByteBuf}
 * 构造抛 {@link IllegalArgumentException}，在重生 {@code COPY_FROM} 路径被原版吞掉，
 * 导致形态未同步、重生按钮无反应、卡死亡屏幕。</p>
 *
 * <p><b>三层防御</b>（HEAD 接管原 sync 方法体，直接强转 this 访问字段，避免 @Shadow mapping 问题）：
 * <ol>
 *   <li><b>预防性自清理</b>：sync 前检测 {@code instinctEffects}(HashMap) 与 {@code formHistory}(List)
 *       两个唯一可增长集合的大小。正常游玩下前者仅几个、后者 2-3 个；一旦异常膨胀（>阈值），
 *       主动裁剪，把膨胀掐死在序列化之前。{@code instinctEffects} 全清（等效游戏内 clearInstinct），
 *       {@code formHistory} 只保留最后 3 个（保留回退能力）。</li>
 *   <li><b>异常兜底</b>：若膨胀源不在这俩字段（可能是 CCA 内部或 vanilla 实体数据），
 *       catch 住「Payload may not be larger than」异常，跳过本次 sync 放行重生主流程。</li>
 *   <li><b>自动自愈</b>：被跳过的 sync，下一 tick 的正常 sync（本能 tick / 形态切换触发）会自动补上。</li>
 * </ol></p>
 *
 * <p><b>正常游玩零影响</b>：阈值（instinctEffects 64 / formHistory 16）远高于正常值，
 * 正常情况下自清理分支永不触发，sync 正常执行。</p>
 *
 * <p><b>诊断</b>：自清理与兜底均打 WARN 日志，便于下次复现时定位真正膨胀源。</p>
 */
@Mixin(PlayerFormComponent.class)
public class PlayerFormComponentSyncGuardMixin {

    /** 本能效果集合膨胀阈值；正常游玩仅几个，超过视为异常 */
    private static final int INSTINCT_EFFECTS_THRESHOLD = 64;
    /** 形态历史膨胀阈值；正常每次切换 clear 重建仅 2-3 个，超过视为异常 */
    private static final int FORM_HISTORY_THRESHOLD = 16;
    /** formHistory 裁剪后保留的最大长度（保留回退能力） */
    private static final int FORM_HISTORY_KEEP = 3;

    @Inject(method = "sync", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void sscAddon$guardSyncOverflow(CallbackInfo ci) {
        // 通过强转访问目标字段，规避 @Shadow 对 mod 类的 obf mapping 问题
        PlayerFormComponent self = (PlayerFormComponent) (Object) this;

        // 【1 预防性自清理】在序列化之前裁剪异常膨胀的可增长集合
        HashMap<?, ?> effects = self.instinctEffects;
        if (effects.size() > INSTINCT_EFFECTS_THRESHOLD) {
            ShapeShifterCurseFabric.LOGGER.warn(
                    "[SSCA] instinctEffects 异常膨胀(size={}>{})，sync 前已自清理以避免同步包超限",
                    effects.size(), INSTINCT_EFFECTS_THRESHOLD);
            effects.clear();
        }
        List<?> history = self.formHistory;
        if (history.size() > FORM_HISTORY_THRESHOLD) {
            ShapeShifterCurseFabric.LOGGER.warn(
                    "[SSCA] formHistory 异常膨胀(size={}>{})，sync 前已裁剪至 {}",
                    history.size(), FORM_HISTORY_THRESHOLD, FORM_HISTORY_KEEP);
            while (history.size() > FORM_HISTORY_KEEP) {
                history.remove(0);
            }
        }

        // 【2 兜底】手动执行原 sync 逻辑并 try-catch 包超限，放行重生
        try {
            PlayerFormComponent.COMPONENT.sync(self.player);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("Payload may not be larger than")) {
                ShapeShifterCurseFabric.LOGGER.warn(
                        "[SSCA] 形态组件同步包仍超限（膨胀源非 instinctEffects/formHistory），已跳过本次 sync 放行重生；"
                                + "下一 tick 正常 sync 会自动补上。异常: {}", e.getMessage());
            } else {
                throw e;
            }
        }

        // 取消原 sync 方法体（已由上方手动完成同步）
        ci.cancel();
    }
}


