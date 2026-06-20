/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.partydps;

import java.time.Duration;
import java.time.Instant;
import lombok.Getter;

/**
 * Accumulated damage and elapsed (wall-clock) time for one combatant. DPS = damage / active seconds,
 * with paused intervals excluded.
 */
@Getter
class DpsMember
{
	private final String name;
	private Instant start;
	private Instant end;
	private int damage;

	DpsMember(String name)
	{
		this.name = name;
	}

	void addDamage(int amount)
	{
		if (start == null)
		{
			start = Instant.now();
		}
		damage += amount;
	}

	float getDps()
	{
		if (start == null)
		{
			return 0f;
		}
		final Instant now = end == null ? Instant.now() : end;
		final long millis = now.toEpochMilli() - start.toEpochMilli();
		return millis <= 0 ? 0f : damage / (millis / 1000f);
	}

	boolean isPaused()
	{
		return start == null || end != null;
	}

	void pause()
	{
		if (start != null && end == null)
		{
			end = Instant.now();
		}
	}

	void unpause()
	{
		if (end == null)
		{
			return;
		}
		// Slide start forward by the paused duration so idle time isn't counted.
		start = start.plus(Duration.between(end, Instant.now()));
		end = null;
	}

	void reset()
	{
		damage = 0;
		start = null;
		end = null;
	}
}
