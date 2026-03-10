package com.fliphelper.tracker;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// Tracks volatile items and their historical dump/recovery patterns.
@Slf4j
public class DumpKnowledgeEngine
{
	private final Map<Integer, DumpProfile> knownDumpItems;
	private final PortfolioManager portfolioManager;
	private final DumpRecoveryTracker recoveryTracker;
	private final DumpRuleEngine ruleEngine;

	public DumpKnowledgeEngine()
	{
		this.knownDumpItems = initializeKnownDumpProfiles();
		this.portfolioManager = new PortfolioManager();
		this.recoveryTracker = new DumpRecoveryTracker();
		this.ruleEngine = new DumpRuleEngine();
		log.info("DumpKnowledgeEngine initialized with {} known volatile items", knownDumpItems.size());
	}

	private Map<Integer, DumpProfile> initializeKnownDumpProfiles()
	{
		Map<Integer, DumpProfile> profiles = new ConcurrentHashMap<>();

		// High-value uniques - typically 20-40% dumps on release/meta changes
		profiles.put(11865, DumpProfile.builder()
			.itemId(11865)
			.itemName("Twisted Bow")
			.category("Boss Unique - High Tier")
			.typicalDumpPercentage(25)
			.recoveryTimeHours(48)
			.recoveryPattern(RecoveryPattern.V_SHAPE)
			.buyInThreshold(20)
			.volumeCharacteristics("Very High - Panic selling during crashes")
			.build());

		// Claws always has decent volume, very liquid
		profiles.put(13652, DumpProfile.builder()
			.itemId(13652)
			.itemName("Dragon Claws")
			.category("Boss Unique - High Demand")
			.typicalDumpPercentage(20)
			.recoveryTimeHours(36)
			.recoveryPattern(RecoveryPattern.V_SHAPE)
			.buyInThreshold(15)
			.volumeCharacteristics("Very High - Common flipping target")
			.build());

		profiles.put(11802, DumpProfile.builder()
			.itemId(11802)
			.itemName("Bandos Chestplate")
			.category("Boss Armour")
			.typicalDumpPercentage(15)
			.recoveryTimeHours(24)
			.recoveryPattern(RecoveryPattern.V_SHAPE)
			.buyInThreshold(12)
			.volumeCharacteristics("High - Consistent demand")
			.build());

		profiles.put(11804, DumpProfile.builder()
			.itemId(11804)
			.itemName("Bandos Tassets")
			.category("Boss Armour")
			.typicalDumpPercentage(15)
			.recoveryTimeHours(24)
			.recoveryPattern(RecoveryPattern.V_SHAPE)
			.buyInThreshold(12)
			.volumeCharacteristics("High - Consistent demand")
			.build());

		profiles.put(12954, DumpProfile.builder()
			.itemId(12954)
			.itemName("Spirit Shield")
			.category("Boss Unique - Tank Gear")
			.typicalDumpPercentage(18)
			.recoveryTimeHours(30)
			.recoveryPattern(RecoveryPattern.V_SHAPE)
			.buyInThreshold(14)
			.volumeCharacteristics("Moderate-High - PvM demand")
			.build());

		profiles.put(12932, DumpProfile.builder()
			.itemId(12932)
			.itemName("Ancestral Robe Top")
			.category("Raid Unique")
			.typicalDumpPercentage(22)
			.recoveryTimeHours(40)
			.recoveryPattern(RecoveryPattern.V_SHAPE)
			.buyInThreshold(18)
			.volumeCharacteristics("High - BiS melee gear")
			.build());

		profiles.put(11828, DumpProfile.builder()
			.itemId(11828)
			.itemName("Armadyl Chestplate")
			.category("Boss Armour")
			.typicalDumpPercentage(16)
			.recoveryTimeHours(28)
			.recoveryPattern(RecoveryPattern.V_SHAPE)
			.buyInThreshold(13)
			.volumeCharacteristics("Moderate-High - Ranging BiS")
			.build());

		profiles.put(21777, DumpProfile.builder()
			.itemId(21777)
			.itemName("Inquisitor's Mace")
			.category("Boss Unique")
			.typicalDumpPercentage(28)
			.recoveryTimeHours(48)
			.recoveryPattern(RecoveryPattern.V_SHAPE)
			.buyInThreshold(22)
			.volumeCharacteristics("High - Limited supply")
			.build());

		profiles.put(12695, DumpProfile.builder()
			.itemId(12695)
			.itemName("Scythe of Vitur")
			.category("Boss Unique - Expensive")
			.typicalDumpPercentage(30)
			.recoveryTimeHours(60)
			.recoveryPattern(RecoveryPattern.V_SHAPE)
			.buyInThreshold(25)
			.volumeCharacteristics("Moderate - Limited traders")
			.build());

		profiles.put(27277, DumpProfile.builder()
			.itemId(27277)
			.itemName("Tumeken's Shadow")
			.category("Boss Unique")
			.typicalDumpPercentage(32)
			.recoveryTimeHours(72)
			.recoveryPattern(RecoveryPattern.V_SHAPE)
			.buyInThreshold(28)
			.volumeCharacteristics("Moderate-High - New content volatile")
			.build());

		// Mid-value supplies and alchables - typically 8-20% dumps
		profiles.put(561, DumpProfile.builder()
			.itemId(561)
			.itemName("Coins")
			.category("Currency")
			.typicalDumpPercentage(0)
			.recoveryTimeHours(0)
			.recoveryPattern(RecoveryPattern.STABLE)
			.buyInThreshold(0)
			.volumeCharacteristics("Infinite")
			.build());

		profiles.put(1391, DumpProfile.builder()
			.itemId(1391)
			.itemName("Coal")
			.category("Skilling Supply")
			.typicalDumpPercentage(8)
			.recoveryTimeHours(12)
			.recoveryPattern(RecoveryPattern.GRADUAL)
			.buyInThreshold(6)
			.volumeCharacteristics("Very High - Bulk trading")
			.build());

		profiles.put(1333, DumpProfile.builder()
			.itemId(1333)
			.itemName("Iron Ore")
			.category("Skilling Supply")
			.typicalDumpPercentage(7)
			.recoveryTimeHours(10)
			.recoveryPattern(RecoveryPattern.GRADUAL)
			.buyInThreshold(5)
			.volumeCharacteristics("Very High - Low margins")
			.build());

		profiles.put(227, DumpProfile.builder()
			.itemId(227)
			.itemName("Rune Longsword")
			.category("High-alch Item")
			.typicalDumpPercentage(10)
			.recoveryTimeHours(8)
			.recoveryPattern(RecoveryPattern.GRADUAL)
			.buyInThreshold(8)
			.volumeCharacteristics("High - Alch target")
			.build());

		log.info("Initialized {} DumpProfiles with historical behavior data", profiles.size());
		return profiles;
	}

	
	public DumpAnalysis analyzeDump(PriceAlert alert)
	{
		float dumpPercentage = calculateDumpPercentage(alert);
		boolean isHighVolume = isHighVolume(alert);
		float spread = calculateSpread(alert);

		DumpProfile knownProfile = knownDumpItems.get(alert.getItemId());

		// Apply Rule of 3 validation
		boolean meetsVolumeRule = ruleEngine.validateVolumeRule(alert);
		boolean meetsSpreadRule = ruleEngine.validateSpreadRule(spread);
		boolean meetsCandleRule = ruleEngine.validateConsecutiveCandleRule(alert);

		// Determine confidence and action
		AnalysisConfidence confidence = determineConfidence(
			dumpPercentage, isHighVolume, spread, knownProfile,
			meetsVolumeRule, meetsSpreadRule, meetsCandleRule);

		RecommendedAction action = determineAction(
			dumpPercentage, isHighVolume, spread, confidence, knownProfile);

		long targetBuyPrice = calculateTargetBuyPrice(alert, dumpPercentage, knownProfile);
		long expectedRecoveryPrice = calculateExpectedRecoveryPrice(alert, knownProfile);
		int recoveryHours = estimateRecoveryTime(knownProfile, dumpPercentage);
		RiskLevel riskLevel = determineRiskLevel(dumpPercentage, spread, isHighVolume, knownProfile);

		return DumpAnalysis.builder()
			.itemId(alert.getItemId())
			.itemName(alert.getItemName())
			.dumpPercentage(dumpPercentage)
			.currentPrice(alert.getCurrentPrice())
			.targetBuyPrice(targetBuyPrice)
			.expectedRecoveryPrice(expectedRecoveryPrice)
			.recoveryTimeHours(recoveryHours)
			.confidence(confidence)
			.recommendedAction(action)
			.riskLevel(riskLevel)
			.reasoning(generateReasoning(alert, dumpPercentage, knownProfile, meetsVolumeRule, meetsSpreadRule))
			.timestamp(Instant.now())
			.build();
	}

	
	public MultiAccountPlan generateMultiAccountPlan(List<DumpAnalysis> opportunities)
	{
		List<DumpAnalysis> viableOpportunities = opportunities.stream()
			.filter(a -> a.getRecommendedAction() == RecommendedAction.BUY)
			.collect(Collectors.toList());

		Map<String, List<TradeRecommendation>> accountPlans = new HashMap<>();
		long totalExposure = 0;

		for (AccountPortfolio account : portfolioManager.getAccounts())
		{
			List<TradeRecommendation> accountTrades = new ArrayList<>();
			long remainingCash = account.getCashStack();

			for (DumpAnalysis opportunity : viableOpportunities)
			{
				// Check GE limit cooldown
				if (portfolioManager.isGeLimitCooldownActive(account.getAccountName(), opportunity.getItemId()))
				{
					log.debug("GE limit cooldown active for {} on account {}",
						opportunity.getItemName(), account.getAccountName());
					continue;
				}

				long costPerUnit = opportunity.getTargetBuyPrice();
				long maxUnitsForAccount = Math.min(
					remainingCash / costPerUnit,
					2000); // Conservative GE limit assumption

				if (maxUnitsForAccount > 0)
				{
					TradeRecommendation recommendation = TradeRecommendation.builder()
						.itemId(opportunity.getItemId())
						.itemName(opportunity.getItemName())
						.accountName(account.getAccountName())
						.recommendedQuantity(maxUnitsForAccount)
						.targetPrice(opportunity.getTargetBuyPrice())
						.expectedSellPrice(opportunity.getExpectedRecoveryPrice())
						.geLimitResetAt(portfolioManager.getNextGeLimitReset(account.getAccountName()))
						.build();

					accountTrades.add(recommendation);
					remainingCash -= (costPerUnit * maxUnitsForAccount);
					totalExposure += (costPerUnit * maxUnitsForAccount);
				}
			}

			accountPlans.put(account.getAccountName(), accountTrades);
		}

		return MultiAccountPlan.builder()
			.timestamp(Instant.now())
			.accountPlans(accountPlans)
			.totalCapitalAllocation(totalExposure)
			.opportunitiesIdentified(viableOpportunities.size())
			.riskDistribution(calculateRiskDistribution(accountPlans))
			.build();
	}

	
	public void trackDumpRecovery(int itemId, long entryPrice, long quantity, long expectedRecoveryPrice)
	{
		recoveryTracker.addPosition(
			RecoveryPosition.builder()
				.itemId(itemId)
				.entryPrice(entryPrice)
				.currentPrice(entryPrice)
				.quantity(quantity)
				.expectedRecoveryPrice(expectedRecoveryPrice)
				.entryTime(Instant.now())
				.build());
		log.info("Tracking recovery for item {} purchased at {}", itemId, entryPrice);
	}

	
	public void updateRecoveryTracking(int itemId, long currentPrice)
	{
		recoveryTracker.updatePosition(itemId, currentPrice);

		Optional<RecoveryPosition> position = recoveryTracker.getPosition(itemId);
		if (position.isPresent())
		{
			float recoveryPercent = calculateRecoveryPercentage(
				position.get().getEntryPrice(), currentPrice);

			if (currentPrice >= position.get().getExpectedRecoveryPrice())
			{
				log.info("Recovery target reached for item {} - recovery: {:.2f}%",
					itemId, recoveryPercent);
			}
		}
	}

	
	public Optional<RecoveryStats> getRecoveryStats(int itemId)
	{
		Optional<RecoveryPosition> position = recoveryTracker.getPosition(itemId);
		if (!position.isPresent())
		{
			return Optional.empty();
		}

		RecoveryPosition pos = position.get();
		float recoveryPercent = calculateRecoveryPercentage(pos.getEntryPrice(), pos.getCurrentPrice());
		long projectedRecoveryPrice = pos.getExpectedRecoveryPrice();

		return Optional.of(RecoveryStats.builder()
			.itemId(itemId)
			.entryPrice(pos.getEntryPrice())
			.currentPrice(pos.getCurrentPrice())
			.expectedRecoveryPrice(projectedRecoveryPrice)
			.recoveryPercentage(recoveryPercent)
			.elapsedTime(Duration.between(pos.getEntryTime(), Instant.now()))
			.profitPerUnit(pos.getCurrentPrice() - pos.getEntryPrice())
			.totalProfit(pos.getQuantity() * (pos.getCurrentPrice() - pos.getEntryPrice()))
			.build());
	}

