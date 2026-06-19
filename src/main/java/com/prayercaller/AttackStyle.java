/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.prayercaller;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Prayer;

/**
 * An attack style and the protection prayer that answers it.
 */
@Getter
@RequiredArgsConstructor
enum AttackStyle
{
	MAGIC("Magic", "Protect from Magic", "magic.wav", Prayer.PROTECT_FROM_MAGIC),
	RANGED("Ranged", "Protect from Missiles", "ranged.wav", Prayer.PROTECT_FROM_MISSILES),
	MELEE("Melee", "Protect from Melee", "melee.wav", Prayer.PROTECT_FROM_MELEE);

	private final String displayName;
	private final String prayerName;
	/** Bundled WAV resource (in this package) used when custom sounds are enabled. */
	private final String soundResource;
	/** The protection prayer that blocks this attack. */
	private final Prayer prayer;
}
