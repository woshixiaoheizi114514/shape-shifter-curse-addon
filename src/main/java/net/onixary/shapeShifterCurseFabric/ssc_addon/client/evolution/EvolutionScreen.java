package net.onixary.shapeShifterCurseFabric.ssc_addon.client.evolution;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.EvolutionNode;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.FamiliarFoxTree;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.RegEvolutionComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;

import java.util.ArrayList;
import java.util.List;

/**
 * SSCA 进化加点界面：使魔（familiar_fox）进化树。
 * 入口：幻形者之书界面内的「进化加点」按钮（见 EvolutionBookHook）。
 *
 * 节点用物品图标表示，按 {@link FamiliarFoxTree} 的 col/row 布局，连线表示前置关系。
 * 悬停看详情，点击可解锁节点（消耗点数）；未选路线时点初始形态开始进化。
 */
public class EvolutionScreen extends Screen {
    private static final int COL_SPACING = 94;
    private static final int ROW_SPACING = 66;
    private static final int NODE = 24;

    private final Screen parent;

    public EvolutionScreen(Screen parent) {
        super(Text.translatable("evolution.my_addon.screen.title"));
        this.parent = parent;
    }

    private EvolutionComponent getComp() {
        PlayerEntity player = MinecraftClient.getInstance().player;
        return player == null ? null : RegEvolutionComponent.EVOLUTION.get(player);
    }

    @Override
    protected void init() {
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.back"),
                b -> this.close()
        ).dimensions(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    private int originX() {
        return (this.width - 5 * COL_SPACING) / 2;
    }

    private int originY() {
        return (this.height - 2 * ROW_SPACING) / 2;
    }

    private int nodeCx(EvolutionNode n) {
        return originX() + n.col * COL_SPACING;
    }

    private int nodeCy(EvolutionNode n) {
        return originY() + n.row * ROW_SPACING;
    }

    private boolean prereqsMet(EvolutionComponent comp, EvolutionNode node) {
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

    private boolean canUnlock(EvolutionComponent comp, EvolutionNode node) {
        return comp.isOnSscaRoute() && !node.autoUnlock && !comp.isUnlocked(node.id)
                && prereqsMet(comp, node) && comp.getPoints() >= node.cost;
    }

    private boolean isInNode(int mx, int my, int cx, int cy) {
        int half = NODE / 2;
        return mx >= cx - half && mx <= cx + half && my >= cy - half && my <= cy + half;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);
        EvolutionComponent comp = getComp();

        // 连线（前置 → 节点）
        for (EvolutionNode node : FamiliarFoxTree.NODES) {
            for (String pid : node.prereqs) {
                EvolutionNode pre = FamiliarFoxTree.get(pid);
                if (pre == null) {
                    continue;
                }
                boolean active = comp != null && comp.isUnlocked(pid);
                drawConnection(ctx, nodeCx(pre), nodeCy(pre), nodeCx(node), nodeCy(node),
                        active ? 0xFFFFC85A : 0xFF3A3A3A);
            }
        }

        // 节点
        EvolutionNode hovered = null;
        for (EvolutionNode node : FamiliarFoxTree.NODES) {
            int cx = nodeCx(node), cy = nodeCy(node);
            boolean unlocked = comp != null && comp.isUnlocked(node.id);
            boolean canUn = comp != null && canUnlock(comp, node);
            drawNode(ctx, node, cx, cy, unlocked, canUn);
            if (isInNode(mouseX, mouseY, cx, cy)) {
                hovered = node;
            }
        }

        super.render(ctx, mouseX, mouseY, delta);

        // 顶部标题 + 点数 / 状态
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFF);
        Text info;
        if (comp == null) {
            info = Text.literal("-");
        } else if (comp.isOnSscaRoute()) {
            info = Text.translatable("evolution.my_addon.screen.points", comp.getPoints());
        } else {
            info = Text.translatable("evolution.my_addon.screen.no_route");
        }
        ctx.drawCenteredTextWithShadow(this.textRenderer, info, this.width / 2, 26, 0xFFD86A);

        // tooltip
        if (hovered != null && comp != null) {
            drawNodeTooltip(ctx, hovered, mouseX, mouseY, comp);
        }
    }

