package net.onixary.shapeShifterCurseFabric.ssc_addon.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * 荧光幼灵（Axolotl Fluorescent）主要技能「潮汐波动」粒子球实体。
 *
 * <p>状态机：
 * <ul>
 *   <li>FLYING：4 格/s 向前飞行，每 tick 缓慢偏向主人准星方向（陶氏导弹式，最大 ~3°/tick）；最多飞 8 秒。</li>
 *   <li>DECELERATING：飞行中再次按键 / 飞满 8 秒触发，0.75 秒内速度线性衰减至 0（保持当前方向，不再转向）。</li>
 *   <li>ATTRACTING：静止后以半径 5 吸附周围非白名单生物，每次吸附作用 0.15s + 间隔 0.85s，共 6 次；被吸者获 15% 减速 1.5s（刷新）+ 脚底青蓝粒子。</li>
 *   <li>DELAY：6 次吸附完毕，延迟 0.35 秒后泡泡破裂消失。</li>
 * </ul>
 * 不消耗潮湿度；CD 在球完全消失后由技能管理器起算；可被净化（PURIFIED）直接消除。
 *
 * <p>渲染：实现 {@link net.minecraft.entity.FlyingItemEntity} 以潮涌（Conduit）方块作为可见核心
 * （由 FlyingItemEntityRenderer 渲染，对齐 red 火球标准），外层附以富的水/荧光拖尾粒子。
 */
public class TidalOrbEntity extends Entity implements net.minecraft.entity.FlyingItemEntity {

    // ===== 飞行参数 =====
    private static final double FLY_SPEED = 0.2;            // 4 格/s = 0.2 格/tick
    private static final int MAX_FLY_TICKS = 160;           // 8 秒后自动进入吸附
    private static final double MAX_TURN_RAD = Math.toRadians(3.0); // 每 tick 最多转向 3°（幅度小）

    // ===== 减速参数 =====
    private static final int DECEL_TICKS = 15;              // 0.75 秒减速到 0

    // ===== 吸附参数 =====
    private static final double ATTRACT_RADIUS = 5.0;
    private static final int ATTRACT_ACTIVE_TICKS = 3;      // 0.15 秒吸附作用
    private static final int ATTRACT_INTERVAL_TICKS = 17;   // 0.85 秒间隔
    private static final int ATTRACT_TOTAL_TIMES = 6;       // 共 6 次
    private static final double PULL_STRENGTH = 0.22;       // 单次吸附位移力度（×factor，温和聚怪不爆冲）
    private static final float PULL_PHYSICAL_DAMAGE = 4.0f; // 每次吸附范围内造成 4 点物理伤害

    // ===== 消失延迟 =====
    private static final int POP_DELAY_TICKS = 7;           // 0.35 秒后破裂

    private enum Phase { FLYING, DECELERATING, ATTRACTING, DELAY }

    private Phase phase = Phase.FLYING;
    private int ticksAlive = 0;
    private int phaseTicks = 0;
    private UUID ownerUuid;
    private Vec3d flyDir = new Vec3d(0, 0, 1);   // FLYING / DECEL 用
    private double currentSpeed = FLY_SPEED;     // DECEL 时衰减
    private int attractCount = 0;                // 已吸附次数
    private int attractCycleTick = 0;            // 吸附周期内 tick（0..INTERVAL）

    // ===== 粒子配色（青/蓝/淡蓝/白：水 + 荧光） =====
    private static final DustParticleEffect CYAN_DUST =
            new DustParticleEffect(new Vector3f(0.20f, 0.78f, 0.92f), 1.5f);
    private static final DustParticleEffect BLUE_DUST =
            new DustParticleEffect(new Vector3f(0.25f, 0.45f, 0.95f), 1.5f);
    private static final DustParticleEffect LIGHT_BLUE_DUST =
            new DustParticleEffect(new Vector3f(0.55f, 0.80f, 1.0f), 1.4f);

