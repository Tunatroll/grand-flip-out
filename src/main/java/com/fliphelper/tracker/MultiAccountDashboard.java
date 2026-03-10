package com.fliphelper.tracker;

import com.google.gson.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


@Slf4j
public class MultiAccountDashboard
{

	private static final int MAX_ACCOUNTS = 10;
	private static final int MAX_GE_SLOTS = 8;
	private static final long GE_LIMIT_CYCLE_MILLIS = 4 * 60 * 60 * 1000; // 4 hours

	private final Map<String, Account> accounts;
	private final Path dataFilePath;
	private final Gson gson;

	public MultiAccountDashboard(String dataDir, Gson gson)
	{
		this.accounts = new ConcurrentHashMap<>();
		this.dataFilePath = Paths.get(dataDir, "multi_account_data.json");
		this.gson = gson;
		loadFromFile();
	}

	
	public boolean addAccount(Account account)
	{
		if (accounts.size() >= MAX_ACCOUNTS)
		{
			log.warn("Cannot add account {}. Dashboard at max capacity of {} accounts.",
				account.getAccountName(), MAX_ACCOUNTS);
			return false;
		}

		accounts.put(account.getAccountName(), account);
		log.info("Added account: {}", account.getAccountName());
		saveToFile();
		return true;
	}

	
	public boolean removeAccount(String accountName)
	{
		if (accounts.remove(accountName) != null)
		{
			log.info("Removed account: {}", accountName);
			saveToFile();
			return true;
		}
		return false;
	}

	
	public Account getAccount(String accountName)
	{
		return accounts.get(accountName);
	}

	
	public Collection<Account> getAllAccounts()
	{
		return Collections.unmodifiableCollection(accounts.values());
	}

	
	public AggregateStats getAggregateStats()
	{
		long totalCash = 0;
		long totalBankValue = 0;
		double totalUnrealizedPnL = 0;
		long totalCapitalInvested = 0;
		long totalGPPerHour = 0;
		int accountCount = accounts.size();

		for (Account account : accounts.values())
		{
			totalCash += account.getCashStack();
			totalBankValue += account.getTotalBankValue();

			for (AccountPosition pos : account.getPositions().values())
			{
				totalCapitalInvested += (pos.getBuyPrice() * pos.getQuantity());
				totalUnrealizedPnL += pos.getUnrealizedPnL();
			}

			// Calculate GP/hr for this account (simple approximation)
			if (account.getLastActive() != null)
			{
				long msActive = System.currentTimeMillis() - account.getLastActive().toEpochMilli();
				if (msActive > 0)
				{
					long hoursActive = msActive / (60 * 60 * 1000);
					if (hoursActive > 0 && totalUnrealizedPnL > 0)
					{
						totalGPPerHour += (totalUnrealizedPnL / hoursActive);
					}
				}
			}
		}

		long totalExposure = totalCash + totalCapitalInvested;
		long averageGPPerHour = accountCount > 0 ? totalGPPerHour / accountCount : 0;

		return AggregateStats.builder()
			.totalCash(totalCash)
			.totalBankValue(totalBankValue)
			.totalCapitalInvested(totalCapitalInvested)
			.totalExposure(totalExposure)
			.totalUnrealizedPnL(totalUnrealizedPnL)
			.totalAccounts(accountCount)
			.averageGPPerHour(averageGPPerHour)
			.build();
	}

	
	public Map<String, Integer> getGeLimitAvailability(int itemId)
	{
		Map<String, Integer> availability = new HashMap<>();

		for (Account account : accounts.values())
		{
			// Check if this item's GE limit has reset
			Instant lastReset = account.getGeLimitResets().get(itemId);
			int usedLimit = 0;

			if (lastReset != null)
			{
				long timeSinceReset = System.currentTimeMillis() - lastReset.toEpochMilli();
				if (timeSinceReset < GE_LIMIT_CYCLE_MILLIS)
				{
					// Still within same 4-hour cycle; check how much was used
					AccountPosition pos = account.getPositions().values().stream()
						.filter(p -> p.getItemId() == itemId)
						.findFirst()
						.orElse(null);

					if (pos != null)
					{
						usedLimit = pos.getQuantity();
					}
				}
			}

			// Assuming standard GE limits (most items are 10k/4h, or similar)
			int remainingLimit = Math.max(0, 10000 - usedLimit); // Simplified default
			availability.put(account.getAccountName(), remainingLimit);
		}

		return availability;
	}

	// NOTE: suggestAccountForPurchase() was removed for Plugin Hub compliance.
	// Cross-account purchase recommendations could be interpreted as facilitating
	// coordinated market manipulation. Users should make independent decisions
	// per account. Use getGeLimitAvailability() for display-only limit info.

	
	public PortfolioHeatmap getPortfolioHeatmap()
	{
		Map<Integer, ItemDistribution> itemMap = new HashMap<>();
		Map<String, List<Integer>> accountHoldings = new HashMap<>();

		for (Account account : accounts.values())
		{
			List<Integer> holdings = new ArrayList<>();

			for (AccountPosition pos : account.getPositions().values())
			{
				int itemId = pos.getItemId();
				holdings.add(itemId);

				itemMap.computeIfAbsent(itemId, k -> ItemDistribution.builder()
					.itemId(itemId)
					.itemName(pos.getItemName())
					.accountsHolding(new HashSet<>())
					.build())
					.getAccountsHolding().add(account.getAccountName());
			}

			accountHoldings.put(account.getAccountName(), holdings);
		}

		// Identify concentration risks
		List<String> risks = new ArrayList<>();
		for (Account account : accounts.values())
		{
			Map<Integer, Long> itemValueMap = new HashMap<>();
			long totalValue = 0;

			for (AccountPosition pos : account.getPositions().values())
			{
				long value = pos.getCurrentPrice() * pos.getQuantity();
				itemValueMap.put(pos.getItemId(), value);
				totalValue += value;
			}

			if (totalValue > 0)
			{
				for (Map.Entry<Integer, Long> entry : itemValueMap.entrySet())
				{
					double percentage = (double) entry.getValue() / totalValue * 100;
					if (percentage > 50)
					{
						risks.add(String.format("%s is %.1f%% invested in a single item (ID: %d)",
							account.getAccountName(), percentage, entry.getKey()));
					}
				}
			}
		}

		return PortfolioHeatmap.builder()
			.itemDistribution(itemMap)
			.accountHoldings(accountHoldings)
			.concentrationRisks(risks)
			.build();
	}

