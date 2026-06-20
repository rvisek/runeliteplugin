/*
 * Copyright (c) 2026, ryanv
 * BSD 2-Clause License. See LICENSE.
 */
package com.partydps;

import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Broadcast to the party for each of the local player's qualifying hits. The framework fills in the
 * sender's {@code memberId}. Uniquely named (not "DpsUpdate") so it doesn't clash with RuneLite's
 * built-in DPS counter on the party network.
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class PartyDpsHit extends PartyMemberMessage
{
	int hit;
}
