package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.entity;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.GoldenSandstormRegen;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.AllaySPRangedHitPassive;
import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.MancianimaMarkManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.power.EffectEfficiencyReductionPower;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.UndeadNeutralState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LivingEntity.class)
public abstract class SscAddonLivingEntityMixin {

	/**
	 * 中立生物被玩家攻击时，记录全局挑衅状态。
	 * 裁决者: 攻击任何亡灵触发挑衅
	 * 金沙岚: 攻击尸壳或咒文胡狼触发挑衅
	 */
	@Inject(method = "damage", at = @At("HEAD"))
	private void ssc_addon$onUndeadDamaged(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof MobEntity mob
				&& source.getAttacker() instanceof PlayerEntity player) {
			// 裁决者: 所有亡灵触发挑衅
			if (mob.getGroup() == EntityGroup.UNDEAD
					&& FormUtils.isForm(player, FormIdentifiers.ANUBIS_WOLF_SP)) {
				UndeadNeutralState.PROVOKE_TIMESTAMPS.put(player.getUuid(), mob.getWorld().getTime());
			}
			// 金沙岚: 仅尸壳和咒文胡狼触发挑衅
			if ((mob instanceof HuskEntity || FormUtils.isTransformativeWolf(mob))
					&& FormUtils.isForm(player, FormIdentifiers.GOLDEN_SANDSTORM_SP)) {
				UndeadNeutralState.PROVOKE_TIMESTAMPS.put(player.getUuid(), mob.getWorld().getTime());
			}
		}

