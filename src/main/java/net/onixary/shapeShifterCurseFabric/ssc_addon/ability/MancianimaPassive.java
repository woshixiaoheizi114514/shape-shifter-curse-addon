package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 契灵被动行为统一处理器（服务端）。
 * 职责：
 *  - mana 自然恢复 + 周围劫掠生物 bonus（每 3s +min(count,5)）
 *  - 25% 概率村庄袭击事件触发与完成奖励
 *  - 村民/商人遭契灵击杀的物品掉落
 *  - 村民/商人逃跑判定（白名单内的契灵不会吓跑）
 *
 * 注：抗伤恢复 / 战斗状态 由 MancianimaMarkManager 处理；
 *     药水免疫 由 form_familiar_fox_mancianima_no_buff_effect.json 处理。
 */
public final class MancianimaPassive {
	private MancianimaPassive() {}

	private static final Map<UUID, AssaultData> ASSAULTS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_ASSAULT_ROLL = new ConcurrentHashMap<>();
	private static final long ASSAULT_ROLL_COOLDOWN = 24000L; // 1 MC 天内只能袭击 1 次

	// “被君临”还在生效的村民/商人 UUID + 上次刷新 tick
	private static final Map<UUID, Long> FLEE_AFFECTED = new ConcurrentHashMap<>();
	private static final UUID MANCIANIMA_FEAR_SLOW_UUID = UUID.fromString("7f3e2c4a-3b16-4ad2-9d4d-2bf1d8a5d111");
	private static final double FLEE_RADIUS = 12.0;
	private static final int FLEE_LINGER_TICKS = 60; // 离开后 3s 才清除 debuff

	private static final Random RNG = new Random();

