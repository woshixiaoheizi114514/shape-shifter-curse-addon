package net.onixary.shapeShifterCurseFabric.ssc_addon.evolution;

import net.minecraft.item.Item;

import java.util.List;

/**
 * SSCA 进化树的单个节点（能力）定义。纯数据，服务端与客户端共用。
 *
 * <p>图标用 vanilla/mod 物品（客户端渲染时 {@code new ItemStack(icon)}），零美术成本。</p>
 * <p>节点 {@link #id} 即解锁状态 / 能力门控（{@code ssc_addon:has_talent}）所用的 talent id。</p>
 */
public class EvolutionNode {
    /** 节点唯一 id（= talent id）。 */
    public final String id;
    /** 名称 lang key。 */
    public final String nameKey;
    /** 描述 lang key（tooltip 正文）。 */
    public final String descKey;
    /** 图标物品。 */
    public final Item icon;
    /** 解锁消耗点数。 */
    public final int cost;
    /** 前置节点 id 列表（解锁语义见 {@link FamiliarFoxTree}）。 */
    public final List<String> prereqs;
    /** 布局列（进化层级，越大越靠后）。 */
    public final int col;
    /** 布局行（同层内的纵向位置）。 */
    public final int row;
    /** true = 满足前置即自动解锁、不消耗点数（如初始形态、50 级分支）。 */
    public final boolean autoUnlock;
    /** 所属分支标记（""=主线）。 */
    public final String branch;

    public EvolutionNode(String id, String nameKey, String descKey, Item icon, int cost,
                         List<String> prereqs, int col, int row, boolean autoUnlock, String branch) {
        this.id = id;
        this.nameKey = nameKey;
        this.descKey = descKey;
        this.icon = icon;
        this.cost = cost;
        this.prereqs = prereqs == null ? List.of() : prereqs;
        this.col = col;
        this.row = row;
        this.autoUnlock = autoUnlock;
        this.branch = branch == null ? "" : branch;
    }
}