    public TidalOrbEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public TidalOrbEntity(World world, ServerPlayerEntity owner) {
        super(SscAddon.TIDAL_ORB_ENTITY, world);
        this.ownerUuid = owner.getUuid();
        // 从玩家眼部前方生成，方向取准星
        Vec3d look = owner.getRotationVec(1.0f);
        this.flyDir = look.normalize();
        this.setPosition(owner.getX() + look.x * 0.8, owner.getEyeY() - 0.1 + look.y * 0.8, owner.getZ() + look.z * 0.8);
        this.currentSpeed = FLY_SPEED;
        this.noClip = true;
    }

    @Override
    protected void initDataTracker() {
    }

    /** 飞行阶段：触发减速（玩家再次按键 / 飞满 8 秒自动）。 */
    public void triggerDecelerate() {
        if (phase == Phase.FLYING) {
            phase = Phase.DECELERATING;
            phaseTicks = 0;
        }
    }

    @Override
    public void tick() {
        super.tick();
        ticksAlive++;
        phaseTicks++;

        if (this.getWorld().isClient) {
            // 客户端：在实体（渲染核心）位置生成拖尾粒子，紧跟核心无网络延迟
            spawnTrailParticlesClient();
            return;
        }
        if (!(this.getWorld() instanceof ServerWorld sw)) return;

        // 净化消除：主人受到 PURIFIED 时球立即破裂
        if (isOwnerPurified(sw)) {
            pop(sw);
            return;
        }

        switch (phase) {
            case FLYING -> tickFlying(sw);
            case DECELERATING -> tickDecelerating(sw);
            case ATTRACTING -> tickAttracting(sw);
            case DELAY -> tickDelay(sw);
        }

        // 全局保底：超过 30 秒强制消失，防卡死
        if (ticksAlive > 600) {
            pop(sw);
        }
    }

    // ==================== FLYING ====================
    private void tickFlying(ServerWorld sw) {
        ServerPlayerEntity owner = getOwner(sw);
        // 向主人当前准星方向缓慢偏转（陶氏式追踪）
        if (owner != null) {
            Vec3d look = owner.getRotationVec(1.0f);
            if (look.lengthSquared() > 1.0e-6) {
                flyDir = turnToward(flyDir, look.normalize(), MAX_TURN_RAD);
            }
        }
        // 移动（撞墙则停下并进入吸附，不再穿墙）
        if (moveWithWallCheck(sw, flyDir.x * currentSpeed, flyDir.y * currentSpeed, flyDir.z * currentSpeed)) {
            enterAttractPhase(sw);
            return;
        }
        // 飞行环境音（每 10 tick 一次轻水波）
        if (ticksAlive % 10 == 0) {
            sw.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.ENTITY_AXOLOTL_SWIM, SoundCategory.PLAYERS, 0.6f, 1.1f);
        }

