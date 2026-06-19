/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.yamaprayer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Prayer;

/**
 * The three things Yama can do to you and the prayer that answers each one.
 */
@Getter
@RequiredArgsConstructor
enum YamaAttackStyle
{
	/** Flames envelop the player, then Yama snaps his fingers. Block with Protect from Magic. */
	MAGIC("Magic", "Protect from Magic", "magic.wav", Prayer.PROTECT_FROM_MAGIC),
	/** Shadowy swirls engulf the player, then Yama snaps his fingers. Block with Protect from Missiles. */
	RANGED("Ranged", "Protect from Missiles", "ranged.wav", Prayer.PROTECT_FROM_MISSILES),
	/** Yama swings his axe at adjacent targets. Protect from Melee greatly reduces the hit. */
	MELEE("Melee", "Protect from Melee", "melee.wav", Prayer.PROTECT_FROM_MELEE);

	private final String displayName;
	private final String prayerName;
	/** Bundled WAV resource (in this package) used when custom sounds are enabled. */
	private final String soundResource;
	/** The protection prayer that blocks this attack. */
	private final Prayer prayer;
}
