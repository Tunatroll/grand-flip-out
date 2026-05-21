package com.fliphelper.tracker;

import com.fliphelper.tracker.FlipSuggestionEngine;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SlotOptimizer {

    private static final int TOTAL_GE_SLOTS = 8;
    private final Map<Integer, SlotState> slotStates;

    public SlotOptimizer() {
        this.slotStates = new ConcurrentHashMap<>();
        initializeSlots();
    }

    /**
     * Initialize all 8 GE slots as empty
     */
    private void initializeSlots() {
        for (int i = 0; i < TOTAL_GE_SLOTS; i++) {
            slotStates.put(i, SlotState.builder()
                    .slotNumber(i)
                    .state(SlotState.State.EMPTY)
                    .build());
        }
    }

    /**
     * Get the states of all GE slots
     */
    public Map<Integer, SlotState> getSlotStates() {
        return new ConcurrentHashMap<>(slotStates);
    }

    /**
     * Update a slot's state
     */
    public void updateSlot(int slot, SlotState state) {
        if (slot < 0 || slot >= TOTAL_GE_SLOTS) {
            log.warn("Invalid slot number: {}", slot);
            return;
        }
        state.slotNumber = slot;
        slotStates.put(slot, state);
        log.debug("Updated slot {} to state: {}", slot, state.state);
    }

    /**
     * Suggest optimal slot allocation based on flip suggestions and available capital
     * Uses greedy knapsack algorithm - allocates highest profit/capital ratio items first
     */
    public List<SlotAllocation> suggestSlotAllocation(
            List<FlipSuggestionEngine.FlipSuggestion> suggestions,
            long totalCapital) {

        List<SlotAllocation> allocations = new ArrayList<>();

        if (suggestions == null || suggestions.isEmpty()) {
            log.warn("No flip suggestions provided for slot allocation");
            return allocations;
        }

        // Calculate profit/capital ratio and sort by efficiency
        List<SuggestionWithRatio> ranked = new ArrayList<>();
        for (FlipSuggestionEngine.FlipSuggestion suggestion : suggestions) {
            if (suggestion.getItemName() != null && suggestion.getCapitalRequired() > 0) {
                double ratio = (double) suggestion.getProfitPerLimit() / suggestion.getCapitalRequired();
                ranked.add(new SuggestionWithRatio(suggestion, ratio));
            }
        }

        ranked.sort((a, b) -> Double.compare(b.ratio, a.ratio));

        long remainingCapital = totalCapital;
        int slotIndex = 0;

        // Greedy allocation - fit items in slots by profitability
        for (SuggestionWithRatio item : ranked) {
            if (slotIndex >= TOTAL_GE_SLOTS) {
                log.debug("All slots filled");
                break;
            }

            if (item.suggestion.getCapitalRequired() <= remainingCapital) {
                SlotAllocation allocation = SlotAllocation.builder()
                        .slotNumber(slotIndex)
                        .itemId(item.suggestion.getItemId())
                        .itemName(item.suggestion.getItemName())
                        .capitalRequired(item.suggestion.getCapitalRequired())
                        .expectedProfit(item.suggestion.getProfitPerLimit())
                        .profitCapitalRatio(item.ratio)
                        .suggestion(item.suggestion)
                        .build();

                allocations.add(allocation);
                remainingCapital -= item.suggestion.getCapitalRequired();
                slotIndex++;

                log.debug("Slot {} allocated to {} with capital requirement {}",
                        slotIndex - 1, item.suggestion.getItemName(),
                        item.suggestion.getCapitalRequired());
            }
        }

        log.info("Suggested allocation for {} slots with {} remaining capital",
                allocations.size(), remainingCapital);

        return allocations;
    }

    /**
     * Get the count of non-empty slots
     */
    public int getActiveSlotCount() {
        return (int) slotStates.values().stream()
                .filter(state -> state.state != SlotState.State.EMPTY)
                .count();
    }

    /**
     * Get total capital currently in use across all slots
     */
    public long getTotalCapitalInUse() {
        return slotStates.values().stream()
                .filter(state -> state.state == SlotState.State.BUYING || state.state == SlotState.State.BOUGHT)
                .mapToLong(state -> state.price * state.quantity)
                .sum();
    }

    /**
     * Get expected hourly profit across all active slots
     */
    public long getExpectedHourlyProfit() {
        return slotStates.values().stream()
                .filter(state -> state.state != SlotState.State.EMPTY)
                .mapToLong(state -> state.expectedHourlyProfit)
                .sum();
    }

    /**
     * Get a list of available empty slots
     */
    public List<Integer> getAvailableSlots() {
        List<Integer> available = new ArrayList<>();
        slotStates.values().stream()
                .filter(state -> state.state == SlotState.State.EMPTY)
                .forEach(state -> available.add(state.slotNumber));
        return available;
    }

    /**
     * Inner class representing the state of a GE slot
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class SlotState {
        private int slotNumber;
        private int itemId;
        private String itemName;
        private State state;
        private long price;
        private long quantity;
        private Instant startTime;
        private long expectedHourlyProfit;

        public enum State {
            EMPTY,
            BUYING,
            BOUGHT,
            SELLING,
            SOLD
        }

        public SlotState() {
            this.state = State.EMPTY;
            this.startTime = Instant.now();
            this.expectedHourlyProfit = 0;
        }
    }

    /**
     * Result of slot allocation suggestion
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class SlotAllocation {
        private int slotNumber;
        private int itemId;
        private String itemName;
        private long capitalRequired;
        private long expectedProfit;
        private double profitCapitalRatio;
        private FlipSuggestionEngine.FlipSuggestion suggestion;
    }

    /**
     * Helper class for ranking suggestions
     */
    private static class SuggestionWithRatio {
        final FlipSuggestionEngine.FlipSuggestion suggestion;
        final double ratio;

        SuggestionWithRatio(FlipSuggestionEngine.FlipSuggestion suggestion, double ratio) {
            this.suggestion = suggestion;
            this.ratio = ratio;
        }
    }
}
