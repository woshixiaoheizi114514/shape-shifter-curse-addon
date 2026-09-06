/*
 * Copyright (c) 2026 MangZai-120
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).
 */
package net.jackcooper.shapeShifterCurseAddon.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Pair;
import net.onixary.shapeShifterCurseFabric.util.Accessory.AccessoryUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 饰品检测工具：判断实体是否正戴着某饰品。
 * <p>框架无关——内部走原版 SSC 的 {@link AccessoryUtils} 抽象层（{@code "auto"} 自动适配
 * 当前活动的饰品框架：Curios 优先级 2000 &gt; Trinkets 1000），因此在纯 Curios 或纯 Trinkets
 * 环境下均可正确工作。
 * <p>异常安全——抽象层未就绪（{@code nowAccessoryMod == null}，即两个框架都没装）或查询异常时
 * 一律返回 false，可在服务端 tick 里放心调用。
 */
public final class TrinketUtils {
    /** 诊断日志：Curios 反射兜底各阶段失败原因（定位 Kilt 类加载器/能力注入问题用）。 */
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("ssc-addon");

    private TrinketUtils() {
    }

    /**
     * 该实体是否正装备着指定饰品物品。
     * 遍历当前活动饰品框架下的所有槽位，查找匹配的物品栈。
     */
    public static boolean isWearing(LivingEntity entity, Item item) {
        return isWearing(entity, stack -> stack.getItem() == item);
    }

