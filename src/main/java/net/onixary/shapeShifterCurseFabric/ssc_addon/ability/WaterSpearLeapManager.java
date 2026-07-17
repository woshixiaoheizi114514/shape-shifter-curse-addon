package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.entity.ThrownWaterSpearEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.AxolotlTree;
import net.onixary.shapeShifterCurseFabric.ssc_addon.evolution.RegEvolutionComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进化美西螈「投掷水矛」主技能（sp_primary）—— 服务端状态机。
 *
 * <p>按主技能键：把主手物品临时换成真水矛（{@code held} 谓词 → 稳定 3D 水矛模型），
 * 并同步「蓄力中」状态给客户端 → 举矛过肩姿势由 {@code getArmPose}/第一人称矩阵 mixin 持久渲染
 * （不依赖 vanilla「正在使用物品」状态，故不会闪退）。同时冲量斜后上跃并<b>无重力悬浮</b>；
 * 蓄力 1.35 秒后朝准星投出直线水矛并<b>归还原物品</b>。直击 12 物理 + 2 格半径 5 点 AOE，
 * 耗 6% 湿润度（18 air），CD 8 秒。</p>
 *
 * <p>物品安全：投出 / 取消 / 死亡 / 形态丢失 / 断线 都会归还原物品并恢复重力。</p>
 */
public final class WaterSpearLeapManager {

	private static final int CHARGE_TICKS = 27;    // 蓄力 ~1.35 秒后投矛
	private static final int CD_TICKS = 160;       // 8 秒
	private static final int AIR_COST = 18;        // 6% 湿润度
	private static final double LEAP_BACK = 0.80;  // 起跃向后冲量（更斜后）
	private static final double LEAP_UP = 0.62;    // 起跃向上冲量（跳更高）
	private static final double DECAY = 0.80;       // 每 tick 速度衰减（缓入缓出 + 收束到悬浮）

	private static final Map<UUID, LeapState> STATES = new ConcurrentHashMap<>();

	private static final class LeapState {
		int tick = 0;
		int savedSlot = -1;              // 被替换的热键栏槽位
		ItemStack savedStack = ItemStack.EMPTY; // 被替换前的原物品
	}

	private WaterSpearLeapManager() {
	}

