package net.onixary.shapeShifterCurseFabric.ssc_addon.evolution;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.data.StaticParams;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.TransformManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;

/**
 * SSCA 进化加点系统 - 服务端业务逻辑入口（框架骨架）。
 *
 * 网络包接收器与指令统一调用本类，避免业务逻辑散落。
 * 「待后续设计」标注处为技能解锁规则 / EXP 消耗 / 前置校验等，留待业务设计阶段填充。
 */
public final class EvolutionManager {
    private EvolutionManager() {
    }

    public static EvolutionComponent get(ServerPlayerEntity player) {
        return RegEvolutionComponent.EVOLUTION.get(player);
    }

    public static void sync(ServerPlayerEntity player) {
        RegEvolutionComponent.EVOLUTION.sync(player);
    }

    /** 经验等级里程碑：到达这些等级各发放 1 点（与导图设定一致）。 */
    private static final int[] LEVEL_MILESTONES = {5, 10, 15, 20, 30, 40, 45};

    /** 选择进化路线；选中使魔路线时自动解锁初始形态节点，并从使魔形态进化进入“进化使魔”。 */
    public static void selectRoute(ServerPlayerEntity player, String routeId) {
        EvolutionComponent comp = get(player);
        comp.setRoute(routeId);
        if (FamiliarFoxTree.ROUTE_ID.equals(routeId)) {
            comp.unlock(FamiliarFoxTree.NODE_BASE);
            transformToUpgradeForm(player);
        }
        sync(player);
    }

    /** 若玩家当前为使魔系列形态（FormID path 含 familiar_fox）且非进化使魔本身，立即变身为“进化使魔”。 */
    private static void transformToUpgradeForm(ServerPlayerEntity player) {
        IForm currentForm = RegPlayerFormComponent.PLAYER_FORM.get(player).nowForm;
        Identifier formId = (currentForm == null) ? null : currentForm.getFormID();
        Identifier upgradeId = FormIdentifiers.UPGRADE_FAMILIAR_FOX;
        if (formId == null || !formId.getPath().contains("familiar_fox") || formId.equals(upgradeId)) {
            return;
        }
        IForm upgradeForm = RegPlayerForms.getPlayerForm(upgradeId);
        if (upgradeForm != null) {
            TransformManager.immediatelyTransform(player, upgradeForm);
        }
    }

    /** 当前唯一可在「开局选形态」界面进入的 SSCA 进化形态，与所属进化路线绑定。 */
    private static boolean isStartFormAllowed(Identifier formId) {
        return FormIdentifiers.UPGRADE_FAMILIAR_FOX.equals(formId);
    }

