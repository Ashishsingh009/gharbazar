package com.ashish.gharbazaar.domain

private val medicalTerms = listOf(
    "diabetes", "cholesterol", "kidney", "cancer", "pregnant", "pregnancy",
    "thyroid", "blood pressure", "prescription", "disease", "diagnose",
)

const val ESTIMATE_DISCLAIMER = "Nutrition values are estimates, not medical advice."
const val MEDICAL_DISCLAIMER = "This is not medical advice. Please consult a qualified professional."

fun answerRecipe(request: String, book: RecipeBook, locale: String): RecipeAnswer {
    val lower = request.lowercase()
    if (medicalTerms.any { it in lower }) {
        return RecipeAnswer(
            kind = RecipeKind.MEDICAL,
            explanation = "A medical condition needs a qualified professional.",
            disclaimer = MEDICAL_DISCLAIMER,
        )
    }
    val recipe = book.find(lower)
        ?: return RecipeAnswer(
            kind = RecipeKind.CLARIFY,
            explanation = "Which recipe should be used?",
            disclaimer = ESTIMATE_DISCLAIMER,
        )
    val servings = parseServings(lower)
    val recipeName = recipe.names[locale] ?: recipe.names.getValue("en")
    if (servings == null) {
        return RecipeAnswer(
            kind = RecipeKind.CLARIFY,
            recipeName = recipeName,
            explanation = "How many servings should this recipe make?",
            disclaimer = ESTIMATE_DISCLAIMER,
        )
    }
    val target = parseProteinTarget(lower)
    val scaled = recipe.ingredients.map { template ->
        template.toIngredient(locale, roundQuantity(template.quantity * servings))
    }
    val baseMacros = sumMacros(scaled, book)
    if (target == null) {
        return ready(recipeName, servings, scaled, baseMacros, book, null, null, false, "Quantities are scaled for $servings serving(s).")
    }
    val proteinIngredient = scaled.firstOrNull { it.id == "chicken" }
        ?: scaled.maxByOrNull { macrosFor(it, book).proteinGrams }
    if (proteinIngredient == null) {
        return ready(recipeName, servings, scaled, baseMacros, book, target, target - baseMacros.proteinGrams, false, "The target cannot be calculated from these ingredients.")
    }
    val others = baseMacros.proteinGrams - macrosFor(proteinIngredient, book).proteinGrams
    val needed = target - others
    val perGram = proteinPerGram(recipe, proteinIngredient.id)
    val cannot = needed <= 0 || perGram <= 0 || (needed / perGram) / proteinIngredient.quantity > book.materialChangeFactor
    if (cannot) {
        return ready(
            recipeName, servings, scaled, baseMacros, book, target,
            baseMacros.proteinGrams - target, false,
            "The target cannot be met without changing the recipe materially.",
        )
    }
    val adjusted = scaled.map { ingredient ->
        if (ingredient.id == proteinIngredient.id) ingredient.copy(quantity = roundQuantity(needed / perGram)) else ingredient
    }
    val adjustedMacros = sumMacros(adjusted, book)
    return ready(
        recipeName, servings, adjusted, adjustedMacros, book, target,
        adjustedMacros.proteinGrams - target, true,
        "Protein-bearing quantity was adjusted using ${book.sourceVersion}.",
    )
}

fun macrosFor(ingredient: Ingredient, book: RecipeBook): Macros {
    val template = book.ingredientTemplate(ingredient.id) ?: return Macros(0.0, 0.0, 0.0, 0.0, 0.0)
    if (ingredient.unit != "g") return Macros(0.0, 0.0, 0.0, 0.0, 0.0)
    return template.per100g.scaled(ingredient.quantity / 100.0)
}

private fun ready(
    recipeName: String,
    servings: Int,
    ingredients: List<Ingredient>,
    macros: Macros,
    book: RecipeBook,
    target: Double?,
    difference: Double?,
    adjusted: Boolean,
    explanation: String,
): RecipeAnswer = RecipeAnswer(
    kind = RecipeKind.READY,
    recipeName = recipeName,
    servings = servings,
    ingredients = ingredients,
    estimate = NutritionEstimate(
        servingSize = "$servings serving",
        macros = macros,
        sourceVersion = book.sourceVersion,
        sourceKind = "calculated-estimate",
        assumptions = "Per-ingredient values from ${book.sourceVersion}, scaled to $servings serving(s).",
        confidence = "medium",
        disclaimer = ESTIMATE_DISCLAIMER,
    ),
    targetProteinGrams = target,
    differenceGrams = difference,
    adjusted = adjusted,
    explanation = explanation,
    disclaimer = ESTIMATE_DISCLAIMER,
)

private fun sumMacros(ingredients: List<Ingredient>, book: RecipeBook): Macros =
    ingredients.fold(Macros(0.0, 0.0, 0.0, 0.0, 0.0)) { acc, ingredient -> acc.plus(macrosFor(ingredient, book)) }

private fun proteinPerGram(recipe: RecipeTemplate, ingredientId: String): Double {
    val template = recipe.ingredients.firstOrNull { it.id == ingredientId } ?: return 0.0
    return if (template.unit == "g") template.per100g.proteinGrams / 100.0 else 0.0
}

private fun parseServings(text: String): Int? {
    if ("one serving" in text || "1 serving" in text || "per serving" in text) return 1
    return Regex("(\\d+)\\s+servings?").find(text)?.groupValues?.get(1)?.toInt()
}

private fun parseProteinTarget(text: String): Double? {
    val match = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:grams|gram|g)\\s+of\\s+protein").find(text)
        ?: Regex("(\\d+(?:\\.\\d+)?)\\s*(?:grams|gram|g)\\s+protein").find(text)
        ?: return null
    return match.groupValues[1].toDouble()
}

private fun roundQuantity(value: Double): Double = kotlin.math.round(value * 10.0) / 10.0

private fun Macros.plus(other: Macros) = Macros(
    proteinGrams + other.proteinGrams,
    fiberGrams + other.fiberGrams,
    fatGrams + other.fatGrams,
    carbohydrateGrams + other.carbohydrateGrams,
    calories + other.calories,
)

private fun Macros.scaled(factor: Double) = Macros(
    proteinGrams * factor,
    fiberGrams * factor,
    fatGrams * factor,
    carbohydrateGrams * factor,
    calories * factor,
)
