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
			of(NPC_ANIMATION, 12146, MELEE)).killSound("goodboy.wav"),

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
			of(PROJECTILE, 1340, RANGED)),

		// Phantom Muspah — phase-1 mage/range flip (and melee phase) read from the attack animation.
		new BossDefinition("Phantom Muspah", PrayerCallerConfig::muspah, Set.of(12077, 12078, 12079, 12080),
			of(NPC_ANIMATION, 9918, MAGIC),
			of(NPC_ANIMATION, 9922, RANGED),
			of(NPC_ANIMATION, 9920, MELEE)),

		// --- Tombs of Amascut ---

		// Akkha (Path of Het) — distinct mage/range/melee animations (spear normal, sword on enrage).
		new BossDefinition("Akkha (ToA)", PrayerCallerConfig::akkha,
			Set.of(11789, 11790, 11791, 11792, 11793, 11794, 11795, 11796),
			of(NPC_ANIMATION, 9770, MELEE),
			of(NPC_ANIMATION, 9771, MELEE),
			of(NPC_ANIMATION, 9772, RANGED),
			of(NPC_ANIMATION, 9773, RANGED),
			of(NPC_ANIMATION, 9774, MAGIC),
			of(NPC_ANIMATION, 9775, MAGIC)),

		// Zebak (Path of Crondis) — mage/range share an animation, so detect autos by projectile.
		new BossDefinition("Zebak (ToA)", PrayerCallerConfig::zebak, Set.of(11730, 11732),
			of(PROJECTILE, 2176, MAGIC),
			of(PROJECTILE, 2177, MAGIC),
			of(PROJECTILE, 2178, RANGED),
			of(PROJECTILE, 2179, RANGED),
			of(NPC_ANIMATION, 9620, MELEE),
			of(NPC_ANIMATION, 9621, MELEE),
			of(NPC_ANIMATION, 9622, MELEE),
			of(NPC_ANIMATION, 9623, MELEE)),

		// Kephri (Path of Scabaras) — her fireball is dodged, not prayed; the real call is the agile
		// scarab's ranged attack. (presence includes the range-kiting scarab so its projectile matches.)
		new BossDefinition("Kephri (ToA)", PrayerCallerConfig::kephri, Set.of(11719, 11720, 11721, 11727),
			of(PROJECTILE, 2152, RANGED)),

		// Ba-Ba (Path of Apmeken) — melee only; her rocks/boulders bypass prayer.
		new BossDefinition("Ba-Ba (ToA)", PrayerCallerConfig::baba, Set.of(11778),
			of(NPC_ANIMATION, 9743, MELEE)),

		// The Wardens (final) — phase-2 auto-attacks alternate magic/ranged by projectile.
		new BossDefinition("Wardens (ToA)", PrayerCallerConfig::wardens,
			Set.of(11753, 11754, 11755, 11756, 11757, 11758),
			of(PROJECTILE, 2241, RANGED),
			of(PROJECTILE, 2224, MAGIC))
	);

	private Bosses()
	{
	}
}