	// NOTE: getCombinedBuyingPower() was removed for Plugin Hub compliance.
	// Aggregating buying power across accounts could facilitate coordinated
	// market cornering. Use getGeLimitAvailability() per-account instead.

	
	public PortfolioRebalancer createRebalancer()
	{
		return new PortfolioRebalancer(this);
	}

	
	public void saveToFile()
	{
		try
		{
			Files.createDirectories(dataFilePath.getParent());
			String json = gson.toJson(new DashboardSnapshot(accounts));
			Files.write(dataFilePath, json.getBytes());
			log.debug("Dashboard saved to {}", dataFilePath);
		}
		catch (IOException e)
		{
			log.error("Failed to save dashboard to {}", dataFilePath, e);
		}
	}

	
	private void loadFromFile()
	{
		if (Files.exists(dataFilePath))
		{
			try
			{
				String json = Files.readString(dataFilePath);
				DashboardSnapshot snapshot = gson.fromJson(json, DashboardSnapshot.class);
				if (snapshot != null && snapshot.accounts != null)
				{
					accounts.putAll(snapshot.accounts);
					log.info("Dashboard loaded from {}. {} accounts restored.",
						dataFilePath, accounts.size());
				}
			}
			catch (IOException | JsonSyntaxException e)
			{
				log.error("Failed to load dashboard from {}", dataFilePath, e);
			}
		}
	}

	// [Inner Classes]

	
	@Data
	@Builder
	@AllArgsConstructor
	public static class Account
	{
		private String accountName;

		private long cashStack;

		private long totalBankValue;

		@Builder.Default
		private Map<Integer, AccountPosition> positions = new ConcurrentHashMap<>();

		@Builder.Default
		private Map<Integer, Instant> geLimitResets = new ConcurrentHashMap<>();

		private int totalGeSlotsUsed;

		private boolean isMembers;

		private Instant lastActive;
	}

	
	@Data
	@Builder
	@AllArgsConstructor
	public static class AccountPosition
	{
		private int itemId;

		private String itemName;

		private long buyPrice;

		private int quantity;

		private Instant buyTime;

		private long currentPrice;

		private double unrealizedPnL;

		private PositionState state;

		public enum PositionState
		{
			BUYING,
			HOLDING,
			SELLING
		}
	}

	
	@Slf4j
	public static class PortfolioRebalancer
	{
		private final MultiAccountDashboard dashboard;

		public PortfolioRebalancer(MultiAccountDashboard dashboard)
		{
			this.dashboard = dashboard;
		}

		
		public List<String> getDiversificationWarnings()
		{
			List<String> warnings = new ArrayList<>();

			for (Account account : dashboard.accounts.values())
			{
				// Check concentration in single item (per-account only)
				Map<Integer, Long> itemValues = new HashMap<>();
				long totalValue = 0;

				for (AccountPosition pos : account.getPositions().values())
				{
					long value = pos.getCurrentPrice() * pos.getQuantity();
					itemValues.put(pos.getItemId(), value);
					totalValue += value;
				}

				if (totalValue > 0)
				{
					for (Map.Entry<Integer, Long> entry : itemValues.entrySet())
					{
						double percentage = (double) entry.getValue() / totalValue * 100;
						if (percentage > 60)
						{
							warnings.add(String.format(
								"%s is %.1f%% concentrated in item ID %d.",
								account.getAccountName(), percentage, entry.getKey()));
						}
					}
				}

				// Check GE slot usage
				if (account.getTotalGeSlotsUsed() >= 7)
				{
					warnings.add(String.format(
						"%s is using %d/8 GE slots. Limit approaching.",
						account.getAccountName(), account.getTotalGeSlotsUsed()));
				}
			}

			// NOTE: Cross-account arbitrage detection was removed for Plugin Hub compliance.
			// Suggesting trades between accounts could be seen as facilitating coordinated
			// market manipulation.

			return warnings;
		}
	}

	
	@Data
	@Builder
	public static class PortfolioHeatmap
	{
		private Map<Integer, ItemDistribution> itemDistribution;

		private Map<String, List<Integer>> accountHoldings;

		private List<String> concentrationRisks;
	}

	
	@Data
	@Builder
	public static class ItemDistribution
	{
		private int itemId;
		private String itemName;
		@Builder.Default
		private Set<String> accountsHolding = new HashSet<>();
	}

	
	@Data
	@Builder
	public static class AggregateStats
	{
		private long totalCash;
		private long totalBankValue;
		private long totalCapitalInvested;
		private long totalExposure;
		private double totalUnrealizedPnL;
		private int totalAccounts;
		private long averageGPPerHour;
	}

	
	@Data
	public static class DashboardSnapshot
	{
		public Map<String, Account> accounts;

		public DashboardSnapshot(Map<String, Account> accounts)
		{
			this.accounts = accounts;
		}
	}
}
