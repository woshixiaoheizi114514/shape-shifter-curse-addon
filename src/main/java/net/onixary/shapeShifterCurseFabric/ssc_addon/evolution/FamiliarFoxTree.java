package net.onixary.shapeShifterCurseFabric.ssc_addon.evolution;

import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 使魔（familiar_fox）进化树的硬编码节点表 + 布局（试点路线）。
 *
 * <p>对应 SSCA 进化方案导图：初始形态 → Mana 系统 → 火花/火箭/buff免疫 → 炼药/灵视 → 火环 → 灵界之主/契灵分支。</p>
 * <p><b>前置语义</b>：{@code prereqs} 中【任一】已解锁即满足（OR）。后续如需 AND 可在 EvolutionManager 调整。</p>
 */
public final class FamiliarFoxTree {
    public static final String ROUTE_ID = "familiar_fox";

    public static final String NODE_BASE = "familiar_fox_base";
    public static final String NODE_MANA = "mana_system";
    public static final String NODE_SPARK = "spark";
    public static final String NODE_ROCKET = "rocket";
    public static final String NODE_BUFF_IMMUNITY = "buff_immunity";
    public static final String NODE_ALCHEMY = "alchemy";
    public static final String NODE_SPIRIT_VISION = "spirit_vision";
    public static final String NODE_FIRE_RING = "fire_ring";
    public static final String NODE_BRANCH_SPIRIT_LORD = "branch_spirit_lord";
    public static final String NODE_BRANCH_MANCIANIMA = "branch_mancianima";

    /** 进化树全部节点（保持声明顺序）。 */
    public static final List<EvolutionNode> NODES = new ArrayList<>();
    private static final Map<String, EvolutionNode> BY_ID = new LinkedHashMap<>();

    private static void add(EvolutionNode n) {
        NODES.add(n);
        BY_ID.put(n.id, n);
    }

    private static String name(String id) {
        return "evolution.my_addon.familiar_fox.node." + id + ".name";
    }

    private static String desc(String id) {
        return "evolution.my_addon.familiar_fox.node." + id + ".desc";
    }

    static {
        // col0：初始形态（自动解锁，0 点）
        add(new EvolutionNode(NODE_BASE, name(NODE_BASE), desc(NODE_BASE), Items.FOX_SPAWN_EGG,
                0, List.of(), 0, 1, true, ""));
        // col1：Mana 系统
        add(new EvolutionNode(NODE_MANA, name(NODE_MANA), desc(NODE_MANA), Items.EXPERIENCE_BOTTLE,
                1, List.of(NODE_BASE), 1, 1, false, ""));
        // col2：火花 / 火箭 / buff 免疫
        add(new EvolutionNode(NODE_SPARK, name(NODE_SPARK), desc(NODE_SPARK), Items.FIRE_CHARGE,
                1, List.of(NODE_MANA), 2, 0, false, ""));
        add(new EvolutionNode(NODE_ROCKET, name(NODE_ROCKET), desc(NODE_ROCKET), Items.ARROW,
                1, List.of(NODE_MANA), 2, 1, false, ""));
        add(new EvolutionNode(NODE_BUFF_IMMUNITY, name(NODE_BUFF_IMMUNITY), desc(NODE_BUFF_IMMUNITY), Items.MILK_BUCKET,
                1, List.of(NODE_MANA), 2, 2, false, ""));
        // col3：炼药 / 灵视
        add(new EvolutionNode(NODE_ALCHEMY, name(NODE_ALCHEMY), desc(NODE_ALCHEMY), Items.BREWING_STAND,
                1, List.of(NODE_SPARK, NODE_ROCKET), 3, 0, false, ""));
        add(new EvolutionNode(NODE_SPIRIT_VISION, name(NODE_SPIRIT_VISION), desc(NODE_SPIRIT_VISION), Items.SPIDER_EYE,
                1, List.of(NODE_BUFF_IMMUNITY), 3, 2, false, ""));
        // col4：火环
        add(new EvolutionNode(NODE_FIRE_RING, name(NODE_FIRE_RING), desc(NODE_FIRE_RING), Items.BLAZE_POWDER,
                1, List.of(NODE_ALCHEMY, NODE_SPIRIT_VISION), 4, 1, false, ""));
        // col5：两个 SP 分支（50 级自动解锁，0 点）
        add(new EvolutionNode(NODE_BRANCH_SPIRIT_LORD, name(NODE_BRANCH_SPIRIT_LORD), desc(NODE_BRANCH_SPIRIT_LORD),
                Items.SOUL_LANTERN, 0, List.of(NODE_FIRE_RING), 5, 0, true, "spirit_lord"));
        add(new EvolutionNode(NODE_BRANCH_MANCIANIMA, name(NODE_BRANCH_MANCIANIMA), desc(NODE_BRANCH_MANCIANIMA),
                Items.NETHER_STAR, 0, List.of(NODE_FIRE_RING), 5, 2, true, "mancianima"));
    }

    private FamiliarFoxTree() {
    }

    public static EvolutionNode get(String id) {
        return BY_ID.get(id);
    }

    public static boolean isValidNode(String id) {
        return BY_ID.containsKey(id);
    }
}
