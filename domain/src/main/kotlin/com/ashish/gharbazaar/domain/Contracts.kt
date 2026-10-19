package com.ashish.gharbazaar.domain

import org.json.JSONObject
import java.io.File

data class IngredientTemplate(
    val id: String,
    val names: Map<String, String>,
    val quantity: Double,
    val unit: String,
    val diet: String,
    val allergens: Set<String>,
    val jainRestricted: Boolean,
    val per100g: Macros,
)

data class SubstitutionTemplate(
    val ingredientId: String,
    val replacementId: String,
    val replacementNames: Map<String, String>,
    val reason: Map<String, String>,
)

data class RecipeTemplate(
    val id: String,
    val names: Map<String, String>,
    val diet: String,
    val costPerServingInr: Int,
    val allergens: Set<String>,
    val jain: Boolean,
    val ingredients: List<IngredientTemplate>,
    val substitution: SubstitutionTemplate?,
)

data class RecipeBook(
    val sourceVersion: String,
    val materialChangeFactor: Double,
    val recipes: List<RecipeTemplate>,
) {
    fun find(query: String): RecipeTemplate? {
        val normalized = query.lowercase()
        return recipes.firstOrNull { recipe ->
            recipe.id.replace('-', ' ') in normalized ||
                recipe.names.values.any { it.lowercase() in normalized }
        }
    }

    fun ingredientTemplate(id: String): IngredientTemplate? =
        recipes.asSequence().flatMap { it.ingredients.asSequence() }.firstOrNull { it.id == id }

    companion object {
        fun parse(json: String): RecipeBook {
            val root = JSONObject(json)
            val recipes = root.getJSONArray("recipes")
            return RecipeBook(
                sourceVersion = root.getString("sourceVersion"),
                materialChangeFactor = root.getDouble("materialChangeFactor"),
                recipes = (0 until recipes.length()).map { index ->
                    val recipe = recipes.getJSONObject(index)
                    val substitution = recipe.optJSONObject("substitution")?.let {
                        SubstitutionTemplate(
                            ingredientId = it.getString("ingredientId"),
                            replacementId = it.getString("replacementId"),
                            replacementNames = it.getJSONObject("replacementNames").toStringMap(),
                            reason = it.getJSONObject("reason").toStringMap(),
                        )
                    }
                    RecipeTemplate(
                        id = recipe.getString("id"),
                        names = recipe.getJSONObject("names").toStringMap(),
                        diet = recipe.getString("diet"),
                        costPerServingInr = recipe.getInt("costPerServingInr"),
                        allergens = recipe.getJSONArray("allergens").toStringSet(),
                        jain = recipe.optBoolean("jain"),
                        ingredients = recipe.getJSONArray("ingredients").let { array ->
                            (0 until array.length()).map { ingredientIndex ->
                                array.getJSONObject(ingredientIndex).toIngredient()
                            }
                        },
                        substitution = substitution,
                    )
                },
            )
        }

        fun load(contracts: File = contractRoot()): RecipeBook =
            parse(File(contracts, "nutrition/recipes.json").readText())
    }
}

fun contractRoot(): File {
    val configured = System.getenv("GHARBAZAAR_CONTRACTS")
    if (!configured.isNullOrBlank()) {
        return File(configured)
    }
    var cursor = File(System.getProperty("user.dir"))
    while (cursor.parentFile != null) {
        if (File(cursor, "contracts/catalog.json").isFile) return File(cursor, "contracts")
        cursor = cursor.parentFile
    }
    error("contracts directory was not found")
}

private fun JSONObject.toIngredient(): IngredientTemplate {
    val per100g = getJSONObject("per100g")
    return IngredientTemplate(
        id = getString("id"),
        names = getJSONObject("names").toStringMap(),
        quantity = getDouble("quantity"),
        unit = getString("unit"),
        diet = getString("diet"),
        allergens = getJSONArray("allergens").toStringSet(),
        jainRestricted = getBoolean("jainRestricted"),
        per100g = Macros(
            proteinGrams = per100g.getDouble("protein_g"),
            fiberGrams = per100g.getDouble("fiber_g"),
            fatGrams = per100g.getDouble("fat_g"),
            carbohydrateGrams = per100g.getDouble("carbohydrate_g"),
            calories = per100g.getDouble("calories"),
        ),
    )
}

private fun JSONObject.toStringMap(): Map<String, String> =
    keys().asSequence().associateWith { getString(it) }

private fun org.json.JSONArray.toStringSet(): Set<String> =
    (0 until length()).map { getString(it) }.toSet()

fun IngredientTemplate.toIngredient(locale: String, quantity: Double = this.quantity): Ingredient =
    Ingredient(
        id = id,
        name = names[locale] ?: names["en"] ?: id,
        quantity = quantity,
        unit = unit,
        diet = diet,
        allergens = allergens,
        jainRestricted = jainRestricted,
        aisle = aisleFor(id),
    )

fun aisleFor(ingredientId: String): String = when (ingredientId) {
    "spinach", "onion", "tomato", "peas", "carrot", "garlic" -> "produce"
    "paneer", "curd", "butter", "cream", "ghee" -> "dairy"
    "chicken", "egg", "tofu" -> "protein"
    else -> "pantry"
}
