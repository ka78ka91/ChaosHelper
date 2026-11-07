package com.chaoshelper.api;

/**
 * Entry point implemented by every dynamic module.
 */
public interface ChaosModule {
	default void onEnable(ChaosContext context) {
	}

	default void onDisable(ChaosContext context) {
	}

	default void tick(ChaosContext context) {
	}

	default String status(ChaosContext context) {
		return "";
	}
}
