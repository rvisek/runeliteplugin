/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.prayercaller;

/**
 * How an {@link AttackTrigger}'s id is matched against game events.
 */
enum TriggerType
{
	/** An animation played by the boss NPC (matched on AnimationChanged). */
	NPC_ANIMATION,
	/** A spotanim/graphic played on the boss NPC (matched on GraphicChanged). */
	NPC_SPOTANIM,
	/** A spotanim/graphic played on the local player (matched on GraphicChanged). */
	PLAYER_SPOTANIM,
	/** A projectile id launched during the fight (matched on ProjectileMoved, first sighting). */
	PROJECTILE,
	/** An NPC id appearing in the world — e.g. a Zulrah form or a Cerberus ghost (matched on NpcSpawned). */
	NPC_SPAWN
}
