package com.ashish.gharbazaar.domain

enum class Diet {
    VEGETARIAN,
    EGGETARIAN,
    JAIN,
    NON_VEGETARIAN,
}

data class Household(
    val id: String,
    val name: String,
    val memberCount: Int,
    val region: String,
    val weeklyBudgetInr: Int,
    val diet: Diet,
    val allergies: Set<String> = emptySet(),
    val exclusions: Set<String> = emptySet(),
    val language: String = "en",
    val members: List<String> = emptyList(),
)

data class Ingredient(
    val id: String,
    val name: String,
    val quantity: Double,
    val unit: String,
    val diet: String,
    val allergens: Set<String>,
    val jainRestricted: Boolean,
    val aisle: String = "pantry",
)

data class Substitution(
    val id: String,
    val ingredientId: String,
    val replacementId: String,
    val replacementName: String,
    val reason: String,
    val accepted: Boolean? = null,
)

data class Meal(
    val id: String,
    val day: Int,
    val slot: String,
    val recipeId: String,
    val name: String,
    val costInr: Int,
    val ingredients: List<Ingredient>,
    val substitution: Substitution? = null,
)

data class MealPlan(
    val id: String,
    val days: Int,
    val meals: List<Meal>,
    val estimatedCostInr: Int,
    val budgetInr: Int,
    val currency: String = "INR",
    val note: String? = null,
)

sealed class PlanOutcome {
    data class Ready(val plan: MealPlan) : PlanOutcome()
    data class Rejected(val message: String) : PlanOutcome()
}

data class GroceryItem(
    val id: String,
    val name: String,
    val quantity: Double,
    val unit: String,
    val aisle: String,
    val purchased: Boolean = false,
    val note: String = "",
)

data class GroceryList(
    val id: String,
    val items: List<GroceryItem>,
    val servingsNote: String? = null,
)

data class Macros(
    val proteinGrams: Double,
    val fiberGrams: Double,
    val fatGrams: Double,
    val carbohydrateGrams: Double,
    val calories: Double,
)

data class NutritionEstimate(
    val servingSize: String,
    val macros: Macros,
    val sourceVersion: String,
    val sourceKind: String,
    val assumptions: String,
    val confidence: String,
    val disclaimer: String,
)

enum class RecipeKind { READY, CLARIFY, MEDICAL }

data class RecipeAnswer(
    val kind: RecipeKind,
    val recipeName: String? = null,
    val servings: Int? = null,
    val ingredients: List<Ingredient> = emptyList(),
    val estimate: NutritionEstimate? = null,
    val targetProteinGrams: Double? = null,
    val differenceGrams: Double? = null,
    val adjusted: Boolean = false,
    val explanation: String,
    val disclaimer: String,
)

data class DetectedFood(
    val id: String,
    val name: String,
    val portion: String,
    val estimatedGrams: Double?,
    val exactWeight: Boolean,
    val macros: Macros?,
)

data class PhotoAnalysis(
    val imageRetained: Boolean,
    val confidence: String,
    val assumptions: String,
    val foods: List<DetectedFood>,
    val needsConfirmation: Boolean,
    val confirmed: Boolean,
    val estimate: NutritionEstimate?,
    val message: String,
    val saved: Boolean = false,
)

data class StoredProfile(
    val household: Household,
    val installationId: String,
)

interface HouseholdStore {
    fun save(profile: StoredProfile)
    fun load(): StoredProfile?
}

class MemoryHouseholdStore : HouseholdStore {
    private var profile: StoredProfile? = null
    override fun save(profile: StoredProfile) {
        this.profile = profile
    }
    override fun load(): StoredProfile? = profile
}

interface AccountLink {
    fun link(installationId: String, accountId: String): LinkResult
}

sealed class LinkResult {
    data object Unavailable : LinkResult()
    data class Linked(val installationId: String) : LinkResult()
}

class UnconfiguredAccountLink : AccountLink {
    override fun link(installationId: String, accountId: String): LinkResult = LinkResult.Unavailable
}
