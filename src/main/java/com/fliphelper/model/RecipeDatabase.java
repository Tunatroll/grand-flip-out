package com.fliphelper.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.*;
import java.util.stream.Collectors;


public class RecipeDatabase {

    public static final List<Recipe> ALL_RECIPES = Collections.unmodifiableList(initializeRecipes());

    private static List<Recipe> initializeRecipes() {
        List<Recipe> recipes = new ArrayList<>();

        // ==================== HERB CLEANING ====================
        // Grimy guam (199) → Guam leaf (249)
        recipes.add(Recipe.builder()
                .name("Guam leaf")
                .inputs(List.of(new RecipeInput(199, "Grimy guam", 1)))
                .outputs(List.of(new RecipeOutput(249, "Guam leaf", 1)))
                .skill("Herblore")
                .levelRequired(3)
                .category(RecipeCategory.HERB_CLEANING)
                .build());

        // Grimy marrentill (201) → Marrentill (251)
        recipes.add(Recipe.builder()
                .name("Marrentill")
                .inputs(List.of(new RecipeInput(201, "Grimy marrentill", 1)))
                .outputs(List.of(new RecipeOutput(251, "Marrentill", 1)))
                .skill("Herblore")
                .levelRequired(11)
                .category(RecipeCategory.HERB_CLEANING)
                .build());

        // Grimy tarromin (203) → Tarromin (253)
        recipes.add(Recipe.builder()
                .name("Tarromin")
                .inputs(List.of(new RecipeInput(203, "Grimy tarromin", 1)))
                .outputs(List.of(new RecipeOutput(253, "Tarromin", 1)))
                .skill("Herblore")
                .levelRequired(14)
                .category(RecipeCategory.HERB_CLEANING)
                .build());

        // Grimy harralander (205) → Harralander (255)
        recipes.add(Recipe.builder()
                .name("Harralander")
                .inputs(List.of(new RecipeInput(205, "Grimy harralander", 1)))
                .outputs(List.of(new RecipeOutput(255, "Harralander", 1)))
                .skill("Herblore")
                .levelRequired(20)
                .category(RecipeCategory.HERB_CLEANING)
                .build());

        // Grimy ranarr weed (207) → Ranarr weed (257)
        recipes.add(Recipe.builder()
                .name("Ranarr weed")
                .inputs(List.of(new RecipeInput(207, "Grimy ranarr weed", 1)))
                .outputs(List.of(new RecipeOutput(257, "Ranarr weed", 1)))
                .skill("Herblore")
                .levelRequired(25)
                .category(RecipeCategory.HERB_CLEANING)
                .build());

        // Grimy toadflax (3049) → Toadflax (2998)
        recipes.add(Recipe.builder()
                .name("Toadflax")
                .inputs(List.of(new RecipeInput(3049, "Grimy toadflax", 1)))
                .outputs(List.of(new RecipeOutput(2998, "Toadflax", 1)))
                .skill("Herblore")
                .levelRequired(30)
                .category(RecipeCategory.HERB_CLEANING)
                .build());

        // Grimy irit leaf (209) → Irit leaf (259)
        recipes.add(Recipe.builder()
                .name("Irit leaf")
                .inputs(List.of(new RecipeInput(209, "Grimy irit leaf", 1)))
                .outputs(List.of(new RecipeOutput(259, "Irit leaf", 1)))
                .skill("Herblore")
                .levelRequired(40)
                .category(RecipeCategory.HERB_CLEANING)
                .build());

        // Grimy avantoe (211) → Avantoe (261)
        recipes.add(Recipe.builder()
                .name("Avantoe")
                .inputs(List.of(new RecipeInput(211, "Grimy avantoe", 1)))
                .outputs(List.of(new RecipeOutput(261, "Avantoe", 1)))
                .skill("Herblore")
                .levelRequired(48)
                .category(RecipeCategory.HERB_CLEANING)
                .build());

        // Grimy kwuarm (213) → Kwuarm (263)
        recipes.add(Recipe.builder()
                .name("Kwuarm")
                .inputs(List.of(new RecipeInput(213, "Grimy kwuarm", 1)))
                .outputs(List.of(new RecipeOutput(263, "Kwuarm", 1)))
                .skill("Herblore")
                .levelRequired(54)
                .category(RecipeCategory.HERB_CLEANING)
                .build());

        // Grimy snapdragon (3051) → Snapdragon (3000)
        recipes.add(Recipe.builder()
                .name("Snapdragon")
                .inputs(List.of(new RecipeInput(3051, "Grimy snapdragon", 1)))
                .outputs(List.of(new RecipeOutput(3000, "Snapdragon", 1)))
                .skill("Herblore")
                .levelRequired(59)
                .category(RecipeCategory.HERB_CLEANING)
                .build());

        // Grimy cadantine (215) → Cadantine (265)
        recipes.add(Recipe.builder()
                .name("Cadantine")
                .inputs(List.of(new RecipeInput(215, "Grimy cadantine", 1)))
                .outputs(List.of(new RecipeOutput(265, "Cadantine", 1)))
                .skill("Herblore")
                .levelRequired(65)
                .category(RecipeCategory.HERB_CLEANING)
                .build());

        // Grimy lantadyme (2485) → Lantadyme (2481)
        recipes.add(Recipe.builder()
                .name("Lantadyme")
                .inputs(List.of(new RecipeInput(2485, "Grimy lantadyme", 1)))
                .outputs(List.of(new RecipeOutput(2481, "Lantadyme", 1)))
                .skill("Herblore")
                .levelRequired(67)
                .category(RecipeCategory.HERB_CLEANING)
                .build());

        // Grimy dwarf weed (217) → Dwarf weed (267)
        recipes.add(Recipe.builder()
                .name("Dwarf weed")
                .inputs(List.of(new RecipeInput(217, "Grimy dwarf weed", 1)))
                .outputs(List.of(new RecipeOutput(267, "Dwarf weed", 1)))
                .skill("Herblore")
                .levelRequired(70)
                .category(RecipeCategory.HERB_CLEANING)
                .build());

        // Grimy torstol (219) → Torstol (269)
        recipes.add(Recipe.builder()
                .name("Torstol")
                .inputs(List.of(new RecipeInput(219, "Grimy torstol", 1)))
                .outputs(List.of(new RecipeOutput(269, "Torstol", 1)))
                .skill("Herblore")
                .levelRequired(75)
                .category(RecipeCategory.HERB_CLEANING)
                .build());

        // ==================== UNFINISHED POTIONS ====================
        // Guam leaf (249) + Vial of water (227) → Guam potion (unf) (91)
        recipes.add(Recipe.builder()
                .name("Guam potion (unf)")
                .inputs(List.of(
                        new RecipeInput(249, "Guam leaf", 1),
                        new RecipeInput(227, "Vial of water", 1)
                ))
                .outputs(List.of(new RecipeOutput(91, "Guam potion (unf)", 1)))
                .skill("Herblore")
                .levelRequired(3)
                .category(RecipeCategory.HERBLORE_UNFINISHED)
                .build());

        // Ranarr weed (257) + Vial of water (227) → Ranarr potion (unf) (99)
        recipes.add(Recipe.builder()
                .name("Ranarr potion (unf)")
                .inputs(List.of(
                        new RecipeInput(257, "Ranarr weed", 1),
                        new RecipeInput(227, "Vial of water", 1)
                ))
                .outputs(List.of(new RecipeOutput(99, "Ranarr potion (unf)", 1)))
                .skill("Herblore")
                .levelRequired(25)
                .category(RecipeCategory.HERBLORE_UNFINISHED)
                .build());

        // Toadflax (2998) + Vial of water (227) → Toadflax potion (unf) (3002)
        recipes.add(Recipe.builder()
                .name("Toadflax potion (unf)")
                .inputs(List.of(
                        new RecipeInput(2998, "Toadflax", 1),
                        new RecipeInput(227, "Vial of water", 1)
                ))
                .outputs(List.of(new RecipeOutput(3002, "Toadflax potion (unf)", 1)))
                .skill("Herblore")
                .levelRequired(30)
                .category(RecipeCategory.HERBLORE_UNFINISHED)
                .build());

        // Irit leaf (259) + Vial of water (227) → Irit potion (unf) (101)
        recipes.add(Recipe.builder()
                .name("Irit potion (unf)")
                .inputs(List.of(
                        new RecipeInput(259, "Irit leaf", 1),
                        new RecipeInput(227, "Vial of water", 1)
                ))
                .outputs(List.of(new RecipeOutput(101, "Irit potion (unf)", 1)))
                .skill("Herblore")
                .levelRequired(40)
                .category(RecipeCategory.HERBLORE_UNFINISHED)
                .build());

        // Avantoe (261) + Vial of water (227) → Avantoe potion (unf) (103)
        recipes.add(Recipe.builder()
                .name("Avantoe potion (unf)")
                .inputs(List.of(
                        new RecipeInput(261, "Avantoe", 1),
                        new RecipeInput(227, "Vial of water", 1)
                ))
                .outputs(List.of(new RecipeOutput(103, "Avantoe potion (unf)", 1)))
                .skill("Herblore")
                .levelRequired(48)
                .category(RecipeCategory.HERBLORE_UNFINISHED)
                .build());

        // Kwuarm (263) + Vial of water (227) → Kwuarm potion (unf) (105)
        recipes.add(Recipe.builder()
                .name("Kwuarm potion (unf)")
                .inputs(List.of(
                        new RecipeInput(263, "Kwuarm", 1),
                        new RecipeInput(227, "Vial of water", 1)
                ))
                .outputs(List.of(new RecipeOutput(105, "Kwuarm potion (unf)", 1)))
                .skill("Herblore")
                .levelRequired(54)
                .category(RecipeCategory.HERBLORE_UNFINISHED)
                .build());

        // Snapdragon (3000) + Vial of water (227) → Snapdragon potion (unf) (3004)
        recipes.add(Recipe.builder()
                .name("Snapdragon potion (unf)")
                .inputs(List.of(
                        new RecipeInput(3000, "Snapdragon", 1),
                        new RecipeInput(227, "Vial of water", 1)
                ))
                .outputs(List.of(new RecipeOutput(3004, "Snapdragon potion (unf)", 1)))
                .skill("Herblore")
                .levelRequired(59)
                .category(RecipeCategory.HERBLORE_UNFINISHED)
                .build());

        // Cadantine (265) + Vial of water (227) → Cadantine potion (unf) (107)
        recipes.add(Recipe.builder()
                .name("Cadantine potion (unf)")
                .inputs(List.of(
                        new RecipeInput(265, "Cadantine", 1),
                        new RecipeInput(227, "Vial of water", 1)
                ))
                .outputs(List.of(new RecipeOutput(107, "Cadantine potion (unf)", 1)))
                .skill("Herblore")
                .levelRequired(65)
                .category(RecipeCategory.HERBLORE_UNFINISHED)
                .build());

        // Lantadyme (2481) + Vial of water (227) → Lantadyme potion (unf) (2483)
        recipes.add(Recipe.builder()
                .name("Lantadyme potion (unf)")
                .inputs(List.of(
                        new RecipeInput(2481, "Lantadyme", 1),
                        new RecipeInput(227, "Vial of water", 1)
                ))
                .outputs(List.of(new RecipeOutput(2483, "Lantadyme potion (unf)", 1)))
                .skill("Herblore")
                .levelRequired(67)
                .category(RecipeCategory.HERBLORE_UNFINISHED)
                .build());

        // Dwarf weed (267) + Vial of water (227) → Dwarf weed potion (unf) (109)
        recipes.add(Recipe.builder()
                .name("Dwarf weed potion (unf)")
                .inputs(List.of(
                        new RecipeInput(267, "Dwarf weed", 1),
                        new RecipeInput(227, "Vial of water", 1)
                ))
                .outputs(List.of(new RecipeOutput(109, "Dwarf weed potion (unf)", 1)))
                .skill("Herblore")
                .levelRequired(70)
                .category(RecipeCategory.HERBLORE_UNFINISHED)
                .build());

        // Torstol (269) + Vial of water (227) → Torstol potion (unf) (111)
        recipes.add(Recipe.builder()
                .name("Torstol potion (unf)")
                .inputs(List.of(
                        new RecipeInput(269, "Torstol", 1),
                        new RecipeInput(227, "Vial of water", 1)
                ))
                .outputs(List.of(new RecipeOutput(111, "Torstol potion (unf)", 1)))
                .skill("Herblore")
                .levelRequired(75)
                .category(RecipeCategory.HERBLORE_UNFINISHED)
                .build());

        // ==================== GEM CUTTING ====================
        // Uncut sapphire (1623) → Sapphire (1607)
        recipes.add(Recipe.builder()
                .name("Sapphire")
                .inputs(List.of(new RecipeInput(1623, "Uncut sapphire", 1)))
                .outputs(List.of(new RecipeOutput(1607, "Sapphire", 1)))
                .skill("Crafting")
                .levelRequired(20)
                .category(RecipeCategory.GEM_CUTTING)
                .build());

        // Uncut emerald (1621) → Emerald (1605)
        recipes.add(Recipe.builder()
                .name("Emerald")
                .inputs(List.of(new RecipeInput(1621, "Uncut emerald", 1)))
                .outputs(List.of(new RecipeOutput(1605, "Emerald", 1)))
                .skill("Crafting")
                .levelRequired(27)
                .category(RecipeCategory.GEM_CUTTING)
                .build());

        // Uncut ruby (1619) → Ruby (1603)
        recipes.add(Recipe.builder()
                .name("Ruby")
                .inputs(List.of(new RecipeInput(1619, "Uncut ruby", 1)))
                .outputs(List.of(new RecipeOutput(1603, "Ruby", 1)))
                .skill("Crafting")
                .levelRequired(34)
                .category(RecipeCategory.GEM_CUTTING)
                .build());

        // Uncut diamond (1617) → Diamond (1601)
        recipes.add(Recipe.builder()
                .name("Diamond")
                .inputs(List.of(new RecipeInput(1617, "Uncut diamond", 1)))
                .outputs(List.of(new RecipeOutput(1601, "Diamond", 1)))
                .skill("Crafting")
                .levelRequired(43)
                .category(RecipeCategory.GEM_CUTTING)
                .build());

        // Uncut dragonstone (1631) → Dragonstone (1615)
        recipes.add(Recipe.builder()
                .name("Dragonstone")
                .inputs(List.of(new RecipeInput(1631, "Uncut dragonstone", 1)))
                .outputs(List.of(new RecipeOutput(1615, "Dragonstone", 1)))
                .skill("Crafting")
                .levelRequired(55)
                .category(RecipeCategory.GEM_CUTTING)
                .build());

        // Uncut onyx (6571) → Onyx (6573)
        recipes.add(Recipe.builder()
                .name("Onyx")
                .inputs(List.of(new RecipeInput(6571, "Uncut onyx", 1)))
                .outputs(List.of(new RecipeOutput(6573, "Onyx", 1)))
                .skill("Crafting")
                .levelRequired(67)
                .category(RecipeCategory.GEM_CUTTING)
                .build());

        // Uncut zenyte (19496) → Zenyte (19493)
        recipes.add(Recipe.builder()
                .name("Zenyte")
                .inputs(List.of(new RecipeInput(19496, "Uncut zenyte", 1)))
                .outputs(List.of(new RecipeOutput(19493, "Zenyte", 1)))
                .skill("Crafting")
                .levelRequired(78)
                .category(RecipeCategory.GEM_CUTTING)
                .build());

        // ==================== ENCHANTING JEWELRY ====================
        // Sapphire ring (1637) → Ring of recoil (2550)
        recipes.add(Recipe.builder()
                .name("Ring of recoil")
                .inputs(List.of(new RecipeInput(1637, "Sapphire ring", 1)))
                .outputs(List.of(new RecipeOutput(2550, "Ring of recoil", 1)))
                .skill("Magic")
                .levelRequired(34)
                .category(RecipeCategory.ENCHANTING)
                .build());

        // Emerald ring (1639) → Ring of dueling (2552)
        recipes.add(Recipe.builder()
                .name("Ring of dueling")
                .inputs(List.of(new RecipeInput(1639, "Emerald ring", 1)))
                .outputs(List.of(new RecipeOutput(2552, "Ring of dueling", 1)))
                .skill("Magic")
                .levelRequired(40)
                .category(RecipeCategory.ENCHANTING)
                .build());

        // Ruby ring (1641) → Ring of forging (2568)
        recipes.add(Recipe.builder()
                .name("Ring of forging")
                .inputs(List.of(new RecipeInput(1641, "Ruby ring", 1)))
                .outputs(List.of(new RecipeOutput(2568, "Ring of forging", 1)))
                .skill("Magic")
                .levelRequired(49)
                .category(RecipeCategory.ENCHANTING)
                .build());

        // Diamond ring (1643) → Ring of life (2570)
        recipes.add(Recipe.builder()
                .name("Ring of life")
                .inputs(List.of(new RecipeInput(1643, "Diamond ring", 1)))
                .outputs(List.of(new RecipeOutput(2570, "Ring of life", 1)))
                .skill("Magic")
                .levelRequired(57)
                .category(RecipeCategory.ENCHANTING)
                .build());

        // Dragonstone ring (1645) → Ring of wealth (2572)
        recipes.add(Recipe.builder()
                .name("Ring of wealth")
                .inputs(List.of(new RecipeInput(1645, "Dragonstone ring", 1)))
                .outputs(List.of(new RecipeOutput(2572, "Ring of wealth", 1)))
                .skill("Magic")
                .levelRequired(68)
                .category(RecipeCategory.ENCHANTING)
                .build());

        // Sapphire necklace (1656) → Games necklace (3853)
        recipes.add(Recipe.builder()
                .name("Games necklace")
                .inputs(List.of(new RecipeInput(1656, "Sapphire necklace", 1)))
                .outputs(List.of(new RecipeOutput(3853, "Games necklace", 1)))
                .skill("Magic")
                .levelRequired(23)
                .category(RecipeCategory.ENCHANTING)
                .build());

        // Emerald necklace (1658) → Binding necklace (5521)
        recipes.add(Recipe.builder()
                .name("Binding necklace")
                .inputs(List.of(new RecipeInput(1658, "Emerald necklace", 1)))
                .outputs(List.of(new RecipeOutput(5521, "Binding necklace", 1)))
                .skill("Magic")
                .levelRequired(29)
                .category(RecipeCategory.ENCHANTING)
                .build());

        // Diamond necklace (1662) → Phoenix necklace (11090)
        recipes.add(Recipe.builder()
                .name("Phoenix necklace")
                .inputs(List.of(new RecipeInput(1662, "Diamond necklace", 1)))
                .outputs(List.of(new RecipeOutput(11090, "Phoenix necklace", 1)))
                .skill("Magic")
                .levelRequired(40)
                .category(RecipeCategory.ENCHANTING)
                .build());

        // Dragonstone necklace (1664) → Skills necklace (11105)
        recipes.add(Recipe.builder()
                .name("Skills necklace")
                .inputs(List.of(new RecipeInput(1664, "Dragonstone necklace", 1)))
                .outputs(List.of(new RecipeOutput(11105, "Skills necklace", 1)))
                .skill("Magic")
                .levelRequired(72)
                .category(RecipeCategory.ENCHANTING)
                .build());

        // Sapphire amulet (1694) → Amulet of magic (1727)
        recipes.add(Recipe.builder()
                .name("Amulet of magic")
                .inputs(List.of(new RecipeInput(1694, "Sapphire amulet", 1)))
                .outputs(List.of(new RecipeOutput(1727, "Amulet of magic", 1)))
                .skill("Magic")
                .levelRequired(25)
                .category(RecipeCategory.ENCHANTING)
                .build());

        // Emerald amulet (1696) → Amulet of defence (1729)
        recipes.add(Recipe.builder()
                .name("Amulet of defence")
                .inputs(List.of(new RecipeInput(1696, "Emerald amulet", 1)))
                .outputs(List.of(new RecipeOutput(1729, "Amulet of defence", 1)))
                .skill("Magic")
                .levelRequired(31)
                .category(RecipeCategory.ENCHANTING)
                .build());

        // Ruby amulet (1698) → Amulet of strength (1725)
        recipes.add(Recipe.builder()
                .name("Amulet of strength")
                .inputs(List.of(new RecipeInput(1698, "Ruby amulet", 1)))
                .outputs(List.of(new RecipeOutput(1725, "Amulet of strength", 1)))
                .skill("Magic")
                .levelRequired(49)
                .category(RecipeCategory.ENCHANTING)
                .build());

        // Diamond amulet (1700) → Amulet of power (1731)
        recipes.add(Recipe.builder()
                .name("Amulet of power")
                .inputs(List.of(new RecipeInput(1700, "Diamond amulet", 1)))
                .outputs(List.of(new RecipeOutput(1731, "Amulet of power", 1)))
                .skill("Magic")
                .levelRequired(57)
                .category(RecipeCategory.ENCHANTING)
                .build());

        // Dragonstone amulet (1702) → Amulet of glory (1712)
        recipes.add(Recipe.builder()
                .name("Amulet of glory")
                .inputs(List.of(new RecipeInput(1702, "Dragonstone amulet", 1)))
                .outputs(List.of(new RecipeOutput(1712, "Amulet of glory", 1)))
                .skill("Magic")
                .levelRequired(70)
                .category(RecipeCategory.ENCHANTING)
                .build());

        // Onyx amulet (6579) → Amulet of fury (6585)
        recipes.add(Recipe.builder()
                .name("Amulet of fury")
                .inputs(List.of(new RecipeInput(6579, "Onyx amulet", 1)))
                .outputs(List.of(new RecipeOutput(6585, "Amulet of fury", 1)))
                .skill("Magic")
                .levelRequired(80)
                .category(RecipeCategory.ENCHANTING)
                .build());

        // Zenyte amulet (19501) → Amulet of torture (19553)
        recipes.add(Recipe.builder()
                .name("Amulet of torture")
                .inputs(List.of(new RecipeInput(19501, "Zenyte amulet", 1)))
                .outputs(List.of(new RecipeOutput(19553, "Amulet of torture", 1)))
                .skill("Magic")
                .levelRequired(82)
                .category(RecipeCategory.ENCHANTING)
                .build());

        return recipes;
    }

    
    public static List<Recipe> getRecipesByCategory(RecipeCategory category) {
        return ALL_RECIPES.stream()
                .filter(r -> r.getCategory() == category)
                .collect(Collectors.toList());
    }

    
    public static List<Recipe> getRecipesForInput(int itemId) {
        return ALL_RECIPES.stream()
                .filter(r -> r.getInputs().stream()
                        .anyMatch(input -> input.getItemId() == itemId))
                .collect(Collectors.toList());
    }

    
    public static List<Recipe> getRecipesForOutput(int itemId) {
        return ALL_RECIPES.stream()
                .filter(r -> r.getOutputs().stream()
                        .anyMatch(output -> output.getItemId() == itemId))
                .collect(Collectors.toList());
    }

    
    public static Optional<Recipe> getRecipeByName(String name) {
        return ALL_RECIPES.stream()
                .filter(r -> r.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    
    public static Set<RecipeCategory> getAllCategories() {
        return ALL_RECIPES.stream()
                .map(Recipe::getCategory)
                .collect(Collectors.toSet());
    }

    // ==================== DATA CLASSES ====================

    
    @Data
    @Builder
    @AllArgsConstructor
    public static class Recipe {
        private String name;
        private List<RecipeInput> inputs;
        private List<RecipeOutput> outputs;
        private String skill;
        private int levelRequired;
        private RecipeCategory category;
    }

    
    @Data
    @Builder
    @AllArgsConstructor
    public static class RecipeInput {
        private int itemId;
        private String itemName;
        private int quantity;
    }

    
    @Data
    @Builder
    @AllArgsConstructor
    public static class RecipeOutput {
        private int itemId;
        private String itemName;
        private int quantity;
    }

    
    public enum RecipeCategory {
        HERBLORE_UNFINISHED,
        HERBLORE_POTION,
        HERB_CLEANING,
        GEM_CUTTING,
        FLETCHING_BOW,
        FLETCHING_ARROW,
        SMITHING_BAR,
        SMITHING_ITEM,
        ENCHANTING,
        COOKING,
        TANNING,
        SPINNING,
        OTHER
    }
}
