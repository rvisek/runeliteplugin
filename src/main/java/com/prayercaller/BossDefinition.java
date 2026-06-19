/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.prayercaller;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import lombok.Getter;

/**
 * Everything the plugin needs to call prayers for one boss: how to tell it's present, and which game
 * events map to which attack style.
 */
@Getter
class BossDefinition
{
	private final String name;
	/** Whether this boss is enabled in config. */
	private final Predicate<PrayerCallerConfig> enabled;
	/** NPC ids whose presence means "you are fighting this boss" (scopes detection). */
	private final Set<Integer> presenceNpcIds;
	/** Fast lookup: type -> (id -> style). */
	private final Map<TriggerType, Map<Integer, AttackStyle>> triggers = new EnumMap<>(TriggerType.class);

	BossDefinition(String name, Predicate<PrayerCallerConfig> enabled, Set<Integer> presenceNpcIds,
		AttackTrigger... triggerList)
	{
		this.name = name;
		this.enabled = enabled;
		this.presenceNpcIds = presenceNpcIds;
		for (AttackTrigger t : triggerList)
		{
			triggers.computeIfAbsent(t.getType(), k -> new HashMap<>()).put(t.getId(), t.getStyle());
		}
	}

	/** @return the attack style for this event id, or null if this boss doesn't recognise it. */
	AttackStyle lookup(TriggerType type, int id)
	{
		final Map<Integer, AttackStyle> byId = triggers.get(type);
		return byId == null ? null : byId.get(id);
	}
}