	// Calculation Methods

	private float calculateDumpPercentage(PriceAlert alert)
	{
		if (alert.getPreviousPrice() <= 0)
		{
			return 0;
		}
		return ((alert.getPreviousPrice() - alert.getCurrentPrice()) /
			(float) alert.getPreviousPrice()) * 100;
	}

	private boolean isHighVolume(PriceAlert alert)
	{
		return alert.getVolume() > (alert.getAverageVolume() * 2);
	}

	private float calculateSpread(PriceAlert alert)
	{
		if (alert.getBuyPrice() <= 0 || alert.getSellPrice() <= 0)
		{
			return 0;
		}
		return ((alert.getSellPrice() - alert.getBuyPrice()) /
			(float) alert.getBuyPrice()) * 100;
	}

	private long calculateTargetBuyPrice(PriceAlert alert, float dumpPercentage, DumpProfile profile)
	{
		long targetBuyPrice = alert.getCurrentPrice();

		if (profile != null)
		{
			// Buy at or below the threshold from profile
			int buyThreshold = profile.getBuyInThreshold();
			targetBuyPrice = (long) (alert.getCurrentPrice() * (1 - (buyThreshold / 100f)));
		}
		else
		{
			// Generic formula: wait for additional 5% drop
			targetBuyPrice = (long) (alert.getCurrentPrice() * 0.95f);
		}

		return targetBuyPrice;
	}