    /**
     * 游戏开局直接走 SSCA 进化路线：玩家在 StartBook 界面选定一个 SSCA 形态后调用。
     *
     * <p>仅允许尚未启用 mod（{@code ORIGINAL_BEFORE_ENABLE}）的玩家进入，与本体「翻开幻形者之书」对称。
     * 流程：设置进化路线并解锁初始节点 → 触发启用 mod 语义 → 带黑屏淡入淡出动画变身到目标形态，
     * 动画期间定身（STUN），完成时播放升级音效。</p>
     *
     * @param formIdStr 目标 SSCA 形态 ID 字符串（当前仅支持「进化使魔」）
     */
    public static void startSscaRoute(ServerPlayerEntity player, String formIdStr) {
        if (!RegPlayerForms.ORIGINAL_BEFORE_ENABLE.isPlayerForm(player)) {
            return;
        }
        Identifier formId = Identifier.tryParse(formIdStr);
        if (formId == null || !isStartFormAllowed(formId)) {
            return;
        }
        IForm targetForm = RegPlayerForms.getPlayerForm(formId);
        if (targetForm == null) {
            return;
        }
        // 设置进化路线并解锁初始节点（独立于变身动画）
        EvolutionComponent comp = get(player);
        comp.setRoute(FamiliarFoxTree.ROUTE_ID);
        comp.unlock(FamiliarFoxTree.NODE_BASE);
        sync(player);
        // 启用 mod 语义（成就 / 状态），与本体「翻开幻形者之书」一致
        ShapeShifterCurseFabric.ON_ENABLE_MOD.trigger(player);
        // 进化演出：黑屏淡入淡出动画期间定身，完成时升级音效
        int fxDuration = StaticParams.TRANSFORM_FX_DURATION_IN + StaticParams.TRANSFORM_FX_DURATION_OUT;
        player.addStatusEffect(new StatusEffectInstance(SscAddon.STUN, fxDuration, 0, false, false, false));
        TransformManager.startTransform(player, targetForm, data ->
                player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0F, 1.0F));
    }

    /** 选择 SP 分支。框架阶段不校验分支前置条件，待业务设计。 */
    public static void selectBranch(ServerPlayerEntity player, String branchId) {
        get(player).setBranch(branchId);
        sync(player);
    }

    /**
     * 请求解锁一个天赋节点：校验节点合法、未解锁、非自动节点、前置满足（OR）、点数足够，
     * 通过则扣点并解锁。
     */
    public static boolean tryUnlock(ServerPlayerEntity player, String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) {
            return false;
        }
        EvolutionComponent comp = get(player);
        if (!comp.isOnSscaRoute()) {
            return false;
        }
        EvolutionNode node = FamiliarFoxTree.get(nodeId);
        if (node == null || node.autoUnlock || comp.isUnlocked(nodeId)) {
            return false;
        }
        if (!prereqsMet(comp, node)) {
            return false;
        }
        if (!comp.spendPoints(node.cost)) {
            return false;
        }
        comp.unlock(nodeId);
        sync(player);
        return true;
    }

    /** 前置语义：节点无前置，或前置中【任一】已解锁（OR）。 */
    private static boolean prereqsMet(EvolutionComponent comp, EvolutionNode node) {
        if (node.prereqs.isEmpty()) {
            return true;
        }
        for (String p : node.prereqs) {
            if (comp.isUnlocked(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 服务端每 tick 调用：按经验等级里程碑发放点数，并在 50 级自动解锁满足前置的分支节点。
     * 仅对已走 SSCA 路线的玩家生效。
     */
    public static void tickPlayer(ServerPlayerEntity player) {
        EvolutionComponent comp = get(player);
        if (!comp.isOnSscaRoute()) {
            return;
        }
        // 变形重置：曾变身进入进化使魔后，若离开该形态（变成任何其它形态），重置全部进化进度。
        // 用 started 标志避免变身动画期间（route 已设但尚未变成进化使魔）误重置。
        IForm nowForm = RegPlayerFormComponent.PLAYER_FORM.get(player).nowForm;
        Identifier nowFormId = (nowForm == null) ? null : nowForm.getFormID();
        boolean isUpgradeForm = FormIdentifiers.UPGRADE_FAMILIAR_FOX.equals(nowFormId);
        if (isUpgradeForm) {
            if (!comp.hasStarted()) {
                comp.markStarted();
                sync(player);
            }
        } else if (comp.hasStarted()) {
            comp.reset();
            sync(player);
            return;
        }
        int level = player.experienceLevel;
        boolean changed = false;
        for (int milestone : LEVEL_MILESTONES) {
            if (level >= milestone && !comp.hasGrantedLevel(milestone)) {
                comp.markGrantedLevel(milestone);
                comp.addPoints(1);
                changed = true;
            }
        }
        if (level >= 50) {
            for (EvolutionNode node : FamiliarFoxTree.NODES) {
                if (node.autoUnlock && !node.branch.isEmpty()
                        && !comp.isUnlocked(node.id) && prereqsMet(comp, node)) {
                    comp.unlock(node.id);
                    changed = true;
                }
            }
        }
        if (changed) {
            sync(player);
        }
    }

    /** 管理指令：把目标玩家进化路线设为全解锁。 */
    public static void unlockAll(ServerPlayerEntity player) {
        get(player).setUnlockAll(true);
        sync(player);
    }

    /** 管理指令：重置目标玩家全部进化数据。 */
    public static void reset(ServerPlayerEntity player) {
        get(player).reset();
        sync(player);
    }
}
