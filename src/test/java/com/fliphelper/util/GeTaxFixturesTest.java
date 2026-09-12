/*
 * Copyright (c) 2026, tuna troll
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the conditions in the BSD
 * 2-Clause License are met (see repository LICENSE file).
 */

package com.fliphelper.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * GE tax CONTRACT test — the plugin's {@link GeTax} is a hand port of the
 * ecosystem's canonical GE-tax SSOT (packages/ge-contracts/ge-tax in the
 * monorepo). This test replays that package's fixtures.json (a byte-copy kept in
 * src/test/resources; the monorepo's ge-tax-fixtures-hub-sync test pins the two
 * copies equal) so the Java port can never drift from the served numbers:
 * rate, cap, the exempt set, and 20 worked cases.
 */
public class GeTaxFixturesTest
{
	private static JsonObject fixtures()
	{
		try (Reader r = new InputStreamReader(
			GeTaxFixturesTest.class.getResourceAsStream("/ge-tax-fixtures.json"), StandardCharsets.UTF_8))
		{
			return new JsonParser().parse(r).getAsJsonObject();
		}
		catch (Exception e)
		{
			throw new IllegalStateException("ge-tax-fixtures.json missing from test resources", e);
		}
	}

	@Test
	public void constantsMatchTheContract()
	{
		JsonObject fx = fixtures();
		assertEquals(fx.get("rate").getAsDouble(), GeTax.RATE, 0.0);
		assertEquals(fx.get("cap").getAsLong(), GeTax.CAP_PER_ITEM);
	}

	@Test
	public void exemptSetMatchesTheContractExactly()
	{
		JsonObject fx = fixtures();
		Set<Integer> expected = new HashSet<>();
		for (JsonElement e : fx.getAsJsonArray("exempt_ids"))
		{
			expected.add(e.getAsInt());
		}
		// every contract id is exempt here, and nothing else in the contract's id space is
		for (int id : expected)
		{
			assertEquals("contract says exempt: " + id, true, GeTax.isExempt(id));
		}
		int javaExemptCount = 0;
		for (int id = 0; id < 40000; id++)
		{
			if (GeTax.isExempt(id))
			{
				javaExemptCount++;
				assertEquals("Java exempts an id the contract does not: " + id, true, expected.contains(id));
			}
		}
		assertEquals(expected.size(), javaExemptCount);
	}

	@Test
	public void everyWorkedCaseReplays()
	{
		JsonArray cases = fixtures().getAsJsonArray("cases");
		assertEquals(20, cases.size());
		for (JsonElement el : cases)
		{
			JsonObject c = el.getAsJsonObject();
			String label = c.get("label").getAsString();
			long price = c.get("sell_price").getAsLong();
			JsonElement idEl = c.get("item_id");
			int itemId = (idEl == null || idEl.isJsonNull()) ? 0 : idEl.getAsInt();
			long expected = GeTax.isExempt(itemId)
				? c.get("expected_tax_exempt").getAsLong()
				: c.get("expected_tax_raw").getAsLong();
			assertNotNull(label);
			assertEquals(label, expected, GeTax.tax(itemId, price, 1));
		}
	}
}
