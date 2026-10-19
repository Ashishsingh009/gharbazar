package com.ashish.gharbazaar.localization

import android.content.Context
import com.ashish.gharbazaar.domain.LanguagePack
import com.ashish.gharbazaar.domain.RecipeBook
import com.ashish.gharbazaar.domain.parseLanguagePack

class ContractAssets(private val context: Context) {
    fun text(path: String): String = context.assets.open(path).bufferedReader().use { it.readText() }

    fun recipeBook(): RecipeBook = RecipeBook.parse(text("nutrition/recipes.json"))

    fun catalog(): String = text("catalog.json")

    fun photos(): String = text("photos/analyses.json")

    fun pack(locale: String): LanguagePack = parseLanguagePack(text("languages/$locale.json"))
}