	private long calculateExpectedRecoveryPrice(PriceAlert alert, DumpProfile profile)
	{
		if (profile != null)
		{
			// Use historical average as recovery target
			return (long) (alert.getPreviousPrice() * 0.95f);
		}
		else
		{
			// Conservative estimate: 90% of previous price
			return (long) (alert.getPreviousPrice() * 0.90f);
		}
	}

	private int estimateRecoveryTime(DumpProfile profile, float dumpPercentage)
	{
		if (profile == null)
		{
			return 24; // Default 24 hours
		}

		// Larger dumps typically take longer to recover
		if (dumpPercentage > 30)
		{
			return profile.getRecoveryTimeHours() + 24;
		}
		else if (dumpPercentage < 10)
		{
			return Math.max(profile.getRecoveryTimeHours() / 2, 4);
		}

		return profile.getRecoveryTimeHours();
	}

	private float calculateRecoveryPercentage(long entryPrice, long currentPrice)
	{
		if (entryPrice <= 0)
		{
			return 0;
		}
		return ((currentPrice - entryPrice) / (float) entryPrice) * 100;
	}

	private AnalysisConfidence determineConfidence(
		float dumpPercentage, boolean isHighVolume, float spread,
		DumpProfile knownProfile, boolean meetsVolumeRule,
		boolean meetsSpreadRule, boolean meetsCandleRule)
	{
		int confidenceScore = 0;

		// Known item bonus
		if (knownProfile != null)
		{
			confidenceScore += 30;
		}

		// Volume validation
		if (meetsVolumeRule && isHighVolume)
		{
			confidenceScore += 25;
		}

		// Spread validation
		if (meetsSpreadRule)
		{
			confidenceScore += 20;
		}

		// Candle pattern validation
		if (meetsCandleRule)
		{
			confidenceScore += 15;
		}

		// Dump magnitude
		if (dumpPercentage >= 10 && dumpPercentage <= 30)
		{
			confidenceScore += 10;
		}

		if (confidenceScore >= 90)
		{
			return AnalysisConfidence.VERY_HIGH;
		}
		else if (confidenceScore >= 70)
		{
			return AnalysisConfidence.HIGH;
		}
		else if (confidenceScore >= 50)
		{
			return AnalysisConfidence.MODERATE;
		}
		else if (confidenceScore >= 30)
		{
			return AnalysisConfidence.LOW;
		}

		return AnalysisConfidence.VERY_LOW;
	}

