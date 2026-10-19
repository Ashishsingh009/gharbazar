package com.ashish.gharbazaar.domain

import kotlin.math.round

fun groceryFromMeals(plan: MealPlan): GroceryList {
    val grouped = linkedMapOf<String, GroceryItem>()
    plan.meals.forEach { meal ->
        meal.ingredients.forEach { ingredient ->
            val key = "${ingredient.id}|${ingredient.unit}"
            val existing = grouped[key]
            grouped[key] = if (existing == null) {
                GroceryItem(
                    id = key,
                    name = ingredient.name,
                    quantity = ingredient.quantity,
                    unit = ingredient.unit,
                    aisle = ingredient.aisle,
                )
            } else {
                existing.copy(quantity = round1(existing.quantity + ingredient.quantity))
            }
        }
    }
    return GroceryList(
        id = "grocery-${plan.id}",
        items = grouped.values.sortedWith(compareBy({ it.aisle }, { it.name })),
    )
}

fun groceryFromIngredients(id: String, ingredients: List<Ingredient>, servingsNote: String?): GroceryList {
    val plan = MealPlan(
        id = id,
        days = 1,
        meals = listOf(
            Meal(
                id = "recipe",
                day = 1,
                slot = "recipe",
                recipeId = id,
                name = id,
                costInr = 0,
                ingredients = ingredients,
            ),
        ),
        estimatedCostInr = 0,
        budgetInr = 0,
        note = "recipe",
    )
    return groceryFromMeals(plan).copy(id = "grocery-$id", servingsNote = servingsNote)
}

fun editGroceryItem(
    list: GroceryList,
    itemId: String,
    quantity: Double? = null,
    purchased: Boolean? = null,
    note: String? = null,
): GroceryList {
    return list.copy(
        items = list.items.map { item ->
            if (item.id != itemId) item
            else item.copy(
                quantity = quantity ?: item.quantity,
                purchased = purchased ?: item.purchased,
                note = note ?: item.note,
            )
        },
    )
}

private fun round1(value: Double): Double = round(value * 10.0) / 10.0
