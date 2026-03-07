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

/**
 * MultiAccountDashboard for "Grand Flip Out" OSRS GE flipping RuneLite plugin.
 *
 * This dashboard tracks portfolio flipping activities across up to 10 separate accounts
 * for OSRS Grand Exchange item flipping strategies.
 *
 * COMPLIANCE NOTES:
 * - Data stored locally only. No account credentials or RSNs are stored.
 * - Display names are user-provided labels for privacy and clarity.
 * - No automated trading functions. All buying/selling is manual and player-initiated.
 * - Multi-logging is permitted by Jagex rules (allowed since 2014).
 * - Coordinated market manipulation across accounts IS against ToS. This tool assists
 *   independent decision-making per account, not coordinated price manipulation.
 */
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

	/**
	 * Adds a new account to the dashboard. Max 10 accounts.
	 *
	 * @param account The Account to add
	 * @return true if added successfully, false if at capacity
	 */
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

	/**
	 * Removes an account from the dashboard.
	 *
	 * @param accountName The display name of the account to remove
	 * @return true if removed, false if not found
	 */
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

	/**
	 * Retrieves a specific account by display name.
	 *
	 * @param accountName The display name
	 * @return The Account, or null if not found
	 */
	public Account getAccount(String accountName)
	{
		return accounts.get(accountName);
	}

	/**
	 * Returns all tracked accounts.
	 *
	 * @return Unmodifiable collection of accounts
	 */
	public Collection<Account> getAllAccounts()
	{
		return Collections.unmodifiableCollection(accounts.values());
	}

	/**
	 * Calculates aggregate statistics across all accounts.
	 *
	 * @return AggregateStats with portfolio totals
	 */
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

	/**
	 * Determines which accounts can currently purchase a specific item
	 * based on their 4-hour GE limit availability.
	 *
	 * @param itemId The item ID to check
	 * @return Map of account name to remaining GE limit quantity
	 */
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

	/**
	 * Suggests the best account for a purchase based on GE limit availability,
	 * cash on hand, diversification, and active position count.
	 *
	 * @param itemId The item ID to buy
	 * @param price The buy price
	 * @param quantity The desired quantity
	 * @return The recommended Account, or null if no suitable account found
	 */
	public Account suggestAccountForPurchase(int itemId, long price, int quantity)
	{
		Map<String, Integer> availability = getGeLimitAvailability(itemId);

		Account bestAccount = null;
		int bestScore = Integer.MIN_VALUE;

		for (Account account : accounts.values())
		{
			Integer remaining = availability.get(account.getAccountName());
			if (remaining == null || remaining < quantity)
			{
				continue; // Can't buy this quantity
			}

			if (account.getCashStack() < (price * quantity))
			{
				continue; // Insufficient funds
			}

			// Scoring criteria
			int score = 0;

			// Higher score for more remaining GE limit
			score += remaining;

			// Higher score for more available cash (10 points per 1M gp)
			score += (int) (account.getCashStack() / 1_000_000);

			// Penalize for high concentration (fewer active positions is better)
			score -= (account.getPositions().size() * 100);

			if (score > bestScore)
			{
				bestScore = score;
				bestAccount = account;
			}
		}

		if (bestAccount != null)
		{
			log.info("Suggested account for itemId {}: {}", itemId, bestAccount.getAccountName());
		}

		return bestAccount;
	}

	/**
	 * Returns a portfolio heatmap showing item distribution across accounts
	 * and concentration/overlap risks.
	 *
	 * @return PortfolioHeatmap with risk analysis
	 */
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

	/**
	 * Calculates the combined buying power across all accounts for a specific item.
	 * This is the total quantity all accounts could purchase based on remaining GE limits.
	 *
	 * @param itemId The item ID
	 * @return Total remaining GE limit quantity across all accounts
	 */
	public long getCombinedBuyingPower(int itemId)
	{
		Map<String, Integer> availability = getGeLimitAvailability(itemId);
		return availability.values().stream()
			.mapToLong(Integer::longValue)
			.sum();
	}

	/**
	 * Returns a PortfolioRebalancer for analyzing diversification and cross-account arbitrage.
	 */
	public PortfolioRebalancer createRebalancer()
	{
		return new PortfolioRebalancer(this);
	}

	/**
	 * Saves the current dashboard state to JSON file.
	 */
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

	/**
	 * Loads the dashboard state from JSON file.
	 */
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

	// ==================== Inner Classes ====================

	/**
	 * Represents a single account in the multi-account portfolio.
	 *
	 * Display name is a user-provided label for privacy (not the actual RSN).
	 */
	@Data
	@Builder
	@AllArgsConstructor
	public static class Account
	{
		/** User-provided display name (NOT the actual RuneScape name for privacy) */
		private String accountName;

		/** Current cash stack in GP */
		private long cashStack;

		/** Total estimated bank value in GP */
		private long totalBankValue;

		/** Map of item positions (itemId -> AccountPosition) */
		@Builder.Default
		private Map<Integer, AccountPosition> positions = new ConcurrentHashMap<>();

		/** Map of GE limit reset times per item (itemId -> Instant) */
		@Builder.Default
		private Map<Integer, Instant> geLimitResets = new ConcurrentHashMap<>();

		/** Number of GE trading slots currently in use (0-8) */
		private int totalGeSlotsUsed;

		/** Whether this account has members status */
		private boolean isMembers;

		/** Last time this account was active */
		private Instant lastActive;
	}

	/**
	 * Represents a single position (buy order/holding) on an account.
	 */
	@Data
	@Builder
	@AllArgsConstructor
	public static class AccountPosition
	{
		/** GE item ID */
		private int itemId;

		/** Item name */
		private String itemName;

		/** Price paid per unit */
		private long buyPrice;

		/** Quantity held */
		private int quantity;

		/** When this position was opened */
		private Instant buyTime;

		/** Current market price (updated from price service) */
		private long currentPrice;

		/** Unrealized profit/loss in GP */
		private double unrealizedPnL;

		/** Current state of the position */
		private PositionState state;

		public enum PositionState
		{
			BUYING,
			HOLDING,
			SELLING
		}
	}

	/**
	 * Analyzes portfolio for rebalancing opportunities and diversification issues.
	 */
	@Slf4j
	public static class PortfolioRebalancer
	{
		private final MultiAccountDashboard dashboard;

		public PortfolioRebalancer(MultiAccountDashboard dashboard)
		{
			this.dashboard = dashboard;
		}

		/**
		 * Gets rebalancing suggestions for all accounts.
		 *
		 * @return List of suggestion strings
		 */
		public List<String> getRebalanceSuggestions()
		{
			List<String> suggestions = new ArrayList<>();

			for (Account account : dashboard.accounts.values())
			{
				// Check concentration in single item
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
							suggestions.add(String.format(
								"%s is %.1f%% concentrated in item ID %d. Consider diversifying.",
								account.getAccountName(), percentage, entry.getKey()));
						}
					}
				}

				// Check GE slot usage
				if (account.getTotalGeSlotsUsed() >= 7)
				{
					suggestions.add(String.format(
						"%s is using %d/8 GE slots. Limit approaching.",
						account.getAccountName(), account.getTotalGeSlotsUsed()));
				}
			}

			// Check cross-account arbitrage opportunities
			suggestions.addAll(findArbitrageOpportunities());

			return suggestions;
		}

		/**
		 * Detects potential arbitrage: one account holding an item near-sell price
		 * while another could buy near-buy price.
		 */
		private List<String> findArbitrageOpportunities()
		{
			List<String> opportunities = new ArrayList<>();
			Map<Integer, List<AccountPosition>> itemToPositions = new HashMap<>();

			// Map all positions by item ID
			for (Account account : dashboard.accounts.values())
			{
				for (AccountPosition pos : account.getPositions().values())
				{
					itemToPositions.computeIfAbsent(pos.getItemId(), k -> new ArrayList<>())
						.add(pos);
				}
			}

			// Check for arbitrage spreads (one account with high price, another with low)
			for (Map.Entry<Integer, List<AccountPosition>> entry : itemToPositions.entrySet())
			{
				if (entry.getValue().size() > 1)
				{
					List<AccountPosition> positions = entry.getValue();
					long minPrice = positions.stream()
						.mapToLong(AccountPosition::getCurrentPrice)
						.min()
						.orElse(0);
					long maxPrice = positions.stream()
						.mapToLong(AccountPosition::getCurrentPrice)
						.max()
						.orElse(0);

					long spread = maxPrice - minPrice;
					double spreadPercent = (double) spread / minPrice * 100;

					if (spreadPercent > 2) // More than 2% spread
					{
						opportunities.add(String.format(
							"Item ID %d: Potential arbitrage - price spread of %.1f%% (%d - %d gp) across accounts",
							entry.getKey(), spreadPercent, minPrice, maxPrice));
					}
				}
			}

			return opportunities;
		}
	}

	/**
	 * Portfolio heatmap showing concentration and overlap risks.
	 */
	@Data
	@Builder
	public static class PortfolioHeatmap
	{
		/** Item ID -> which accounts hold it and their allocation */
		private Map<Integer, ItemDistribution> itemDistribution;

		/** Account name -> list of item IDs held */
		private Map<String, List<Integer>> accountHoldings;

		/** Concentration risk warnings */
		private List<String> concentrationRisks;
	}

	/**
	 * Shows which accounts hold a specific item.
	 */
	@Data
	@Builder
	public static class ItemDistribution
	{
		private int itemId;
		private String itemName;
		@Builder.Default
		private Set<String> accountsHolding = new HashSet<>();
	}

	/**
	 * Aggregate statistics across all accounts.
	 */
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

	/**
	 * Snapshot for JSON serialization/deserialization.
	 */
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