    /**
     * 饰品佩戴检测（带 Curios 反射兜底）：
     * ① 先走 {@link #isWearing}（SSC 抽象层，"auto" 自动适配 Trinkets/Accessories/Curios）；
     * ② 抽象层未命中时再反射直查 Curios 自有 API（覆盖 Kilt/Connector 转载环境下
     * SSC 主包原生 AccessoryIO 未注册的情况）。
     * 方法名均为 Curios 自有 API 名（getCuriosInventory/resolve/isEquipped），不受 MC 映射重映射影响；
     * 类未加载（未装 Curios）或任何异常一律返回 false，安全兜底。
     */
    public static boolean isWearingAuto(LivingEntity entity, Item item) {
        if (isWearing(entity, item)) {
            return true;
        }
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Object lazyOptional = api.getMethod("getCuriosInventory", LivingEntity.class).invoke(null, entity);
            if (lazyOptional == null) return false;
            Object resolved = lazyOptional.getClass().getMethod("resolve").invoke(lazyOptional);
            if (!(resolved instanceof java.util.Optional<?> opt) || opt.isEmpty()) return false;
            Object handler = opt.get();
            Object equipped = handler.getClass().getMethod("isEquipped", Item.class).invoke(handler, item);
            return Boolean.TRUE.equals(equipped);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 该实体是否正装备着满足指定谓词的饰品。
     * 用于需要按物品类型（{@code instanceof}）而非具体实例匹配的场合。
     */
    public static boolean isWearing(LivingEntity entity, Predicate<ItemStack> predicate) {
        return findFirstEquipped(entity, predicate) != null;
    }

    /**
     * 查找该实体身上第一件满足谓词的已装备饰品栈；不存在则返回 {@code null}。
     * 用于需要进一步操作饰品栈（如消耗、读 NBT）的场合。框架无关。
     * <p>带 Curios 反射兜底：SSC 抽象层的 Curios 实现（CurioUtils）在 Kilt 转载环境下
     * 拿不到数据（恒空 Map），此处反射直查 Curios 自有 API 遍历全部槽位（含 cosmetic），
     * 保证纯 Curios 环境下 HUD / 施法等取书功能正常。</p>
     */
    public static @Nullable ItemStack findFirstEquipped(LivingEntity entity, Predicate<ItemStack> predicate) {
        try {
            Map<Pair<@Nullable String, String>, List<ItemStack>> slots =
                    AccessoryUtils.getEntitySlots(entity, "auto");
            if (slots != null && !slots.isEmpty()) {
                for (List<ItemStack> stacks : slots.values()) {
                    if (stacks == null) continue;
                    for (ItemStack stack : stacks) {
                        if (stack != null && !stack.isEmpty() && predicate.test(stack)) {
                            return stack;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        // Curios 反射兜底：遍历 Curios 自有 inventory 的全部槽位（含 cosmetic）。
        // Kilt 转载环境下 Forge Curios 由独立类加载器加载，需多加载器尝试 CuriosApi；
        // 各阶段失败均打 warn 日志（不再静默），便于实机定位断点。
        try {
            Class<?> api = null;
            String lastErr = "no classloader tried";
            for (ClassLoader cl : new ClassLoader[]{
                    Thread.currentThread().getContextClassLoader(),
                    TrinketUtils.class.getClassLoader(),
                    entity.getClass().getClassLoader()}) {
                if (cl == null) continue;
                try {
                    api = Class.forName("top.theillusivec4.curios.api.CuriosApi", true, cl);
                    break;
                } catch (ClassNotFoundException e) {
                    lastErr = e.toString();
                }
            }
            if (api == null) {
                LOG.warn("[SSCA] Curios fallback: CuriosApi 类不可见（{}）——Kilt 类加载器隔离？", lastErr);
                return null;
            }
            Object lazyOptional = api.getMethod("getCuriosInventory", LivingEntity.class).invoke(null, entity);
            if (lazyOptional == null) {
                LOG.warn("[SSCA] Curios fallback: getCuriosInventory 返回 null");
                return null;
            }
            Object resolved = lazyOptional.getClass().getMethod("resolve").invoke(lazyOptional);
            if (!(resolved instanceof java.util.Optional<?> opt) || opt.isEmpty()) {
                LOG.warn("[SSCA] Curios fallback: capability 未解析（{}）", resolved);
                return null;
            }
            Object handler = opt.get(); // ICuriosItemHandler
            Object curiosHandlerMap = handler.getClass().getMethod("getCurios").invoke(handler);
            if (!(curiosHandlerMap instanceof Map<?, ?> curiosMap)) {
                LOG.warn("[SSCA] Curios fallback: getCurios() 非 Map（{}）", curiosHandlerMap);
                return null;
            }
            // Forge 的 stacks 容器不是 Iterable（IDynamicStackHandler extends IItemHandlerModifiable），
            // 正确取法：getSlots() 数量 + getStackInSlot(i) 按索引循环（含 cosmetic）。
            // Kilt 双映射下对象可能非 yarn ItemStack：instanceof 命中直接返回；
            // 类名相同但类加载器不同的极端情况，用注册名等价比对做最后兜底。
            for (Object stacksHandler : curiosMap.values()) {
                if (stacksHandler == null) continue;
                for (String getter : new String[]{"getStacks", "getCosmeticStacks"}) {
                    try {
                        Object stacks = stacksHandler.getClass().getMethod(getter).invoke(stacksHandler);
                        if (stacks == null) continue;
                        int slotCount;
                        try {
                            slotCount = (int) stacks.getClass().getMethod("getSlots").invoke(stacks);
                        } catch (NoSuchMethodException e) {
                            continue; // 非 handler 类型，跳过
                        }
                        for (int i = 0; i < slotCount; i++) {
                            Object o = stacks.getClass().getMethod("getStackInSlot", int.class).invoke(stacks, i);
                            if (o == null) continue;
                            if (o instanceof ItemStack s && !s.isEmpty() && predicate.test(s)) {
                                return s;
                            }
                            // 双映射兜底：类名一致但加载器不同 → 反射取注册名等价判定
                            if (!o.getClass().getName().equals("net.minecraft.item.ItemStack")) continue;
                            try {
                                Object isEmpty = o.getClass().getMethod("isEmpty").invoke(o);
                                if (Boolean.TRUE.equals(isEmpty)) continue;
                                Object item = o.getClass().getMethod("getItem").invoke(o);
                                Object yarnItem = net.minecraft.registry.Registries.ITEM.get(
                                        net.minecraft.util.Identifier.tryParse("ssc_addon:moon_dust_spellbook"));
                                if (item == yarnItem) {
                                    return (ItemStack) o;
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            LOG.warn("[SSCA] Curios fallback: 反射链异常 {}", t.toString());
        }
        return null;
    }
}
