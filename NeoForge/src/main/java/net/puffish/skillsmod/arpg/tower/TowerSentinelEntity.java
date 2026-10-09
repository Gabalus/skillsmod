package net.puffish.skillsmod.arpg.tower;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;

/** Tracks the boss UI only; attack selection and hit resolution stay in Epic Fight. */
public final class TowerSentinelEntity extends HuskEntity {
	private final ServerBossBar bossBar = new ServerBossBar(Text.translatable("text.puffish_skills.sentinel.guarding", Text.translatable("entity.puffish_skills.tower_sentinel")),
			BossBar.Color.YELLOW, BossBar.Style.PROGRESS);

	public TowerSentinelEntity(EntityType<? extends HuskEntity> type, World world) {
		super(type, world);
	}

	@Override
	public void tick() {
		super.tick();
		if (!getWorld().isClient()) {
			updateBossBar();
		}
	}

	private void updateBossBar() {
		boolean enraged = SentinelPhase.at(getHealth(), getMaxHealth()) == SentinelPhase.ENRAGED;
		Text name = getCustomName() == null ? Text.translatable("entity.puffish_skills.tower_sentinel") : getCustomName().copy();
		bossBar.setName(Text.translatable(enraged ? "text.puffish_skills.sentinel.enraged" : "text.puffish_skills.sentinel.guarding", name));
		bossBar.setColor(enraged ? BossBar.Color.RED : BossBar.Color.YELLOW);
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