    private void drawConnection(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        int midX = (x1 + x2) / 2;
        fillLineH(ctx, x1, midX, y1, color);
        fillLineV(ctx, midX, y1, y2, color);
        fillLineH(ctx, midX, x2, y2, color);
    }

    private void fillLineH(DrawContext ctx, int xa, int xb, int y, int color) {
        ctx.fill(Math.min(xa, xb), y - 1, Math.max(xa, xb), y + 1, color);
    }

    private void fillLineV(DrawContext ctx, int x, int ya, int yb, int color) {
        ctx.fill(x - 1, Math.min(ya, yb), x + 1, Math.max(ya, yb), color);
    }

    private void drawNode(DrawContext ctx, EvolutionNode node, int cx, int cy, boolean unlocked, boolean canUn) {
        int half = NODE / 2;
        int left = cx - half, top = cy - half;
        int border = unlocked ? 0xFFFFD700 : (canUn ? 0xFF5BE65B : 0xFF555555);
        int bg = unlocked ? 0xCC3A2E10 : (canUn ? 0xCC10240F : 0xCC161616);
        ctx.fill(left, top, left + NODE, top + NODE, bg);
        ctx.fill(left, top, left + NODE, top + 1, border);
        ctx.fill(left, top + NODE - 1, left + NODE, top + NODE, border);
        ctx.fill(left, top, left + 1, top + NODE, border);
        ctx.fill(left + NODE - 1, top, left + NODE, top + NODE, border);
        ctx.drawItem(new ItemStack(node.icon), cx - 8, cy - 8);
        if (!unlocked && !canUn) {
            ctx.fill(left + 1, top + 1, left + NODE - 1, top + NODE - 1, 0x99000000);
        }
        Text name = Text.translatable(node.nameKey);
        int nameW = this.textRenderer.getWidth(name);
        ctx.drawTextWithShadow(this.textRenderer, name, cx - nameW / 2, top + NODE + 2,
                unlocked ? 0xFFFFD700 : (canUn ? 0xFF99FF99 : 0xFF888888));
    }

    private void drawNodeTooltip(DrawContext ctx, EvolutionNode node, int mouseX, int mouseY, EvolutionComponent comp) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable(node.nameKey).formatted(Formatting.GOLD));
        lines.add(Text.translatable(node.descKey).formatted(Formatting.GRAY));
        if (comp.isUnlocked(node.id)) {
            lines.add(Text.translatable("evolution.my_addon.screen.state.unlocked").formatted(Formatting.GREEN));
        } else if (node.autoUnlock) {
            lines.add(Text.translatable("evolution.my_addon.screen.state.auto").formatted(Formatting.AQUA));
        } else {
            lines.add(Text.translatable("evolution.my_addon.screen.cost", node.cost).formatted(Formatting.YELLOW));
            if (!comp.isOnSscaRoute()) {
                lines.add(Text.translatable("evolution.my_addon.screen.state.need_route").formatted(Formatting.RED));
            } else if (!prereqsMet(comp, node)) {
                lines.add(Text.translatable("evolution.my_addon.screen.state.locked").formatted(Formatting.RED));
            } else if (comp.getPoints() < node.cost) {
                lines.add(Text.translatable("evolution.my_addon.screen.state.no_points").formatted(Formatting.RED));
            } else {
                lines.add(Text.translatable("evolution.my_addon.screen.state.click_unlock").formatted(Formatting.GREEN));
            }
        }
        ctx.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            EvolutionComponent comp = getComp();
            if (comp != null) {
                for (EvolutionNode node : FamiliarFoxTree.NODES) {
                    if (!isInNode((int) mouseX, (int) mouseY, nodeCx(node), nodeCy(node))) {
                        continue;
                    }
                    if (!comp.isOnSscaRoute() && FamiliarFoxTree.NODE_BASE.equals(node.id)) {
                        sendString(SscAddonNetworking.PACKET_EVO_SELECT_ROUTE, FamiliarFoxTree.ROUTE_ID);
                        return true;
                    }
                    if (canUnlock(comp, node)) {
                        sendString(SscAddonNetworking.PACKET_EVO_UNLOCK, node.id);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static void sendString(Identifier packet, String value) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(value);
        ClientPlayNetworking.send(packet, buf);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
