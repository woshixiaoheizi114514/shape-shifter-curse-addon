package net.onixary.shapeShifterCurseFabric.ssc_addon.evolution;

import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.HashSet;
import java.util.Set;

/**
 * SSCA 进化加点系统 - 玩家进化数据组件（服务端权威，自动同步到客户端）。
 *
 * 框架阶段：仅承载最小数据骨架（路线 / 分支 / EXP / 已解锁节点 / 全解锁标记），
 * 具体的天赋节点、解锁规则、EXP 消耗曲线等业务逻辑「待后续设计」，由 JSON 数据驱动填充。
 *
 * 设计依据：见 附属根目录 SSCA进化加点系统_设计文档.txt
 */
public class EvolutionComponent implements AutoSyncedComponent {
    /** 玩家选择的进化路线 id（空字符串 = 未选择 / 走原版 SSC 路线）。 */
    private String route = "";
    /** 已选择的 SP 分支 id（空字符串 = 未选择）。 */
    private String branch = "";
    /** 加点货币快照（框架占位；后续接入原版经验 EXP 消耗时再细化）。 */
    private int exp = 0;
    /** 已解锁的天赋节点 id 集合。 */
    private final Set<String> unlockedNodes = new HashSet<>();
    /** 管理指令强制全解锁标记（/ssc_addon evolution unlock_all）。 */
    private boolean unlockAll = false;
    /** 可用升级点数（攒点用于解锁节点）。 */
    private int points = 0;
    /** 已发放过升级点的经验等级里程碑（防重复发放）。 */
    private final Set<Integer> grantedLevels = new HashSet<>();
    /** 是否已真正变身进入过进化形态（用于「进入后再离开则重置进度」判定，避免变身动画期间误重置）。 */
    private boolean started = false;

    // ---------------- 路线 / 分支 ----------------

    public boolean isOnSscaRoute() {
        return route != null && !route.isEmpty();
    }

    public String getRoute() {
        return route == null ? "" : route;
    }

    public void setRoute(String route) {
        this.route = route == null ? "" : route;
    }

    public String getBranch() {
        return branch == null ? "" : branch;
    }

    public void setBranch(String branch) {
        this.branch = branch == null ? "" : branch;
    }

    // ---------------- EXP ----------------

    public int getExp() {
        return exp;
    }

    public void setExp(int exp) {
        this.exp = Math.max(0, exp);
    }

    public void addExp(int delta) {
        setExp(this.exp + delta);
    }

    // ---------------- 升级点数 ----------------

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = Math.max(0, points);
    }

    public void addPoints(int delta) {
        setPoints(this.points + delta);
    }

    /** 尝试消耗点数，足够则扣除返回 true。 */
    public boolean spendPoints(int cost) {
        if (cost <= 0) return true;
        if (this.points < cost) return false;
        this.points -= cost;
        return true;
    }

    /** 该经验等级里程碑是否已发放过点数。 */
    public boolean hasGrantedLevel(int level) {
        return grantedLevels.contains(level);
    }

    public void markGrantedLevel(int level) {
        grantedLevels.add(level);
    }

    // ---------------- 进化形态驻留标志 ----------------

    /** 玩家是否已真正变身进入过进化形态。 */
    public boolean hasStarted() {
        return started;
    }

    /** 标记玩家已变身进入进化形态。 */
    public void markStarted() {
        this.started = true;
    }

    // ---------------- 解锁状态 ----------------

    public boolean isUnlockAll() {
        return unlockAll;
    }

    public void setUnlockAll(boolean unlockAll) {
        this.unlockAll = unlockAll;
    }

    public Set<String> getUnlockedNodes() {
        return unlockedNodes;
    }

    /** 节点是否已解锁（全解锁标记开启时恒为 true）。 */
    public boolean isUnlocked(String nodeId) {
        return unlockAll || (nodeId != null && unlockedNodes.contains(nodeId));
    }

    public void unlock(String nodeId) {
        if (nodeId != null && !nodeId.isEmpty()) {
            unlockedNodes.add(nodeId);
        }
    }

    /** 重置全部进化数据（/ssc_addon evolution reset）。 */
    public void reset() {
        this.route = "";
        this.branch = "";
        this.exp = 0;
        this.unlockAll = false;
        this.unlockedNodes.clear();
        this.points = 0;
        this.grantedLevels.clear();
        this.started = false;
    }

    // ---------------- 持久化 / 同步 ----------------

    @Override
    public void readFromNbt(NbtCompound nbt) {
        this.route = nbt.getString("route");
        this.branch = nbt.getString("branch");
        this.exp = nbt.getInt("exp");
        this.unlockAll = nbt.getBoolean("unlockAll");
        this.points = nbt.getInt("points");
        this.unlockedNodes.clear();
        NbtList list = nbt.getList("unlocked", NbtElement.STRING_TYPE);
        for (int i = 0; i < list.size(); i++) {
            this.unlockedNodes.add(list.getString(i));
        }
        this.grantedLevels.clear();
        for (int g : nbt.getIntArray("grantedLevels")) {
            this.grantedLevels.add(g);
        }
        this.started = nbt.getBoolean("started");
    }

    @Override
    public void writeToNbt(NbtCompound nbt) {
        nbt.putString("route", getRoute());
        nbt.putString("branch", getBranch());
        nbt.putInt("exp", this.exp);
        nbt.putBoolean("unlockAll", this.unlockAll);
        nbt.putInt("points", this.points);
        NbtList list = new NbtList();
        for (String node : this.unlockedNodes) {
            list.add(NbtString.of(node));
        }
        nbt.put("unlocked", list);
        int[] granted = new int[this.grantedLevels.size()];
        int gi = 0;
        for (int g : this.grantedLevels) {
            granted[gi++] = g;
        }
        nbt.putIntArray("grantedLevels", granted);
        nbt.putBoolean("started", this.started);
    }
}