	public static void tick(ServerPlayerEntity player) {
		if (!FormUtils.isForm(player, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) return;
		// 驱逃逻辑：每 10t 检一次，低开销
		if (player.age % 10 == 0) tickFlee(player);
		if (player.age % 60 != 0) return;
		// 1. mana 劫掠 bonus（每 3s）
		int raiderCount = countNearbyRaiders(player, 10.0);
		if (raiderCount > 0) {
			ManaUtils.gainPlayerMana(player, Math.min(raiderCount, 5));
		}
		// 2. 过期卸载驱逃修饰符
		cleanupExpiredFlee(player.getWorld().getTime());
	}

	/** 敷鼎袭击触发入口：敷钟后调用。返回是否成功触发。 */
	public static boolean tryTriggerAssaultByBell(ServerPlayerEntity player) {
		if (!FormUtils.isForm(player, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) return false;
		UUID pid = player.getUuid();
		if (ASSAULTS.containsKey(pid)) return false; // 正在进行中
		long now = player.getWorld().getTime();
		Long last = LAST_ASSAULT_ROLL.get(pid);
		if (last != null && now - last < ASSAULT_ROLL_COOLDOWN) {
			player.sendMessage(Text.translatable("ssc_addon.mancianima.assault.cooldown"), true);
			return false;
		}
		Box markBox = player.getBoundingBox().expand(64.0);
		Set<UUID> villagerTargets = new HashSet<>();
		List<MerchantEntity> merchants = player.getWorld().getEntitiesByClass(MerchantEntity.class, markBox,
				e -> e.isAlive() && !e.isRemoved());
		for (MerchantEntity m : merchants) villagerTargets.add(m.getUuid());
		if (villagerTargets.isEmpty()) {
			player.sendMessage(Text.translatable("ssc_addon.mancianima.assault.no_villagers"), true);
			return false;
		}
		// 酱制原版袭击：应用 BAD_OMEN（如果玩家处于村庄中心，MC 会自动启动 raid）
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.BAD_OMEN, 6000, 0, false, false, true));
		// 如鑂劤主：让附近铁傀儡也跟玩家干上
		for (IronGolemEntity g : player.getWorld().getEntitiesByClass(IronGolemEntity.class, markBox,
				e -> e.isAlive() && !e.isRemoved())) {
			g.setTarget(player);
			g.setAttacker(player);
		}
		// 创建 bossbar
		ServerBossBar bar = new ServerBossBar(
				Text.translatable("ssc_addon.mancianima.assault.bossbar", villagerTargets.size()),
				BossBar.Color.RED, BossBar.Style.NOTCHED_10);
		bar.setPercent(1.0f);
		bar.addPlayer(player);
		ASSAULTS.put(pid, new AssaultData(villagerTargets, bar));
		LAST_ASSAULT_ROLL.put(pid, now);
		player.sendMessage(Text.translatable("ssc_addon.mancianima.assault.start", villagerTargets.size()), false);
		if (player.getWorld() instanceof ServerWorld sw) {
			sw.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.EVENT_RAID_HORN.value(), player.getSoundCategory(), 1.0f, 0.9f);
		}
		return true;
	}

	private static void tickFlee(ServerPlayerEntity player) {
		Box box = player.getBoundingBox().expand(FLEE_RADIUS);
		long now = player.getWorld().getTime();
		List<MerchantEntity> merchants = player.getWorld().getEntitiesByClass(MerchantEntity.class, box,
				e -> e.isAlive() && !e.isRemoved()
						&& (e instanceof VillagerEntity || e instanceof WanderingTraderEntity));
		for (MerchantEntity m : merchants) {
			if (WhitelistUtils.isProtected(player, m)) continue;
			// 纯逵梦向量退避
			Vec3d away = m.getPos().subtract(player.getPos());
			if (away.lengthSquared() < 1.0e-4) {
				away = new Vec3d(RNG.nextDouble() - 0.5, 0, RNG.nextDouble() - 0.5);
			}
			away = away.normalize().multiply(8.0);
			Vec3d target = m.getPos().add(away.x, 0, away.z);
			if (m instanceof PathAwareEntity) {
				m.getNavigation().startMovingTo(target.x, m.getY(), target.z, 1.0);
			}
			// -25% 移速（乘法修饰，幂等衡）
			EntityAttributeInstance attr = m.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
			if (attr != null && attr.getModifier(MANCIANIMA_FEAR_SLOW_UUID) == null) {
				attr.addPersistentModifier(new EntityAttributeModifier(MANCIANIMA_FEAR_SLOW_UUID,
						"Mancianima fear slow", -0.25,
						EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
			}
			FLEE_AFFECTED.put(m.getUuid(), now);
		}
	}

	/** 超过 linger 未刷新 → 移除修饰。 */
	private static void cleanupExpiredFlee(long now) {
		Iterator<Map.Entry<UUID, Long>> it = FLEE_AFFECTED.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Long> e = it.next();
			if (now - e.getValue() <= FLEE_LINGER_TICKS) continue;
			it.remove();
		}
		// 注意：attr modifier 的实际移除需要在 entity 存在时才能做，由 onMerchantTickRemove 处理不可行。
		// 改为：下一轮 tickFlee 没有重新加入时，依靠下面的 maybeRemoveFleeModifier 反向扫描。
	}

	/** 外部调用（每驱逃 tick 后）：对不再被追踪的实体移除 modifier。这里由 SscAddon 每秒扫一次全世界不现实，
	 * 	改为在 tickFlee 中在 +modifier 同时记录 last seen，手动在 LivingEntityTickEvent 中检查。
	 * 	为避免额外 mixin，这里提供一个 static helper，由正在 tick 的商人自己调用。。。
	 * 	简化：这里不主动卸载；改用 SscAddon 服务器 tick 中调用下面的方法。 */
	public static void serverGlobalFleeCleanup(net.minecraft.server.MinecraftServer server) {
		long now = server.getOverworld().getTime();
		if (FLEE_AFFECTED.isEmpty()) return;
		for (ServerWorld world : server.getWorlds()) {
			for (UUID id : new ArrayList<>(FLEE_AFFECTED.keySet())) {
				if (now - FLEE_AFFECTED.getOrDefault(id, 0L) <= FLEE_LINGER_TICKS) continue;
				net.minecraft.entity.Entity ent = world.getEntity(id);
				if (ent instanceof LivingEntity le) {
					EntityAttributeInstance attr = le.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
					if (attr != null && attr.getModifier(MANCIANIMA_FEAR_SLOW_UUID) != null) {
						attr.removeModifier(MANCIANIMA_FEAR_SLOW_UUID);
					}
					FLEE_AFFECTED.remove(id);
				}
			}
		}
	}

	private static int countNearbyRaiders(PlayerEntity player, double radius) {
		Box box = player.getBoundingBox().expand(radius);
		return player.getWorld().getEntitiesByClass(RaiderEntity.class, box,
				e -> e.isAlive() && !e.isRemoved()).size();
	}

	private static class AssaultData {
		final Set<UUID> targets;
		final int initialCount;
		final ServerBossBar bossBar;
		AssaultData(Set<UUID> targets, ServerBossBar bossBar) {
			this.targets = targets;
			this.initialCount = targets.size();
			this.bossBar = bossBar;
		}
	}

	/** mob 死亡时调用：若属于某玩家的袭击目标 → 移除并检查是否完成。 */
	public static void onAssaultTargetDeath(LivingEntity dead) {
		if (dead.getWorld().isClient()) return;
		UUID deadId = dead.getUuid();
		Iterator<Map.Entry<UUID, AssaultData>> it = ASSAULTS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, AssaultData> e = it.next();
			AssaultData data = e.getValue();
			if (!data.targets.remove(deadId)) continue;
			// 更新 bossbar
			int remaining = data.targets.size();
			data.bossBar.setName(Text.translatable("ssc_addon.mancianima.assault.bossbar", remaining));
			data.bossBar.setPercent(data.initialCount > 0 ? (float) remaining / data.initialCount : 0f);
			if (remaining == 0) {
				ServerPlayerEntity p = dead.getWorld().getServer() == null ? null
						: dead.getWorld().getServer().getPlayerManager().getPlayer(e.getKey());
				if (p != null) {
					p.giveItemStack(new ItemStack(Items.EMERALD_BLOCK, 2));
					p.giveItemStack(new ItemStack(Items.TOTEM_OF_UNDYING, 1));
					p.sendMessage(Text.translatable("ssc_addon.mancianima.assault.complete"), false);
					if (p.getWorld() instanceof ServerWorld sw) {
						sw.playSound(null, p.getX(), p.getY(), p.getZ(),
								SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, p.getSoundCategory(), 1.0f, 1.0f);
					}
				}
				data.bossBar.clearPlayers();
				data.bossBar.setVisible(false);
				it.remove();
			}
		}
	}

	/** 村民/商人死亡时：契灵击杀 → 掉落部分可售卖物品 + 概率绿宝石。 */
	public static void onMerchantKilledByMancianima(MerchantEntity merchant, ServerPlayerEntity killer) {
		TradeOfferList offers = merchant.getOffers();
		boolean droppedSomething = false;
		if (offers != null && !offers.isEmpty()) {
			int picks = Math.min(2, offers.size());
			List<TradeOffer> shuffled = new ArrayList<>(offers);
			Collections.shuffle(shuffled, RNG);
			for (int i = 0; i < picks; i++) {
				ItemStack sell = shuffled.get(i).getSellItem();
				if (sell == null || sell.isEmpty()) continue;
				ItemStack drop = sell.copy();
				drop.setCount(Math.max(1, sell.getCount()));
				merchant.dropStack(drop);
				droppedSomething = true;
			}
		}
		// 无交易的村民（无职业/幼年）占多数：fallback 掉点 base 材料
		if (!droppedSomething) {
			merchant.dropStack(new ItemStack(Items.WHEAT, 1 + RNG.nextInt(3)));
			merchant.dropStack(new ItemStack(Items.BREAD, 1 + RNG.nextInt(2)));
		}
		// 30% 额外绿宝石（1-3）
		if (RNG.nextDouble() < 0.30) {
			merchant.dropStack(new ItemStack(Items.EMERALD, 1 + RNG.nextInt(3)));
		}
	}

	/** 是否应当因为契灵在场而逃跑（村民/商人 FleeEntityGoal 的谓词）。 */
	public static boolean shouldVillagerFlee(LivingEntity villager, PlayerEntity player) {
		if (!FormUtils.isForm(player, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) return false;
		if (player instanceof ServerPlayerEntity sp && WhitelistUtils.isProtected(sp, villager)) return false;
		return true;
	}

	public static void clearAll() {
		for (AssaultData data : ASSAULTS.values()) {
			if (data.bossBar != null) {
				data.bossBar.clearPlayers();
				data.bossBar.setVisible(false);
			}
		}
		ASSAULTS.clear();
		LAST_ASSAULT_ROLL.clear();
		FLEE_AFFECTED.clear();
	}
}