	private RecommendedAction determineAction(
		float dumpPercentage, boolean isHighVolume, float spread,
		AnalysisConfidence confidence, DumpProfile profile)
	{
		// Avoid if spread is too high (manipulation indicator)
		if (spread > 15)
		{
			return RecommendedAction.AVOID;
		}

		// Small dump (< 10%) + high volume = likely panic sell = BUY
		if (dumpPercentage < 10 && isHighVolume)
		{
			return RecommendedAction.BUY;
		}

		// Medium dump (10-30%) with high confidence = BUY
		if (dumpPercentage >= 10 && dumpPercentage <= 30)
		{
			if (confidence == AnalysisConfidence.HIGH || confidence == AnalysisConfidence.VERY_HIGH)
			{
				return RecommendedAction.BUY;
			}
			else if (confidence == AnalysisConfidence.MODERATE)
			{
				return RecommendedAction.WAIT;
			}
			else
			{
				return RecommendedAction.AVOID;
			}
		}

		// Large dump (> 30%) with low volume = potential manipulation/permanent decline = AVOID
		if (dumpPercentage > 30 && !isHighVolume)
		{
			return RecommendedAction.AVOID;
		}

		// Large dump (> 30%) with high volume + known recovery profile = BUY
		if (dumpPercentage > 30 && isHighVolume && profile != null)
		{
			if (confidence == AnalysisConfidence.VERY_HIGH)
			{
				return RecommendedAction.BUY;
			}
			else
			{
				return RecommendedAction.WAIT;
			}
		}

		return RecommendedAction.WAIT;
	}

