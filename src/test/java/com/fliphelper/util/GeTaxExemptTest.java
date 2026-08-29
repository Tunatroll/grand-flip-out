/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * GE tax exemption pins — wiki-audited 2026-08-29 against the OSRS Wiki's
 * "Items exempt from Grand Exchange tax" category (45 items).
 *
 * History this pins against: the server-side mirrors (JS/py/fixture) all
 * carried four cooked foods (anchovies 319 / trout 333 / cod 341 /
 * swordfish 373) as exempt that the wiki taxes — Swordfish's own page
 * computes "Profit after GE Tax". THIS Java mirror was the only correct
 * stack. The two additions below were the only IDs it lacked (both noted
 * "not individually verified" in-source until now):
 *   28824 — Civitas illa fortis teleport (tablet), tabs exempt 29 May 2025
 *   5331  — Watering can (the EMPTY 5331 is the GE-tradeable variant)
 */
public class GeTaxExemptTest
{
	@Test
	public void auditedFoodsPayTax()
	{
		// Cooked anchovies / trout / cod / swordfish are NOT on the wiki's
		// exempt list — a 210gp swordfish pays floor(210 * 0.02) = 4gp.
		assertFalse(GeTax.isExempt(319));
		assertFalse(GeTax.isExempt(333));
		assertFalse(GeTax.isExempt(341));
		assertFalse(GeTax.isExempt(373));
		assertEquals(4L, GeTax.tax(373, 210L, 1));
	}

	@Test
	public void verifiedAdditionsAreExempt()
	{
		assertTrue(GeTax.isExempt(28824)); // Civitas illa fortis teleport (tablet)
		assertTrue(GeTax.isExempt(5331));  // Watering can (empty)
		assertEquals(0L, GeTax.tax(28824, 1_000L, 5));
	}

	@Test
	public void bondStaysExempt()
	{
		assertTrue(GeTax.isExempt(GeTax.OLD_SCHOOL_BOND));
		assertEquals(0L, GeTax.tax(GeTax.OLD_SCHOOL_BOND, 10_000_000L, 1));
	}
}
