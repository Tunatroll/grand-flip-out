package com.grandflipout.network;

import com.grandflipout.GrandFlipOutApiConfig;
import com.grandflipout.GrandFlipOutConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles all outbound HTTP requests for live market data from the Grand Flip Out
 * Railway API at grandflipout.com. All I/O and JSON parsing run on a background
 * thread; the client thread is never blocked.
 */
@Slf4j
@Singleton
public class GrandFlipOutNetworkManager
{
	private static final Type OPPORTUNITY_LIST_TYPE = new TypeToken<List<MarketOpportunityDto>>(){}.getType();

	private final GrandFlipOutConfig config;
	private final GrandFlipOutApiConfig apiConfig;
	private final Gson gson = new Gson();
	private final HttpClient httpClient = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.build();

	private volatile ScheduledExecutorService scheduler;
	private volatile ScheduledFuture<?> pollingTask;
	private final AtomicLong lastFetchTimeMs = new AtomicLong(0);
	private volatile MarketDataResponse latestMarketData;
	private volatile String lastErrorMessage;
	private volatile Consumer<MarketDataResponse> marketDataCallback;

	@Inject
	public GrandFlipOutNetworkManager(GrandFlipOutConfig config, GrandFlipOutApiConfig apiConfig)
	{
		this.config = config;
		this.apiConfig = apiConfig;
	}

