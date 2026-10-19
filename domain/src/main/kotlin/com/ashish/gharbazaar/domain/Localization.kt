package com.ashish.gharbazaar.domain

import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

data class LanguagePack(val locale: String, val strings: Map<String, String>) {
    fun text(key: String): String = strings[key] ?: key

    fun meals(count: Int): String {
        val template = if (count == 1) text("plural_meal_one") else text("plural_meal_other")
        return template.replace("{count}", count.toString())
    }

    fun inr(amount: Int): String {
        val localeTag = if (locale == "hi") Locale.forLanguageTag("hi-IN") else Locale.forLanguageTag("en-IN")
        return NumberFormat.getCurrencyInstance(localeTag).format(amount)
    }

    fun unit(unit: String): String = text("unit_$unit")
}

fun parseLanguagePack(json: String): LanguagePack {
    val root = JSONObject(json)
    val strings = root.getJSONObject("strings")
    return LanguagePack(
        locale = root.getString("locale"),
        strings = strings.keys().asSequence().associateWith { strings.getString(it) },
    )
}

fun projectMealPlan(plan: MealPlan): List<String> {
    val create = org.json.JSONObject()
        .put("version", "v0.9")
        .put("sequence", 1)
        .put(
            "createSurface",
            org.json.JSONObject()
                .put("surfaceId", "meal-plan")
                .put("catalogId", "https://gharbazaar.app/a2ui/catalog/v1"),
        )
    val components = org.json.JSONArray()
    val children = org.json.JSONArray()
    components.put(
        org.json.JSONObject()
            .put("id", "budget")
            .put("component", "BudgetSummary")
            .put("currency", plan.currency)
            .put("estimatedInr", plan.estimatedCostInr)
            .put("budgetInr", plan.budgetInr),
    )
    children.put("budget")
    plan.meals.take(3).forEach { meal ->
        val id = "meal-${meal.id}"
        children.put(id)
        components.put(
            org.json.JSONObject()
                .put("id", id)
                .put("component", "MealCard")
                .put("mealName", meal.name)
                .put("day", "Day ${meal.day}")
                .put("slot", meal.slot)
                .put("costInr", meal.costInr),
        )
    }
    val root = org.json.JSONObject()
        .put("id", "root")
        .put("component", "MealPlanHeader")
        .put("title", "Weekly plan")
        .put("dayCount", plan.days)
        .put("children", children)
    if (plan.note != null) root.put("note", plan.note)
    components.put(root)
    val update = org.json.JSONObject()
        .put("version", "v0.9")
        .put("sequence", 2)
        .put(
            "updateComponents",
            org.json.JSONObject().put("surfaceId", "meal-plan").put("components", components),
        )
    return listOf(create.toString(), update.toString())
}

fun verticalSlice(household: Household, book: RecipeBook): GroceryList {
    val generated = generateMealPlan(household, book)
    val plan = (generated as PlanOutcome.Ready).plan
    val substitution = plan.meals.firstNotNullOfOrNull { it.substitution }
    val updated = if (substitution == null) plan else applySubstitution(plan, substitution.id, true, book, household.language)
    return groceryFromMeals(updated)
}