	/** 客户端按主技能键（无 payload）。服务端校验、换水矛并起跃悬浮。 */
	public static void onKeyPress(ServerPlayerEntity player) {
		if (STATES.containsKey(player.getUuid())) return; // 施法中不可重入
		if (!FormUtils.isUpgradeAxolotl(player)) return;
		if (!RegEvolutionComponent.EVOLUTION.get(player).isUnlocked(AxolotlTree.NODE_WATER_SPEAR)) return;
		if (PowerUtils.getResourceValue(player, FormIdentifiers.SP_PRIMARY_CD) > 0) return; // CD 中
		if (player.getAir() < AIR_COST) return; // 湿润度不足

		player.setAir(player.getAir() - AIR_COST);

		LeapState s = new LeapState();
		// 临时把主手物品换成真水矛，并打 CustomModelData=1 → 走 custom_model_data override
		// 恒定渲染 3D 水矛（不依赖 held 引用相等 / vanilla「使用中」状态，故不会退 2D、不闪）
		s.savedSlot = player.getInventory().selectedSlot;
		s.savedStack = player.getInventory().getStack(s.savedSlot).copy();
		ItemStack spear = new ItemStack(SscAddon.WATER_SPEAR);
		spear.getOrCreateNbt().putInt("CustomModelData", 1);
		player.getInventory().setStack(s.savedSlot, spear);
		STATES.put(player.getUuid(), s);

		// 同步「蓄力中」→ 客户端渲染举矛过肩姿势（getArmPose / 第一人称矩阵 mixin）
		SscAddonNetworking.syncSpearChargeState(player, true);

		// 无重力 + 一次性冲量斜后上跃（视线反方向 + 上），随后衰减到空中悬浮
		player.setNoGravity(true);
		Vec3d look = player.getRotationVector();
		Vec3d back = new Vec3d(-look.x, 0, -look.z);
		if (back.lengthSquared() < 1.0e-4) back = new Vec3d(0, 0, -1);
		back = back.normalize();
		player.setVelocity(back.x * LEAP_BACK, LEAP_UP, back.z * LEAP_BACK);
		player.velocityModified = true;
		player.fallDistance = 0.0f;

		ServerWorld sw = (ServerWorld) player.getWorld();
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.PLAYERS, 1.0f, 1.1f);
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_AXOLOTL_SPLASH, SoundCategory.PLAYERS, 1.2f, 0.8f);
		sw.spawnParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.2, player.getZ(),
				30, 0.4, 0.2, 0.4, 0.3);
	}

	/** 每服务端 tick 对每个在线玩家调用。 */
	public static void tick(ServerPlayerEntity player) {
		LeapState s = STATES.get(player.getUuid());
		if (s == null) return;
		if (player.isDead() || !FormUtils.isUpgradeAxolotl(player)) {
			cancel(player); // 死亡 / 形态丢失 → 取消并归还物品、恢复重力
			return;
		}
		s.tick++;
		ServerWorld sw = (ServerWorld) player.getWorld();

		if (s.tick < CHARGE_TICKS) {
			// 无重力下速度衰减：起跃冲量平滑收束到 0 → 跃起后悬浮在空中（不落）
			Vec3d v = player.getVelocity();
			player.setVelocity(v.x * DECAY, v.y * DECAY, v.z * DECAY);
			player.velocityModified = true;
			player.fallDistance = 0.0f;
			if (s.tick % 4 == 0) {
				sw.spawnParticles(ParticleTypes.FALLING_WATER, player.getX(), player.getY() + 1.0, player.getZ(),
						6, 0.4, 0.4, 0.4, 0.0);
			}
		} else {
			// 投矛：归还物品、恢复重力、朝准星发射直线水矛
			throwSpear(player, s);
			finish(player);
		}
	}

	/** 朝准星方向投出直线水矛，归还原物品。 */
	private static void throwSpear(ServerPlayerEntity player, LeapState s) {
		restoreItem(player, s);         // 归还原主手物品
		ServerWorld sw = (ServerWorld) player.getWorld();
		Vec3d dir = player.getRotationVector().normalize();
		ThrownWaterSpearEntity spear = new ThrownWaterSpearEntity(sw, player);
		spear.setPosition(player.getX() + dir.x * 0.6, player.getEyeY() - 0.1 + dir.y * 0.6, player.getZ() + dir.z * 0.6);
		spear.setDirection(dir);
		sw.spawnEntity(spear);
		player.swingHand(Hand.MAIN_HAND, true); // 投掷挥手动作
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 1.2f, 1.1f);
		sw.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_PLAYER_SPLASH, SoundCategory.PLAYERS, 1.0f, 0.9f);
	}

	/** 归还原物品：仅当被替换的槽位仍是水矛（未被玩家操作）时还原，避免覆盖玩家后来放入的东西。 */
	private static void restoreItem(ServerPlayerEntity player, LeapState s) {
		if (s.savedSlot >= 0 && player.getInventory().getStack(s.savedSlot).isOf(SscAddon.WATER_SPEAR)) {
			player.getInventory().setStack(s.savedSlot, s.savedStack);
		}
	}

	/** 投矛完成：恢复重力、结束蓄力渲染、进入 CD 并清理状态。 */
	private static void finish(ServerPlayerEntity player) {
		STATES.remove(player.getUuid());
		player.setNoGravity(false);
		SscAddonNetworking.syncSpearChargeState(player, false);
		PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_PRIMARY_CD, CD_TICKS);
	}

	/** 取消（不进 CD、不投矛）：归还物品、恢复重力、结束蓄力渲染。 */
	public static void cancel(ServerPlayerEntity player) {
		LeapState s = STATES.remove(player.getUuid());
		if (s != null) {
			restoreItem(player, s);
			player.setNoGravity(false);
			SscAddonNetworking.syncSpearChargeState(player, false);
		}
	}

	/** 该玩家（按 UUID）是否处于投掷水矛蓄力中（服务端权威，用于蓄力期禁用右键交互）。 */
	public static boolean isCharging(java.util.UUID id) {
		return STATES.containsKey(id);
	}

	/** 断线：归还原物品、恢复重力并清理，避免水矛残留 / 原物品丢失。 */
	public static void onPlayerDisconnect(ServerPlayerEntity player) {
		LeapState s = STATES.remove(player.getUuid());
		if (s != null) {
			restoreItem(player, s);
			player.setNoGravity(false);
		}
	}
}