	private RiskLevel determineRiskLevel(float dumpPercentage, float spread,
		boolean isHighVolume, DumpProfile profile)
	{
		// High spread = high risk
		if (spread > 15)
		{
			return RiskLevel.VERY_HIGH;
		}

		// Large dump without known profile = high risk
		if (dumpPercentage > 30 && profile == null)
		{
			return RiskLevel.VERY_HIGH;
		}

		// Large dump with low volume = permanent decline risk
		if (dumpPercentage > 30 && !isHighVolume)
		{
			return RiskLevel.HIGH;
		}

		// Medium dump = moderate risk
		if (dumpPercentage >= 15 && dumpPercentage <= 30)
		{
			return RiskLevel.MODERATE;
		}

		// Small dump with high volume = low risk
		if (dumpPercentage < 15 && isHighVolume)
		{
			return RiskLevel.LOW;
		}

		return RiskLevel.MODERATE;
	}

	private String generateReasoning(PriceAlert alert, float dumpPercentage,
		DumpProfile profile, boolean meetsVolumeRule, boolean meetsSpreadRule)
	{
		StringBuilder reasoning = new StringBuilder();

		if (profile != null)
		{
			reasoning.append(String.format("Known volatile item (%s). ", profile.getCategory()));
		}

		reasoning.append(String.format("Price dump: %.1f%%. ", dumpPercentage));

		if (meetsVolumeRule)
		{
			reasoning.append("Volume spike detected (panic selling indicator). ");
		}

		if (!meetsSpreadRule)
		{
			reasoning.append("Wide spread suggests market uncertainty. ");
		}

		if (profile != null && dumpPercentage <= profile.getTypicalDumpPercentage())
		{
			reasoning.append(String.format("Within typical dump range for this item (%.0f%%). ",
				profile.getTypicalDumpPercentage()));
		}

		return reasoning.toString();
	}

