package net.puffish.skillsmod.arpg.tower;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.puffish.skillsmod.arpg.combat.EnemySpellAction;
import net.puffish.skillsmod.main.OptionalEpicMelee;
import net.puffish.skillsmod.main.OptionalIronsSpells;

/** Melee stays in Epic Fight; the ranged telegraph releases an optional native Iron's spell. */
public final class TowerSentinelEntity extends HuskEntity {
	private final EnemySpellAction spellAction = new EnemySpellAction();
	private float spellYaw;
	private float spellPitch;
	private final ServerBossBar bossBar = new ServerBossBar(Text.translatable("text.puffish_skills.sentinel.guarding", Text.translatable("entity.puffish_skills.tower_sentinel")),
			BossBar.Color.YELLOW, BossBar.Style.PROGRESS);

	public TowerSentinelEntity(EntityType<? extends HuskEntity> type, World world) {
		super(type, world);
	}

	@Override
	public void tick() {
		super.tick();
		if (!getWorld().isClient()) {
			tickSpell();
			updateBossBar();
		}
	}

	private void tickSpell() {
		var target = getTarget();
		boolean eligible = isAlive() && !isAiDisabled() && isOnGround() && !hasVehicle() && hurtTime == 0
				&& target instanceof ServerPlayerEntity player && !(player instanceof FakePlayer)
				&& player.getWorld() == getWorld()
				&& player.isAlive() && !player.isCreative() && !player.isSpectator() && !isTeammate(player)
				&& squaredDistanceTo(player) >= 16 && squaredDistanceTo(player) <= 144 && canSee(player)
				&& OptionalIronsSpells.enemyAvailable() && OptionalEpicMelee.enemyReady(this);
		var event = spellAction.tick(target == null ? null : target.getUuid(), eligible);
		if (event == EnemySpellAction.Event.START) {
			var direction = target.getEyePos().subtract(getEyePos());
			spellYaw = (float) (Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90);
			spellPitch = (float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalLength()));
		}
		if (spellAction.casting() || event == EnemySpellAction.Event.RELEASE) {
			getNavigation().stop();
			// Aim locks at the start of the warning, giving the player time to sidestep.
			setYaw(spellYaw);
			setHeadYaw(spellYaw);
			setBodyYaw(spellYaw);
			setPitch(spellPitch);
			if (getWorld() instanceof ServerWorld server) {
				server.spawnParticles(ParticleTypes.SNOWFLAKE, getX(), getEyeY(), getZ(), 6, .3, .3, .3, .01);
			}
		}
		if (event == EnemySpellAction.Event.RELEASE) {
			OptionalIronsSpells.releaseEnemySpell(this);
		}
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("arpg_spell_cooldown", spellAction.cooldown());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("arpg_spell_cooldown")) {
			spellAction.restore(nbt.getInt("arpg_spell_cooldown"));
		}
	}

	private void updateBossBar() {
		boolean enraged = SentinelPhase.at(getHealth(), getMaxHealth()) == SentinelPhase.ENRAGED;
		Text name = getCustomName() == null ? Text.translatable("entity.puffish_skills.tower_sentinel") : getCustomName().copy();
		bossBar.setName(Text.translatable(spellAction.casting() ? "text.puffish_skills.sentinel.casting"
				: enraged ? "text.puffish_skills.sentinel.enraged" : "text.puffish_skills.sentinel.guarding", name));
		bossBar.setColor(spellAction.casting() ? BossBar.Color.BLUE : enraged ? BossBar.Color.RED : BossBar.Color.YELLOW);
		bossBar.setPercent(SentinelPhase.healthFraction(getHealth(), getMaxHealth()));
		bossBar.setVisible(isAlive() && !isRemoved());
	}

	@Override
	public void onStartedTrackingBy(ServerPlayerEntity player) {
		super.onStartedTrackingBy(player);
		updateBossBar();
		bossBar.addPlayer(player);
	}

	@Override
	public void onStoppedTrackingBy(ServerPlayerEntity player) {
		super.onStoppedTrackingBy(player);
		bossBar.removePlayer(player);
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		bossBar.clearPlayers();
		super.remove(reason);
	}

	@Override
	protected boolean canConvertInWater() {
		return false;
	}
}
