/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.prayercaller;

import lombok.Data;

/**
 * Tracked combat state for one NPC: how long it's frozen and how much its defence has been
 * (estimated to be) reduced by special attacks.
 */
@Data
class NpcStatus
{
	/** Client tick at which the current freeze ends; -1 if not frozen. */
	private int freezeExpiryTick = -1;
	/** Running fraction of original defence (1.0 = full, 0.7 = 30% reduced). */
	private double defenceMultiplier = 1.0;

	boolean isFrozen(int currentTick)
	{
		return freezeExpiryTick > currentTick;
	}

	int freezeTicksLeft(int currentTick)
	{
		return Math.max(0, freezeExpiryTick - currentTick);
	}

	boolean hasDefenceReduction()
	{
		return defenceMultiplier < 0.999;
	}

	int defenceReductionPercent()
	{
		return (int) Math.round((1.0 - defenceMultiplier) * 100.0);
	}

	/** True once there's nothing left worth displaying. */
	boolean isExpired(int currentTick)
	{
		return !isFrozen(currentTick) && !hasDefenceReduction();
	}
}