		// ==== 金沙岚回血系统 ====
		if (!self.getWorld().isClient()) {
			// 凋零 tick 伤害 → 为已注册的金沙岚来源回血
			if (source.isOf(DamageTypes.WITHER)) {
				GoldenSandstormRegen.onWitherTickDamage(self);
			}
			// 金沙岚玩家亲手造成伤害 → 标记战斗状态
			if (source.getAttacker() instanceof ServerPlayerEntity attacker
					&& FormUtils.isForm(attacker, FormIdentifiers.GOLDEN_SANDSTORM_SP)) {
				GoldenSandstormRegen.markCombat(attacker);
			}
			// 冥狼造成伤害 → 为主人（金沙岚）标记战斗状态
			if (source.getAttacker() instanceof net.onixary.shapeShifterCurseFabric.minion.mobs.AnubisWolfMinionEntity wolf
					&& self.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
				java.util.UUID ownerUuid = wolf.getMinionOwnerUUID();
				if (ownerUuid != null) {
					net.minecraft.entity.player.PlayerEntity owner = serverWorld.getPlayerByUuid(ownerUuid);
					if (owner instanceof ServerPlayerEntity ownerPlayer
							&& FormUtils.isForm(ownerPlayer, FormIdentifiers.GOLDEN_SANDSTORM_SP)) {
						GoldenSandstormRegen.markCombat(ownerPlayer);
					}
				}
			}
		}
	}

	/**
	 * 拦截带源的 addStatusEffect：当金沙岚玩家给受害者施加凋零时，注册凋零来源用于回血。
	 */
	@Inject(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"))
	private void ssc_addon$registerGoldenSandstormWitherSource(StatusEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
		if (effect.getEffectType() != StatusEffects.WITHER) return;
		if (!(source instanceof ServerPlayerEntity sp)) return;
		if (!FormUtils.isForm(sp, FormIdentifiers.GOLDEN_SANDSTORM_SP)) return;
		LivingEntity self = (LivingEntity) (Object) this;
		GoldenSandstormRegen.registerWitherSource(self, sp, effect.getDuration());
	}

	@Inject(method = "damage", at = @At("RETURN"))
	private void ssc_addon$onAllayRangedHit(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue()) return;
		LivingEntity self = (LivingEntity) (Object) this;
		AllaySPRangedHitPassive.onDamageApplied(self, source);
	}

	@ModifyArgs(method = "damage", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;applyDamage(Lnet/minecraft/entity/damage/DamageSource;F)V"))
	private void ssc_addon$capAllayIncomingDamage(Args args) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getWorld().isClient() || !FormUtils.isAllaySP(self)) return;
		DamageSource source = args.get(0);
		if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) return;

		float amount = args.get(1);
		float maxDamage = self.getMaxHealth() * 0.25F;
		if (amount > maxDamage) {
			args.set(1, maxDamage);
		}
	}

	@ModifyVariable(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"), argsOnly = true)
	private StatusEffectInstance modifyStatusEffect(StatusEffectInstance effect) {
		if (!effect.getEffectType().isInstant() && PowerHolderComponent.hasPower((LivingEntity) (Object) this, EffectEfficiencyReductionPower.class)) {
			int originalAmp = effect.getAmplifier();
			int newDuration;

			// Logic:
			// Level 1 (amp 0): Duration * 0.4 (40%)
			// Level 2 (amp 1): Duration * 0.6 (60%), Amp -> 0
			// Level 3 (amp 2): Duration * 0.8 (80%), Amp -> 0
			// Level 4+ (amp 3+): Duration * 1.0 (100%), Amp -> 0

			if (originalAmp == 0) {
				// Level 1
				newDuration = (int) (effect.getDuration() * 0.4);
			} else if (originalAmp == 1) {
				// Level 2
				newDuration = (int) (effect.getDuration() * 0.6);
			} else if (originalAmp == 2) {
				// Level 3
				newDuration = (int) (effect.getDuration() * 0.8);
			} else {
				// Level 4+
				newDuration = effect.getDuration();
			}

			return new StatusEffectInstance(
					effect.getEffectType(),
					newDuration,
					0, // Always force to Level 1 (amplifier 0)
					effect.isAmbient(),
					effect.shouldShowParticles(),
					effect.shouldShowIcon(),
					null,
					effect.getFactorCalculationData()
			);
		}
		return effect;
	}

	// ============== 契灵 - 抗伤值 / 无敌帧 / 恐惧伤害修正 ==============

	/**
	 * HEAD 注入：契灵抗伤值与无敌帧。
	 * - 受害者是契灵 + iframes>0 → 取消伤害（不消耗抗伤）
	 * - 受害者是契灵 + resistance>0 → 取消伤害+取消击退，消耗1抗伤，置iframes=4tick(0.2s)
	 */
	@Inject(method = "damage", at = @At("HEAD"), cancellable = true)
	private void ssc_addon$mancianimaResistance(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self instanceof ServerPlayerEntity sp)) return;
		if (!FormUtils.isForm(sp, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) return;
		// 跳过虚空/直接击杀，避免BUG
		if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) return;
		// 仅处理"由其它玩家/生物造成的伤害"（近战、远程、魔法）。
		// 环境伤害（坠落、溺水、岩浆、火焰、窒息、仙人掌、饥饿等）的 attacker 为 null，将不抵挡也不进入战斗。
		Entity attacker = source.getAttacker();
		if (!(attacker instanceof LivingEntity) || attacker == sp) return;
		// 受击 → 进入战斗状态（用于 15s 抗伤回复门槛）
		MancianimaMarkManager.markCombat(sp.getUuid(), sp.getServerWorld().getTime());
		int iframes = PowerUtils.getResourceValue(sp, FormIdentifiers.MANCIANIMA_IFRAMES);
		if (iframes > 0) {
			cir.setReturnValue(false);
			return;
		}
		int resist = PowerUtils.getResourceValue(sp, FormIdentifiers.MANCIANIMA_RESISTANCE);
		if (resist > 0) {
			PowerUtils.setResourceValueAndSync(sp, FormIdentifiers.MANCIANIMA_RESISTANCE, resist - 1);
			PowerUtils.setResourceValueAndSync(sp, FormIdentifiers.MANCIANIMA_IFRAMES, 4);
			// 抵抗触发音效：铁砧落地（全场可听见，提示周围玩家）
			sp.getServerWorld().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
					net.minecraft.sound.SoundEvents.BLOCK_ANVIL_LAND,
					net.minecraft.sound.SoundCategory.PLAYERS, 0.6f, 1.6f);
			cir.setReturnValue(false);
		}
	}

	/**
	 * 恐惧伤害修正：契灵 marker 对其红标受害者 +25%；红标受害者对 marker -25%。
	 * 注入 applyDamage 调用点，可同时访问 DamageSource 和 amount。
	 */
	@ModifyArgs(method = "damage", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;applyDamage(Lnet/minecraft/entity/damage/DamageSource;F)V"))
	private void ssc_addon$mancianimaFearAmount(Args args) {
		LivingEntity self = (LivingEntity) (Object) this;
		DamageSource source = args.get(0);
		float amount = args.get(1);
		Entity attacker = source.getAttacker();
		// 攻击发生在契灵玩家身上 → 进入战斗（攻击方为契灵也算）
		if (attacker instanceof ServerPlayerEntity ap && FormUtils.isForm(ap, FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)) {
			MancianimaMarkManager.markCombat(ap.getUuid(), ap.getServerWorld().getTime());
		}
		if (attacker instanceof ServerPlayerEntity ap
				&& MancianimaMarkManager.isRedMarkedBy(ap.getUuid(), self.getUuid())) {
			args.set(1, amount * 1.25f);
			return;
		}
		if (self instanceof ServerPlayerEntity sp && attacker != null) {
			java.util.UUID markerOf = MancianimaMarkManager.getMarkerOf(attacker.getUuid());
			if (markerOf != null && markerOf.equals(sp.getUuid())) {
				MancianimaMarkManager.Mark m = MancianimaMarkManager.getMark(sp.getUuid());
				if (m != null && m.color == MancianimaMarkManager.MarkColor.RED) {
					args.set(1, amount * 0.75f);
				}
			}
		}
	}
}
