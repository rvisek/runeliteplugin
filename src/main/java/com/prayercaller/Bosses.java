/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.prayercaller;

import java.util.List;
import java.util.Set;
import static com.prayercaller.AttackStyle.MAGIC;
import static com.prayercaller.AttackStyle.MELEE;
import static com.prayercaller.AttackStyle.RANGED;
import static com.prayercaller.AttackTrigger.of;
import static com.prayercaller.TriggerType.NPC_ANIMATION;
import static com.prayercaller.TriggerType.NPC_SPAWN;
import static com.prayercaller.TriggerType.NPC_SPOTANIM;
import static com.prayercaller.TriggerType.PLAYER_SPOTANIM;
import static com.prayercaller.TriggerType.PROJECTILE;

/**
 * The supported bosses and how each one's attacks are detected. All ids are sourced from RuneLite's
 * gameval cache dumps and the (Open)OSRS boss plugins — see README for the per-boss provenance.
 */
final class Bosses
{
	static final List<BossDefinition> ALL = List.of(
		// Yama — flames/shadow telegraph on the player, plus the cast on Yama for earliest warning.
		new BossDefinition("Yama", PrayerCallerConfig::yama, Set.of(14176),
			of(NPC_SPOTANIM, 3246, MAGIC),
			of(NPC_SPOTANIM, 3243, RANGED),
			of(PLAYER_SPOTANIM, 3247, MAGIC),
			of(PLAYER_SPOTANIM, 3244, RANGED),
			of(NPC_ANIMATION, 12146, MELEE)),

		// TzTok-Jad — distinct mage/range/melee attack animations.
		new BossDefinition("TzTok-Jad", PrayerCallerConfig::jad, Set.of(3127),
			of(NPC_ANIMATION, 7592, MAGIC),
			of(NPC_ANIMATION, 7593, RANGED),
			of(NPC_ANIMATION, 7590, MELEE)),

		// Crystalline & Corrupted Hunllef (The Gauntlet) — style read from the launched projectile.
		new BossDefinition("Hunllef (Gauntlet)", PrayerCallerConfig::hunllef,
			Set.of(9021, 9022, 9023, 9024, 9035, 9036, 9037, 9038),
			of(PROJECTILE, 1707, MAGIC),
			of(PROJECTILE, 1708, MAGIC),
			of(PROJECTILE, 1711, RANGED),
			of(PROJECTILE, 1712, RANGED)),

		// Cerberus — the summoned soul ghost that spawns tells you the next prayer.
		new BossDefinition("Cerberus", PrayerCallerConfig::cerberus, Set.of(5862, 5863, 5866),
			of(NPC_SPAWN, 5868, MAGIC),
			of(NPC_SPAWN, 5867, RANGED),
			of(NPC_SPAWN, 5869, MELEE)),

		// Vorkath — the two basic attacks share an animation, so detect by projectile.
		new BossDefinition("Vorkath", PrayerCallerConfig::vorkath, Set.of(8061),
			of(PROJECTILE, 1479, MAGIC),
			of(PROJECTILE, 1477, RANGED)),

		// Zulrah — each form is a distinct NPC id that determines the style.
		new BossDefinition("Zulrah", PrayerCallerConfig::zulrah, Set.of(2042, 2043, 2044),
			of(NPC_SPAWN, 2044, MAGIC),
			of(NPC_SPAWN, 2042, RANGED),
			of(NPC_SPAWN, 2043, MELEE)),

		// Great Olm (Chambers of Xeric) — head auto-attacks alternate magic/ranged by projectile.
		new BossDefinition("Great Olm", PrayerCallerConfig::olm, Set.of(7551, 7554),
			of(PROJECTILE, 1339, MAGIC),
			of(PROJECTILE, 1340, RANGED))
	);

	private Bosses()
	{
	}
}