	private Map<String, Float> calculateRiskDistribution(Map<String, List<TradeRecommendation>> plans)
	{
		Map<String, Float> distribution = new HashMap<>();
		long totalCapital = plans.values().stream()
			.flatMap(List::stream)
			.mapToLong(t -> t.getTargetPrice() * t.getRecommendedQuantity())
			.sum();

		for (Map.Entry<String, List<TradeRecommendation>> entry : plans.entrySet())
		{
			long accountCapital = entry.getValue().stream()
				.mapToLong(t -> t.getTargetPrice() * t.getRecommendedQuantity())
				.sum();
			distribution.put(entry.getKey(), (accountCapital / (float) totalCapital) * 100);
		}

		return distribution;
	}

	// Inner Classes

	
	@Data
	@Builder
	public static class DumpProfile
	{
		private int itemId;
		private String itemName;
		private String category;
		private float typicalDumpPercentage;
		private int recoveryTimeHours;
		private RecoveryPattern recoveryPattern;
		private int buyInThreshold;
		private String volumeCharacteristics;
	}

	
	@Data
	@Builder
	public static class DumpAnalysis
	{
		private int itemId;
		private String itemName;
		private float dumpPercentage;
		private long currentPrice;
		private long targetBuyPrice;
		private long expectedRecoveryPrice;
		private int recoveryTimeHours;
		private AnalysisConfidence confidence;
		private RecommendedAction recommendedAction;
		private RiskLevel riskLevel;
		private String reasoning;
		private Instant timestamp;
	}

	
	@Data
	@Builder
	public static class AccountPortfolio
	{
		private String accountName;
		private long cashStack;
		private Map<Integer, PositionInfo> activePositions;
		private Map<Integer, Instant> geLimitCooldowns;
		private long totalExposure;
	}

	
	@Data
	@Builder
	public static class PositionInfo
	{
		private int itemId;
		private String itemName;
		private long entryPrice;
		private long currentPrice;
		private long quantity;
		private Instant entryTime;
	}

	
	@Slf4j
	public static class PortfolioManager
	{
		private final List<AccountPortfolio> accounts;
		private static final Duration GE_LIMIT_COOLDOWN = Duration.ofHours(4);

		public PortfolioManager()
		{
			this.accounts = new ArrayList<>();
		}

		public void addAccount(AccountPortfolio account)
		{
			accounts.add(account);
			log.info("Added account {} to portfolio tracking", account.getAccountName());
		}

		public List<AccountPortfolio> getAccounts()
		{
			return new ArrayList<>(accounts);
		}

		public boolean isGeLimitCooldownActive(String accountName, int itemId)
		{
			for (AccountPortfolio account : accounts)
			{
				if (account.getAccountName().equals(accountName))
				{
					Instant cooldownEnd = account.getGeLimitCooldowns().get(itemId);
					if (cooldownEnd != null)
					{
						return Instant.now().isBefore(cooldownEnd);
					}
					break;
				}
			}
			return false;
		}

		public Instant getNextGeLimitReset(String accountName)
		{
			for (AccountPortfolio account : accounts)
			{
				if (account.getAccountName().equals(accountName))
				{
					// Find the earliest cooldown reset
					return account.getGeLimitCooldowns().values().stream()
						.min(Instant::compareTo)
						.orElse(Instant.now());
				}
			}
			return Instant.now();
		}

