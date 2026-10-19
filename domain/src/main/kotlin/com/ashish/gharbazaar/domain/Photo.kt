package com.ashish.gharbazaar.domain

import org.json.JSONObject

private const val UNREADABLE = "unreadable"

fun analyzeFoodPhoto(imageRef: String, authorized: Boolean, photosJson: String, locale: String): PhotoAnalysis {
    if (!authorized) {
        return failure("A food photo needs permission before it can be analyzed.")
    }
    if (imageRef.isBlank() || imageRef == UNREADABLE || imageRef.startsWith("data:")) {
        return failure("The image could not be read. Choose another photo.")
    }
    val images = JSONObject(photosJson).getJSONObject("images")
    if (!images.has(imageRef)) {
        return failure("The image could not be read. Choose another photo.")
    }
    val sourceVersion = JSONObject(photosJson).getString("sourceVersion")
    val image = images.getJSONObject(imageRef)
    val sufficient = image.getBoolean("sufficientEvidence")
    val foods = image.getJSONArray("foods")
    val detected = (0 until foods.length()).map { index ->
        val food = foods.getJSONObject(index)
        val names = food.getJSONObject("names")
        val portions = food.getJSONObject("portion")
        val grams = if (food.isNull("estimatedGrams")) null else food.getDouble("estimatedGrams")
        val macros = if (sufficient && grams != null) {
            val per100g = food.getJSONObject("per100g")
            Macros(
                proteinGrams = per100g.getDouble("protein_g"),
                fiberGrams = per100g.getDouble("fiber_g"),
                fatGrams = per100g.getDouble("fat_g"),
                carbohydrateGrams = per100g.getDouble("carbohydrate_g"),
                calories = per100g.getDouble("calories"),
            ).scaled(grams / 100.0)
        } else {
            null
        }
        DetectedFood(
            id = food.getString("id"),
            name = names.optString(locale, names.getString("en")),
            portion = portions.optString(locale, portions.getString("en")),
            estimatedGrams = if (sufficient) grams else null,
            exactWeight = false,
            macros = macros,
        )
    }
    val total = detected.mapNotNull { it.macros }.fold(Macros(0.0, 0.0, 0.0, 0.0, 0.0)) { acc, item -> acc.plus(item) }
    val confidence = image.getString("confidence")
    return PhotoAnalysis(
        imageRetained = false,
        confidence = confidence,
        assumptions = "Visible foods only. Source $sourceVersion. A photo cannot provide an exact weight.",
        foods = detected,
        needsConfirmation = true,
        confirmed = false,
        estimate = if (sufficient) NutritionEstimate(
            servingSize = "visible plate",
            macros = total,
            sourceVersion = sourceVersion,
            sourceKind = "photo-estimate",
            assumptions = "Portion grams are estimates from the fixture dataset.",
            confidence = confidence,
            disclaimer = ESTIMATE_DISCLAIMER,
        ) else null,
        message = if (sufficient) {
            "Review the detected foods before saving."
        } else {
            "The photo does not show a reliable portion. Confirm the food and amount before saving."
        },
    )
}

fun correctPortion(analysis: PhotoAnalysis, foodId: String, grams: Double, photosJson: String): PhotoAnalysis {
    val per100g = per100gFor(photosJson, foodId) ?: return analysis
    val foods = analysis.foods.map { food ->
        if (food.id != foodId) food
        else food.copy(
            estimatedGrams = grams,
            portion = "$grams g",
            macros = per100g.scaled(grams / 100.0),
            exactWeight = false,
        )
    }
    val total = foods.mapNotNull { it.macros }.fold(Macros(0.0, 0.0, 0.0, 0.0, 0.0)) { acc, item -> acc.plus(item) }
    val estimate = analysis.estimate?.copy(macros = total) ?: NutritionEstimate(
        servingSize = "corrected portion",
        macros = total,
        sourceVersion = JSONObject(photosJson).getString("sourceVersion"),
        sourceKind = "photo-estimate",
        assumptions = "Portion corrected by the user. Weight is still an estimate.",
        confidence = "low",
        disclaimer = ESTIMATE_DISCLAIMER,
    )
    return analysis.copy(foods = foods, estimate = estimate, needsConfirmation = true, confirmed = false, saved = false)
}

fun confirmPhoto(analysis: PhotoAnalysis): PhotoAnalysis {
    if (analysis.foods.any { it.estimatedGrams == null }) return analysis
    return analysis.copy(confirmed = true, needsConfirmation = false)
}

fun savePhoto(analysis: PhotoAnalysis): PhotoAnalysis {
    if (!analysis.confirmed) return analysis.copy(saved = false)
    return analysis.copy(saved = true, imageRetained = false)
}

fun photoAudit(analysis: PhotoAnalysis): String =
    "confidence=${analysis.confidence};foods=${analysis.foods.joinToString(",") { it.id }};retained=${analysis.imageRetained}"

private fun failure(message: String) = PhotoAnalysis(
    imageRetained = false,
    confidence = "none",
    assumptions = "No nutrition was calculated.",
    foods = emptyList(),
    needsConfirmation = false,
    confirmed = false,
    estimate = null,
    message = message,
)

private fun per100gFor(photosJson: String, foodId: String): Macros? {
    val images = JSONObject(photosJson).getJSONObject("images")
    val keys = images.keys()
    while (keys.hasNext()) {
        val foods = images.getJSONObject(keys.next()).getJSONArray("foods")
        for (index in 0 until foods.length()) {
            val food = foods.getJSONObject(index)
            if (food.getString("id") == foodId && !food.isNull("per100g")) {
                val per100g = food.getJSONObject("per100g")
                return Macros(
                    per100g.getDouble("protein_g"),
                    per100g.getDouble("fiber_g"),
                    per100g.getDouble("fat_g"),
                    per100g.getDouble("carbohydrate_g"),
                    per100g.getDouble("calories"),
                )
            }
        }
    }
    return null
}

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
