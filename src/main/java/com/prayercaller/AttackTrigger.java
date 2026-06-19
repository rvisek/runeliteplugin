/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.prayercaller;

import lombok.Value;

/**
 * Maps a single game-event id (animation / spotanim / projectile / npc) to the attack style it signals.
 */
@Value
class AttackTrigger
{
	TriggerType type;
	int id;
	AttackStyle style;

	static AttackTrigger of(TriggerType type, int id, AttackStyle style)
	{
		return new AttackTrigger(type, id, style);
	}
}
