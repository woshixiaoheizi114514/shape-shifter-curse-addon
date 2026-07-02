package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.player;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

/**
 * 荧光幼灵被动③ - 黏液保护膜闪避。
 * 玩家受到【物理攻击】伤害时 20% 概率完全闪避（取消伤害 + 水花粒子 + 音效）。
 * 仅对物理类伤害(mob_attack / player_attack / projectile / thorns 等)生效；
 * 魔法、药水、火焰、岩浆、窒息、饥饿、虚空等不闪避（用户确认 Q16）。
 * 坠落伤害由单独的 Apoli power 免疫（fall_immunity）。
 */
@Mixin(PlayerEntity.class)
public abstract class FluorescentDodgeMixin extends LivingEntity {

    private static final float DODGE_CHANCE = 0.20f;
    private static final Random DODGE_RNG = new Random();

    protected FluorescentDodgeMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void sscAddon$fluorescentDodge(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (this.getWorld().isClient) return;
        PlayerEntity self = (PlayerEntity) (Object) this;
        // 仅荧光幼灵形态
        if (!FormUtils.isAxolotlFluorescent(self)) return;
        // 仅物理类伤害闪避
        if (!isPhysicalDamage(source)) return;
        if (DODGE_RNG.nextFloat() < DODGE_CHANCE) {
            // 闪避成功：取消伤害 + 水花反馈
            if (self.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.SPLASH, self.getX(), self.getY() + 1.0, self.getZ(), 16, 0.4, 0.6, 0.4, 0.3);
                sw.spawnParticles(ParticleTypes.BUBBLE, self.getX(), self.getY() + 1.0, self.getZ(), 8, 0.4, 0.6, 0.4, 0.1);
                sw.playSound(null, self.getX(), self.getY() + 1.0, self.getZ(),
                        SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.PLAYERS, 0.6f, 1.5f);
            }
            cir.setReturnValue(false);
        }
    }

    /** 判定是否为物理类伤害（可被闪避）。 */
    private static boolean isPhysicalDamage(DamageSource source) {
        String id = source.getType().msgId();
        // 物理攻击/弹射物/荆棘/仙人掌/飞镖等；排除魔法/火焰/岩浆/药水/窒息/饥饿/虚空/溺水/干渴
        return "mob".equals(id) || "player".equals(id) || "sting".equals(id)
                || "arrow".equals(id) || "trident".equals(id) || "fireball".equals(id)
                || "thrown".equals(id) || "thorns".equals(id) || "cactus".equals(id)
                || "sweetBerryBush".equals(id) || "stalagmite".equals(id) || "fallingStalactite".equals(id)
                || source.getSource() != null && source.getSource().getType().isIn(net.minecraft.registry.tag.EntityTypeTags.ARROWS);
    }
}