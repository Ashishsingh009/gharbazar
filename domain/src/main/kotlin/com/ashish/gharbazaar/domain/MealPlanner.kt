package com.ashish.gharbazaar.domain

private val dietRank = mapOf(
    "vegetarian" to 0,
    "eggetarian" to 1,
    "non_vegetarian" to 2,
)

fun recipeAllowed(recipe: RecipeTemplate, household: Household): Boolean {
    if (household.allergies.intersect(recipe.allergens).isNotEmpty()) return false
    if (household.diet == Diet.JAIN && !recipe.jain && recipe.ingredients.any { it.jainRestricted }) {
        return false
    }
    if (household.diet == Diet.JAIN && recipe.diet != "vegetarian") return false
    val householdRank = when (household.diet) {
        Diet.VEGETARIAN, Diet.JAIN -> 0
        Diet.EGGETARIAN -> 1
        Diet.NON_VEGETARIAN -> 2
    }
    if ((dietRank[recipe.diet] ?: 2) > householdRank) return false
    return recipe.ingredients.none { ingredient ->
        ingredient.id in household.exclusions ||
            ingredient.allergens.intersect(household.allergies).isNotEmpty()
    }
}

fun validateMealPlan(household: Household, plan: MealPlan, book: RecipeBook): PlanOutcome {
    if (plan.days < 7 && plan.note.isNullOrBlank()) {
        return PlanOutcome.Rejected("A shorter plan must explain why it is shorter than seven days.")
    }
    plan.meals.forEach { meal ->
        val recipe = book.recipes.firstOrNull { it.id == meal.recipeId }
        if (recipe != null && !recipeAllowed(recipe, household) && meal.ingredients.any { violates(it, household) }) {
            return PlanOutcome.Rejected("Meal ${meal.name} breaks a household constraint.")
        }
        if (meal.ingredients.any { violates(it, household) }) {
            return PlanOutcome.Rejected("Meal ${meal.name} breaks a household constraint.")
        }
    }
    return PlanOutcome.Ready(plan)
}

private fun violates(ingredient: Ingredient, household: Household): Boolean {
    if (ingredient.id in household.exclusions) return true
    if (ingredient.allergens.intersect(household.allergies).isNotEmpty()) return true
    if (household.diet == Diet.JAIN && ingredient.jainRestricted) return true
    if (household.diet == Diet.VEGETARIAN && ingredient.diet != "vegetarian") return true
    if (household.diet == Diet.JAIN && ingredient.diet != "vegetarian") return true
    if (household.diet == Diet.EGGETARIAN && ingredient.diet == "non_vegetarian") return true
    return false
}

fun generateMealPlan(household: Household, book: RecipeBook): PlanOutcome {
    val allowed = book.recipes.filter { recipeAllowed(it, household) }
    if (allowed.isEmpty() || household.memberCount <= 0) {
        return PlanOutcome.Rejected("No meal satisfies the household constraints.")
    }
    val ordered = allowed.sortedBy { it.costPerServingInr }
    val slots = listOf("lunch", "dinner")
    val built = mutableListOf<Meal>()
    var cost = 0
    for (day in 1..7) {
        for (slot in slots) {
            val recipe = ordered[(built.size) % ordered.size]
            val mealCost = recipe.costPerServingInr * household.memberCount
            if (cost + mealCost > household.weeklyBudgetInr && built.isNotEmpty()) {
                continue
            }
            if (cost + mealCost > household.weeklyBudgetInr && built.isEmpty()) {
                return PlanOutcome.Rejected("No meal satisfies the household constraints.")
            }
            cost += mealCost
            built += mealFrom(recipe, day, slot, mealCost, household, book)
        }
    }
    val days = built.map { it.day }.distinct().size
    val note = if (days < 7) "The budget covers fewer than seven days of meals." else null
    val plan = MealPlan(
        id = "plan-${household.id}",
        days = days,
        meals = built,
        estimatedCostInr = cost,
        budgetInr = household.weeklyBudgetInr,
        note = note,
    )
    return validateMealPlan(household, plan, book)
}

private fun mealFrom(
    recipe: RecipeTemplate,
    day: Int,
    slot: String,
    cost: Int,
    household: Household,
    book: RecipeBook,
): Meal {
    val locale = household.language
    val ingredients = recipe.ingredients.map { it.toIngredient(locale) }
    val template = recipe.substitution
    val substitution = if (template != null && template.replacementId !in household.exclusions) {
        val replacement = book.ingredientTemplate(template.replacementId)
        val replacementAllergens = replacement?.allergens.orEmpty()
        if (replacementAllergens.intersect(household.allergies).isEmpty()) {
            Substitution(
                id = "${recipe.id}-${template.ingredientId}",
                ingredientId = template.ingredientId,
                replacementId = template.replacementId,
                replacementName = template.replacementNames[locale] ?: template.replacementNames.getValue("en"),
                reason = template.reason[locale] ?: template.reason.getValue("en"),
            )
        } else {
            null
        }
    } else {
        null
    }
    return Meal(
        id = "${recipe.id}-$day-$slot",
        day = day,
        slot = slot,
        recipeId = recipe.id,
        name = recipe.names[locale] ?: recipe.names.getValue("en"),
        costInr = cost,
        ingredients = ingredients,
        substitution = substitution,
    )
}

fun applySubstitution(plan: MealPlan, substitutionId: String, accept: Boolean, book: RecipeBook, locale: String): MealPlan {
    val meals = plan.meals.map { meal ->
        val substitution = meal.substitution
        if (substitution == null || substitution.id != substitutionId) {
            meal
        } else if (!accept) {
            meal.copy(substitution = substitution.copy(accepted = false))
        } else {
            val replacement = book.ingredientTemplate(substitution.replacementId)
            val updated = meal.ingredients.map { ingredient ->
                if (ingredient.id != substitution.ingredientId || replacement == null) {
                    ingredient
                } else {
                    replacement.toIngredient(locale, ingredient.quantity)
                }
            }
            meal.copy(ingredients = updated, substitution = substitution.copy(accepted = true))
        }
    }
    return plan.copy(meals = meals)
}