	/**
	 * Starts background polling at the interval defined in config.
	 * Safe to call from the client thread; scheduling runs on a dedicated executor.
	 */
	public void startPolling()
	{
		stopPolling();
		scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "GrandFlipOut-API-Poll");
			t.setDaemon(true);
			return t;
		});
		int intervalSeconds = Math.max(30, config.apiPollIntervalSeconds());
		pollingTask = scheduler.scheduleAtFixedRate(
			this::fetchMarketDataAsync,
			intervalSeconds,
			intervalSeconds,
			TimeUnit.SECONDS
		);
		log.debug("Market API polling started (interval {}s).", intervalSeconds);
	}

	/**
	 * Stops polling and releases the scheduler. Safe to call from the client thread.
	 */
	public void stopPolling()
	{
		if (pollingTask != null)
		{
			pollingTask.cancel(false);
			pollingTask = null;
		}
		if (scheduler != null)
		{
			scheduler.shutdown();
			try
			{
				if (!scheduler.awaitTermination(2, TimeUnit.SECONDS))
				{
					scheduler.shutdownNow();
				}
			}
			catch (InterruptedException e)
			{
				scheduler.shutdownNow();
				Thread.currentThread().interrupt();
			}
			scheduler = null;
		}
		log.debug("Market API polling stopped.");
	}

	/**
	 * Called by the scheduler (or optionally from a @Schedule method). Runs entirely
	 * on the background thread; no client-thread work.
	 */
	private void fetchMarketDataAsync()
	{
		String baseUrl = apiConfig.apiBaseUrl();
		if (baseUrl == null || baseUrl.isBlank())
		{
			lastErrorMessage = "API server URL is not configured.";
			log.warn("Grand Flip Out API server URL is not configured.");
			return;
		}
		String endpoint = apiConfig.apiEndpoint();
		if (endpoint == null || endpoint.isBlank())
		{
			endpoint = "/api/market";
		}
		if (!endpoint.startsWith("/"))
		{
			endpoint = "/" + endpoint;
		}
		String strategy = config.opportunityStrategy();
		String query = (strategy != null && !strategy.isBlank() && !"default".equalsIgnoreCase(strategy.trim()))
			? "?strategy=" + strategy.trim()
			: "";
		String url = baseUrl.replaceAll("/$", "") + endpoint + query;
		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.timeout(Duration.ofSeconds(15))
			.GET();
		String apiKey = apiConfig.apiKey();
		if (apiKey != null && !apiKey.isBlank())
		{
			requestBuilder.header("Authorization", "Bearer " + apiKey.trim());
		}
		HttpRequest request = requestBuilder.build();
		try
		{
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200)
			{
				if (response.statusCode() == 401 || response.statusCode() == 403)
				{
					lastErrorMessage = "Authentication failed. Check API key.";
				}
				else
				{
					lastErrorMessage = "Server returned status " + response.statusCode() + ".";
				}
				log.warn("Market API returned status {} for {}", response.statusCode(), url);
				return;
			}
			String body = response.body();
			MarketDataResponse parsed = parseMarketResponse(body);
			if (parsed != null)
			{
				if (parsed.getOpportunities() == null || parsed.getOpportunities().isEmpty())
				{
					List<MarketOpportunityDto> opportunities = fetchOpportunities(baseUrl, apiKey);
					if (opportunities != null)
					{
						parsed.setOpportunities(opportunities);
					}
				}
				lastFetchTimeMs.set(System.currentTimeMillis());
				onMarketDataReceived(parsed);
				lastErrorMessage = null;
			}
		}
		catch (IOException e)
		{
			lastErrorMessage = "Network error: " + e.getMessage();
			log.debug("Market API request failed: {}", e.getMessage());
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			lastErrorMessage = "Polling interrupted.";
			log.debug("Market API polling interrupted.");
		}
	}

	/**
	 * Override or extend in the future to push data to UI or other components.
	 * Called on the background thread after a successful fetch.
	 */
	protected void onMarketDataReceived(MarketDataResponse data)
	{
		latestMarketData = data;
		log.debug("Market data received: {} items.", data.getItems() != null ? data.getItems().size() : 0);
		Consumer<MarketDataResponse> callback = marketDataCallback;
		if (callback != null)
		{
			try
			{
				callback.accept(data);
			}
			catch (Exception e)
			{
				log.debug("Market data callback failed: {}", e.getMessage());
			}
		}
	}

	/**
	 * Returns the most recently fetched market data. May be null if no fetch has succeeded yet.
	 */
	public MarketDataResponse getLatestMarketData()
	{
		return latestMarketData;
	}

	private MarketDataResponse parseMarketResponse(String json)
	{
		try
		{
			return gson.fromJson(json, MarketDataResponse.class);
		}
		catch (JsonSyntaxException e)
		{
			log.warn("Failed to parse market API response: {}", e.getMessage());
			return null;
		}
	}

	private List<MarketOpportunityDto> fetchOpportunities(String baseUrl, String apiKey) throws IOException, InterruptedException
	{
		String endpoint = apiConfig.opportunitiesEndpoint();
		if (endpoint == null || endpoint.isBlank())
		{
			return null;
		}
		if (!endpoint.startsWith("/"))
		{
			endpoint = "/" + endpoint;
		}
		String strategy = config.opportunityStrategy();
		String query = (strategy != null && !strategy.isBlank() && !"default".equalsIgnoreCase(strategy.trim()))
			? "?strategy=" + strategy.trim()
			: "";
		String url = baseUrl.replaceAll("/$", "") + endpoint + query;
		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.timeout(Duration.ofSeconds(12))
			.GET();
		if (apiKey != null && !apiKey.isBlank())
		{
			requestBuilder.header("Authorization", "Bearer " + apiKey.trim());
		}
		HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200)
		{
			log.debug("Opportunities endpoint returned status {} for {}", response.statusCode(), url);
			return null;
		}
		try
		{
			String body = response.body();
			// Try wrapped format first: { "opportunities": [...] }
			MarketDataResponse wrapped = gson.fromJson(body, MarketDataResponse.class);
			if (wrapped != null && wrapped.getOpportunities() != null && !wrapped.getOpportunities().isEmpty())
			{
				return wrapped.getOpportunities();
			}
			// Fall back to plain array: [...]
			return gson.fromJson(body, OPPORTUNITY_LIST_TYPE);
		}
		catch (JsonSyntaxException e)
		{
			log.debug("Failed to parse opportunities response: {}", e.getMessage());
			return null;
		}
	}

	public long getLastFetchTimeMs()
	{
		return lastFetchTimeMs.get();
	}

	public String getLastErrorMessage()
	{
		return lastErrorMessage;
	}

	public void setMarketDataCallback(Consumer<MarketDataResponse> callback)
	{
		this.marketDataCallback = callback;
	}
}