		public void recordGETransaction(String accountName, int itemId)
		{
			for (AccountPortfolio account : accounts)
			{
				if (account.getAccountName().equals(accountName))
				{
					account.getGeLimitCooldowns().put(itemId, Instant.now().plus(GE_LIMIT_COOLDOWN));
					log.debug("Recorded GE transaction for {} on account {}", itemId, accountName);
					break;
				}
			}
		}
	}

	
	@Data
	@Builder
	public static class MultiAccountPlan
	{
		private Instant timestamp;
		private Map<String, List<TradeRecommendation>> accountPlans;
		private long totalCapitalAllocation;
		private int opportunitiesIdentified;
		private Map<String, Float> riskDistribution;
	}

	
	@Data
	@Builder
	public static class TradeRecommendation
	{
		private int itemId;
		private String itemName;
		private String accountName;
		private long recommendedQuantity;
		private long targetPrice;
		private long expectedSellPrice;
		private Instant geLimitResetAt;
	}

	
	@Slf4j
	public static class DumpRecoveryTracker
	{
		private final Map<Integer, RecoveryPosition> positions;

		public DumpRecoveryTracker()
		{
			this.positions = new ConcurrentHashMap<>();
		}

		public void addPosition(RecoveryPosition position)
		{
			positions.put(position.getItemId(), position);
		}

		public void updatePosition(int itemId, long currentPrice)
		{
			RecoveryPosition position = positions.get(itemId);
			if (position != null)
			{
				position.setCurrentPrice(currentPrice);
			}
		}

		public Optional<RecoveryPosition> getPosition(int itemId)
		{
			return Optional.ofNullable(positions.get(itemId));
		}

		public void removePosition(int itemId)
		{
			positions.remove(itemId);
		}

		public List<RecoveryPosition> getAllPositions()
		{
			return new ArrayList<>(positions.values());
		}
	}

	
	@Data
	@Builder
	public static class RecoveryPosition
	{
		private int itemId;
		private long entryPrice;
		private long currentPrice;
		private long quantity;
		private long expectedRecoveryPrice;
		private Instant entryTime;
	}

	
	@Data
	@Builder
	public static class RecoveryStats
	{
		private int itemId;
		private long entryPrice;
		private long currentPrice;
		private long expectedRecoveryPrice;
		private float recoveryPercentage;
		private Duration elapsedTime;
		private long profitPerUnit;
		private long totalProfit;
	}

	
	@Slf4j
	public static class DumpRuleEngine
	{
		private static final float VOLUME_MULTIPLIER = 2.0f;
		private static final float SPREAD_THRESHOLD = 0.15f;
		private static final int CANDLE_CONFIRMATION_COUNT = 3;

		
		public boolean validateVolumeRule(PriceAlert alert)
		{
			return alert.getVolume() > (alert.getAverageVolume() * VOLUME_MULTIPLIER);
		}

		
		public boolean validateSpreadRule(float spreadPercent)
		{
			return spreadPercent <= (SPREAD_THRESHOLD * 100);
		}

		
		public boolean validateConsecutiveCandleRule(PriceAlert alert)
		{
			// In production, would check actual candle data
			// For now, use price trend as proxy
			return alert.getPriceMovement() < -2; // At least 2% decline
		}
	}

	
	@Data
	@Builder
	public static class PriceAlert
	{
		private int itemId;
		private String itemName;
		private long currentPrice;
		private long previousPrice;
		private long volume;
		private long averageVolume;
		private long buyPrice;
		private long sellPrice;
		private double priceMovement;
	}

	// Enums

	public enum RecoveryPattern
	{
		V_SHAPE,        // Sharp drop and quick recovery (most common for uniques)
		L_SHAPE,        // Sharp drop then plateau (meta shifts)
		GRADUAL,        // Slow decline and gradual recovery (supplies)
		PERMANENT_DECLINE, // No recovery (dead content)
		STABLE           // Essentially no movement (stable items)
	}

	public enum AnalysisConfidence
	{
		VERY_HIGH,  // 90+ confidence score
		HIGH,       // 70+ confidence score
		MODERATE,   // 50+ confidence score
		LOW,        // 30+ confidence score
		VERY_LOW    // Below 30
	}

	public enum RecommendedAction
	{
		BUY,   // High confidence dump with good recovery prospects
		WAIT,  // Needs more confirmation or better entry point
		AVOID  // Too risky - wide spread, unknown item, or permanent decline
	}

	public enum RiskLevel
	{
		LOW,
		MODERATE,
		HIGH,
		VERY_HIGH
	}
}