        if (ticksAlive >= MAX_FLY_TICKS) {
            triggerDecelerate();   // 飞满 8 秒自动进入减速
        }
    }

    // ==================== DECELERATING ====================
    private void tickDecelerating(ServerWorld sw) {
        // 0.75 秒线性减速到 0，保持当前方向不转向
        double t = (double) phaseTicks / DECEL_TICKS;
        currentSpeed = FLY_SPEED * (1.0 - MathHelper.clamp(t, 0.0, 1.0));
        if (moveWithWallCheck(sw, flyDir.x * currentSpeed, flyDir.y * currentSpeed, flyDir.z * currentSpeed)) {
            enterAttractPhase(sw);
            return;
        }
        if (phaseTicks >= DECEL_TICKS) {
            enterAttractPhase(sw);
        }
    }

    /** 撞墙检测移动：撞墙返回 true 并停在墙前（回退 0.25 格防嵌入）。 */
    private boolean moveWithWallCheck(ServerWorld sw, double dx, double dy, double dz) {
        Vec3d from = getPos();
        Vec3d to = from.add(dx, dy, dz);
        net.minecraft.util.hit.BlockHitResult hit = sw.raycast(new net.minecraft.world.RaycastContext(
                from, to, net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, this));
        if (hit.getType() != net.minecraft.util.hit.HitResult.Type.MISS) {
            Vec3d hp = hit.getPos();
            Vec3d back = from.subtract(to);
            if (back.lengthSquared() > 1.0e-6) back = back.normalize().multiply(0.25);
            else back = Vec3d.ZERO;
            setPosition(hp.x + back.x, hp.y + back.y, hp.z + back.z);
            return true;
        }
        setPosition(to.x, to.y, to.z);
        return false;
    }

    /** 进入吸附阶段（减速停下 / 撞墙 / 飞满时长）。 */
    private void enterAttractPhase(ServerWorld sw) {
        currentSpeed = 0.0;
        phase = Phase.ATTRACTING;
        phaseTicks = 0;
        attractCycleTick = 0;
        // 落点「潮汐降临」：一次清晰潮涌音（球位置 + 主人位置，不重叠）
        sw.playSound(null, getX(), getY(), getZ(),
                SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.1f);
        ServerPlayerEntity owner = getOwner(sw);
        if (owner != null) {
            sw.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                    SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.PLAYERS, 0.5f, 1.1f);
        }
        // 落点水柱爆开
        sw.spawnParticles(ParticleTypes.SPLASH, getX(), getY(), getZ(), 50, 0.8, 0.5, 0.8, 0.4);
        sw.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), getY() - 0.3, getZ(), 30, 0.6, 0.2, 0.6, 0.3);
    }

    // ==================== ATTRACTING ====================
    private void tickAttracting(ServerWorld sw) {
        // 悬停粒子（潮涌核心持续旋转）
        spawnHoverParticles(sw);
        // 吸附周期：前 ATTRACT_ACTIVE_TICKS 施加吸附力，之后等待 INTERVAL 再次触发
        if (attractCycleTick < ATTRACT_ACTIVE_TICKS) {
            applyPull(sw);
        }
        attractCycleTick++;
        if (attractCycleTick >= ATTRACT_INTERVAL_TICKS) {
            attractCycleTick = 0;
            attractCount++;
            if (attractCount >= ATTRACT_TOTAL_TIMES) {
                phase = Phase.DELAY;
                phaseTicks = 0;
            }
        }
    }

    /** 单次吸附：把半径 5 内非白名单生物向球拉，施加 15% 减速 + 4 物理伤害 + 脚底粒子。 */
    private void applyPull(ServerWorld sw) {
        Box box = new Box(getX() - ATTRACT_RADIUS, getY() - ATTRACT_RADIUS, getZ() - ATTRACT_RADIUS,
                getX() + ATTRACT_RADIUS, getY() + ATTRACT_RADIUS, getZ() + ATTRACT_RADIUS);
        List<LivingEntity> targets = sw.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator() && e.squaredDistanceTo(getX(), getY(), getZ()) <= ATTRACT_RADIUS * ATTRACT_RADIUS);
        ServerPlayerEntity owner = getOwner(sw);
        // 物理伤害来源（mob_attack，归属主人）
        RegistryKey<DamageType> dmgKey = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("minecraft", "mob_attack"));
        for (LivingEntity t : targets) {
            // 白名单 / 主人豁免（伤害与吸附均豁免）
            if (owner != null && WhitelistUtils.isProtected(owner, t)) continue;
            if (t.getUuid().equals(ownerUuid)) continue;
            // 拉向球：方向 × 力度
            Vec3d to = new Vec3d(getX() - t.getX(), getY() - (t.getY() + t.getHeight() * 0.5), getZ() - t.getZ());
            double dist = to.length();
            if (dist < 0.05) continue;
            // 力度随距离微调（近处弱、远处强一点），保证“明显位移但不爆冲”
            double factor = MathHelper.clamp(dist / ATTRACT_RADIUS, 0.15, 1.0);
            Vec3d pull = to.normalize().multiply(PULL_STRENGTH * factor);
            t.setVelocity(t.getVelocity().add(pull.x, pull.y, pull.z));
            t.velocityModified = true;
            // 15% 减速 1.5s（刷新）；防御：效果未注册时跳过避免 apoli 同步 NPE
            try {
                t.addStatusEffect(new StatusEffectInstance(SscAddon.TIDAL_SLOW, 30, 0, false, true, true));
            } catch (Throwable ignored) {
            }
            // 4 点物理伤害（归属主人）
            if (owner != null) {
                t.damage(t.getDamageSources().create(dmgKey, owner, owner), PULL_PHYSICAL_DAMAGE);
            }
            // 脚底青蓝粒子提示
            spawnAffectedFootParticles(sw, t);
        }
    }

    // ==================== DELAY ====================
    private void tickDelay(ServerWorld sw) {
        spawnHoverParticles(sw);
        if (phaseTicks >= POP_DELAY_TICKS) {
            pop(sw);
        }
    }

    // ==================== 破裂消失 ====================
    private void pop(ServerWorld sw) {
        double x = getX(), y = getY(), z = getZ();
        // 泡泡破裂：大量水/气泡/淡蓝 dust 向外迸射
        for (int i = 0; i < 80; i++) {
            Vec3d p = randomInSphere(1.8, random);
            sw.spawnParticles(ParticleTypes.BUBBLE, x + p.x, y + p.y, z + p.z, 1, 0, 0, 0, 0.03);
        }
        for (int i = 0; i < 50; i++) {
            Vec3d p = randomInSphere(1.4, random);
            sw.spawnParticles(ParticleTypes.SPLASH, x + p.x, y + p.y, z + p.z, 1, 0, 0, 0, 0.06);
        }
        sw.spawnParticles(CYAN_DUST, x, y, z, 60, 1.2, 1.2, 1.2, 0.0);
        sw.spawnParticles(LIGHT_BLUE_DUST, x, y, z, 60, 1.2, 1.2, 1.2, 0.0);
        sw.spawnParticles(ParticleTypes.END_ROD, x, y, z, 20, 0.5, 0.5, 0.5, 0.15);
        sw.spawnParticles(ParticleTypes.EXPLOSION, x, y, z, 3, 0.3, 0.3, 0.3, 0.0);
        sw.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, x, y - 0.3, z, 40, 0.8, 0.3, 0.8, 0.4);
        // 破裂：单一清晰水灵音（球位置 + 主人位置，不重叠）
        sw.playSound(null, x, y, z, SoundEvents.ENTITY_AXOLOTL_SPLASH, SoundCategory.PLAYERS, 1.3f, 0.9f);
        ServerPlayerEntity owner = getOwner(sw);
        if (owner != null) {
            sw.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                    SoundEvents.ENTITY_AXOLOTL_SPLASH, SoundCategory.PLAYERS, 0.6f, 0.9f);
        }
        // 通知技能管理器：球已消失，可开始 CD
        notifyManagerBallEnded();
        this.discard();
    }

    // ==================== 粒子 ====================
    private void spawnTrailParticlesClient() {
        double x = getX(), y = getY(), z = getZ();
        World w = this.getWorld();
        // 1. 核心发光（END_ROD + GLOW）
        w.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0, 0);
        w.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0, 0);
        if (ticksAlive % 2 == 0) w.addParticle(ParticleTypes.GLOW, x, y, z, 0, 0, 0);
        // 2. 旋转水环（6 点环绕，随时间旋转）
        double rot = ticksAlive * 0.35;
        for (int i = 0; i < 6; i++) {
            double a = rot + i * (Math.PI * 2 / 6);
            double r = 0.5;
            double px = x + Math.cos(a) * r;
            double pz = z + Math.sin(a) * r;
            double py = y + Math.sin(ticksAlive * 0.25 + i) * 0.12;
            w.addParticle((i % 2 == 0) ? CYAN_DUST : LIGHT_BLUE_DUST, px, py, pz, 0, 0, 0);
        }
        // 3. 球体水粒子（气泡 + 水花）
        for (int i = 0; i < 5; i++) {
            Vec3d p = randomInSphere(0.5, random);
            w.addParticle(ParticleTypes.BUBBLE, x + p.x, y + p.y, z + p.z, 0, 0.01, 0);
        }
        if (random.nextFloat() < 0.5f) {
            Vec3d p = randomInSphere(0.45, random);
            w.addParticle(ParticleTypes.SPLASH, x + p.x, y + p.y, z + p.z, 0, 0.02, 0);
        }
        // 4. 蓝色 dust 包围
        for (int i = 0; i < 3; i++) {
            Vec3d p = randomInSphere(0.35, random);
            w.addParticle(LIGHT_BLUE_DUST, x + p.x, y + p.y, z + p.z, 0, 0, 0);
        }
        // 5. 偶发鹦鹉螺
        if (random.nextFloat() < 0.35f) {
            w.addParticle(ParticleTypes.NAUTILUS, x, y, z, 0, 0, 0);
        }
    }

    private void spawnHoverParticles(ServerWorld sw) {
        double x = getX(), y = getY(), z = getZ();
        // 双层反向旋转潮涌核心环（收束漩涡）
        double rot = ticksAlive * 0.3;
        for (int layer = 0; layer < 2; layer++) {
            double rr = 0.6 + layer * 0.35;
            int cnt = 6 + layer * 3;
            for (int i = 0; i < cnt; i++) {
                double ang = rot * (layer == 0 ? 1 : -1) + i * (Math.PI * 2 / cnt);
                double px = x + Math.cos(ang) * rr;
                double pz = z + Math.sin(ang) * rr;
                double py = y + Math.sin(ticksAlive * 0.25 + i) * 0.18;
                sw.spawnParticles((layer == 0) ? CYAN_DUST : LIGHT_BLUE_DUST, px, py, pz, 1, 0, 0, 0, 0.0);
            }
        }
        sw.spawnParticles(ParticleTypes.END_ROD, x, y, z, 3, 0.15, 0.2, 0.15, 0.02);
        sw.spawnParticles(ParticleTypes.GLOW, x, y, z, 1, 0.1, 0.1, 0.1, 0.0);
        // 向内吸附的气泡（从外圈飞向核心）
        for (int i = 0; i < 3; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            double rr = ATTRACT_RADIUS * (0.6 + random.nextDouble() * 0.4);
            double px = x + Math.cos(ang) * rr;
            double pz = z + Math.sin(ang) * rr;
            double vx = (x - px) * 0.08, vz = (z - pz) * 0.08;
            sw.spawnParticles(ParticleTypes.BUBBLE, px, y + 0.2, pz, 0, vx, 0.02, vz, 1.0);
        }
        // 吸附脉冲（每周期开始：闪环 + 脉冲音）
        if (attractCycleTick == 0) {
            for (int i = 0; i < 30; i++) {
                double ang = i * (Math.PI * 2 / 30);
                sw.spawnParticles(BLUE_DUST, x + Math.cos(ang) * ATTRACT_RADIUS, y, z + Math.sin(ang) * ATTRACT_RADIUS, 1, 0, 0, 0, 0.0);
            }
            sw.playSound(null, x, y, z, SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, SoundCategory.PLAYERS, 0.7f, 0.8f);
        }
    }

    private void spawnAffectedFootParticles(ServerWorld sw, LivingEntity t) {
        double fx = t.getX(), fy = t.getY() + 0.05, fz = t.getZ();
        // 脚底青色 + 蓝色粒子（提示被吸附影响）
        for (int i = 0; i < 6; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            double r = random.nextDouble() * t.getWidth() * 0.6;
            sw.spawnParticles(CYAN_DUST, fx + Math.cos(ang) * r, fy, fz + Math.sin(ang) * r, 1, 0, 0.05, 0, 0.0);
        }
        for (int i = 0; i < 4; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            double r = random.nextDouble() * t.getWidth() * 0.6;
            sw.spawnParticles(BLUE_DUST, fx + Math.cos(ang) * r, fy, fz + Math.sin(ang) * r, 1, 0, 0.05, 0, 0.0);
        }
    }

    // ==================== 工具 ====================
    /** 将 from 方向朝 to 方向转最多 maxRad 弧度，返回新单位向量。 */
    private static Vec3d turnToward(Vec3d from, Vec3d to, double maxRad) {
        Vec3d f = from.normalize();
        Vec3d tt = to.normalize();
        double dot = MathHelper.clamp(f.dotProduct(tt), -1.0, 1.0);
        double angle = Math.acos(dot);
        if (angle < 1.0e-4) return f;
        double turn = Math.min(angle, maxRad);
        double t = turn / angle;
        // 球面线性插值（slerp）
        Vec3d cross = f.crossProduct(tt);
        if (cross.lengthSquared() < 1.0e-8) return tt; // 共线
        // 用四元数思路简化：f * sin((1-t)a)/sin(a) + to * sin(ta)/sin(a)
        double sa = Math.sin(angle);
        double w1 = Math.sin((1 - t) * angle) / sa;
        double w2 = Math.sin(t * angle) / sa;
        return new Vec3d(f.x * w1 + tt.x * w2, f.y * w1 + tt.y * w2, f.z * w1 + tt.z * w2).normalize();
    }

    private Vec3d randomInSphere(double r, net.minecraft.util.math.random.Random rnd) {
        double rr = r * Math.cbrt(rnd.nextDouble());
        double theta = rnd.nextDouble() * 2 * Math.PI;
        double phi = Math.acos(2 * rnd.nextDouble() - 1);
        double sinPhi = Math.sin(phi);
        return new Vec3d(rr * sinPhi * Math.cos(theta), rr * Math.cos(phi), rr * sinPhi * Math.sin(theta));
    }

    private ServerPlayerEntity getOwner(ServerWorld sw) {
        if (ownerUuid == null) return null;
        var server = sw.getServer();
        return server == null ? null : server.getPlayerManager().getPlayer(ownerUuid);
    }

    private boolean isOwnerPurified(ServerWorld sw) {
        ServerPlayerEntity owner = getOwner(sw);
        return owner != null && owner.hasStatusEffect(SscAddon.PURIFIED);
    }

    /** 球消失时回调技能管理器，让其开始 CD。 */
    private void notifyManagerBallEnded() {
        if (ownerUuid != null) {
            net.onixary.shapeShifterCurseFabric.ssc_addon.ability.FluorescentTidalManager.onBallRemoved(ownerUuid);
        }
    }

    // ==================== NBT / 同步 ====================
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Phase", phase.ordinal());
        nbt.putInt("TicksAlive", ticksAlive);
        nbt.putInt("PhaseTicks", phaseTicks);
        nbt.putDouble("DirX", flyDir.x);
        nbt.putDouble("DirY", flyDir.y);
        nbt.putDouble("DirZ", flyDir.z);
        nbt.putDouble("Speed", currentSpeed);
        nbt.putInt("AttractCount", attractCount);
        nbt.putInt("AttractCycle", attractCycleTick);
        if (ownerUuid != null) nbt.putUuid("Owner", ownerUuid);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        int p = nbt.contains("Phase") ? nbt.getInt("Phase") : 0;
        phase = Phase.values()[MathHelper.clamp(p, 0, Phase.values().length - 1)];
        ticksAlive = nbt.contains("TicksAlive") ? nbt.getInt("TicksAlive") : 0;
        phaseTicks = nbt.contains("PhaseTicks") ? nbt.getInt("PhaseTicks") : 0;
        double dx = nbt.contains("DirX") ? nbt.getDouble("DirX") : 0;
        double dy = nbt.contains("DirY") ? nbt.getDouble("DirY") : 0;
        double dz = nbt.contains("DirZ") ? nbt.getDouble("DirZ") : 1;
        flyDir = new Vec3d(dx, dy, dz).normalize();
        currentSpeed = nbt.contains("Speed") ? nbt.getDouble("Speed") : FLY_SPEED;
        attractCount = nbt.contains("AttractCount") ? nbt.getInt("AttractCount") : 0;
        attractCycleTick = nbt.contains("AttractCycle") ? nbt.getInt("AttractCycle") : 0;
        if (nbt.containsUuid("Owner")) ownerUuid = nbt.getUuid("Owner");
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket(this);
    }

    /** 渲染为潮涌（Conduit）方块作为发光核心。 */
    @Override
    public net.minecraft.item.ItemStack getStack() {
        return new net.minecraft.item.ItemStack(net.minecraft.item.Items.CONDUIT);
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean canHit() {
        return false;
    }

    /** 主人是否仍持有该球（供管理器查询）。 */
    public boolean isAliveOrActive() {
        return !this.isRemoved();
    }
}