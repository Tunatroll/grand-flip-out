/*
 * Copyright (c) 2026, Tunatroll
 * All rights reserved.
 * SPDX-License-Identifier: BSD-2-Clause
 */
package com.fliphelper.telemetry;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Posts one batched, anonymous activation report per session.
 *
 * Consent is checked by the caller AND re-asserted here in {@link #shouldSend}. Both the decision
 * and the payload are pure static methods so the compliance behaviour is unit-testable without
 * touching the network — a mocked HTTP client could be wired wrong and still pass.
 */
@Slf4j
public class ActivationTelemetrySender
{
	private static final MediaType JSON = MediaType.parse("application/json");

	private final OkHttpClient httpClient;

	@Inject
	public ActivationTelemetrySender(OkHttpClient httpClient)
	{
		this.httpClient = httpClient;
	}

	/** No consent, or nothing to say, means no request is ever constructed. */
	public static boolean shouldSend(boolean consent, Map<String, Integer> counts, Map<String, Integer> panels)
	{
		if (!consent)
		{
			return false;
		}
		boolean hasCounts = counts != null && !counts.isEmpty();
		boolean hasPanels = panels != null && !panels.isEmpty();
		return hasCounts || hasPanels;
	}

	/** The exact payload: a session id and integer counts. Nothing else, ever. */
	public static String buildBody(String sessionId, Map<String, Integer> counts, Map<String, Integer> panels)
	{
		JsonObject root = new JsonObject();
		root.addProperty("sid", sessionId);
		root.add("counts", toJson(counts));
		root.add("panels", toJson(panels));
		return root.toString();
	}

	private static JsonObject toJson(Map<String, Integer> m)
	{
		JsonObject o = new JsonObject();
		if (m != null)
		{
			for (Map.Entry<String, Integer> e : m.entrySet())
			{
				o.addProperty(e.getKey(), e.getValue());
			}
		}
		return o;
	}

	/** Fire-and-forget. A failure is dropped: telemetry must never surface an error to a player. */
	public void send(String baseUrl, boolean consent, String sessionId,
		Map<String, Integer> counts, Map<String, Integer> panels)
	{
		if (!shouldSend(consent, counts, panels))
		{
			return;
		}
		Request request = new Request.Builder()
			.url(baseUrl + "/api/events/plugin-batch")
			.post(RequestBody.create(JSON, buildBody(sessionId, counts, panels)))
			.header("Accept", "application/json")
			.header("User-Agent", "GrandFlipOut-Plugin/1.0")
			.build();
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("activation telemetry dropped: {}", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				response.close();
			}
		});
	}
}
