package net.puffish.skillsmod.client.event;

public interface ClientEventListener {
	void onPlayerJoin();
	default void onClientTick() {
	}
}
