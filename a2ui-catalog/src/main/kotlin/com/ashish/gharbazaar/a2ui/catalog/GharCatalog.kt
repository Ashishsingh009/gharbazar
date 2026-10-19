package com.ashish.gharbazaar.a2ui.catalog

import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiComponentState
import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.compose.runtime.observeA2uiComponentState
import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.a2ui.model.catalog.functions.A2uiLocaleProvider
import androidx.a2ui.model.catalog.functions.A2uiMessageFormatter
import androidx.a2ui.model.catalog.functions.A2uiUrlOpener
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.a2ui.catalog.MaterialA2uiBasicCatalogV1Defaults
import androidx.compose.material3.a2ui.catalog.materialA2uiBasicCatalogV1
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

const val GHAR_CATALOG_ID = "https://gharbazaar.app/a2ui/catalog/v1"

fun materialBasicCatalog(): A2uiCatalog = materialA2uiBasicCatalogV1(
    image = MaterialA2uiBasicCatalogV1Defaults.image { _, _, _, modifier, _ -> Box(modifier) },
    video = MaterialA2uiBasicCatalogV1Defaults.video { _, modifier, _ -> Box(modifier) },
    audioPlayer = MaterialA2uiBasicCatalogV1Defaults.audioPlayer { _, _, modifier, _ -> Box(modifier) },
    urlOpener = A2uiUrlOpener { },
    messageFormatter = A2uiMessageFormatter { pattern, _, _ -> pattern },
    localeProvider = A2uiLocaleProvider.Default,
)

fun gharBazaarCatalog(): A2uiCatalog = A2uiCatalog(
    catalogId = GHAR_CATALOG_ID,
    components = listOf(
        component("HouseholdSummary", "Household size and region", strings = listOf("householdName", "region"), numbers = listOf("memberCount"), children = true),
        component("MealPlanHeader", "Weekly meal plan header", strings = listOf("title", "note"), numbers = listOf("dayCount"), children = true),
        component("BudgetSummary", "Budget in INR", strings = listOf("currency"), numbers = listOf("estimatedInr", "budgetInr")),
        component("MealCard", "One meal", strings = listOf("mealName", "day", "slot"), numbers = listOf("costInr")),
        component("IngredientSwapRow", "Ingredient substitution", strings = listOf("original", "replacement", "reason")),
        component("GroceryListSummary", "Grocery list summary", strings = listOf("title"), numbers = listOf("itemCount"), children = true),
        component("PreferenceSelector", "Preference value", strings = listOf("label", "value")),
        component("AgentStatusMessage", "Agent status", strings = listOf("message", "tone")),
        component("RecipeIngredientList", "Recipe ingredients", strings = listOf("recipeName", "disclaimer"), numbers = listOf("servings"), children = true),
        component("NutritionTargetSummary", "Protein target", strings = listOf("assumptions"), numbers = listOf("proteinGrams", "targetGrams", "differenceGrams")),
        component("PhotoNutritionSummary", "Photo nutrition estimate", strings = listOf("confidence", "assumptions"), booleans = listOf("confirmed")),
        component("MacroBreakdown", "Macro breakdown", numbers = listOf("proteinGrams", "fiberGrams", "fatGrams", "carbohydrateGrams", "calories")),
    ),
)

private fun component(
    componentName: String,
    descriptionText: String,
    strings: List<String> = emptyList(),
    numbers: List<String> = emptyList(),
    booleans: List<String> = emptyList(),
    children: Boolean = false,
): A2uiComponent = object : A2uiComponent {
    private val stringProps = strings.map { A2uiProperty.dynamicString(it) }
    private val numberProps = numbers.map { A2uiProperty.dynamicNumber(it) }
    private val booleanProps = booleans.map { A2uiProperty.dynamicBoolean(it) }
    private val childProperty = A2uiProperty.childList("children")
    override val name: String = componentName
    override val description: String = descriptionText
    override val properties = stringProps + numberProps + booleanProps + if (children) listOf(childProperty) else emptyList()

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean = true

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        Card(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                val lines = stringProps.mapNotNull { properties.bind(it) } +
                    numberProps.mapNotNull { properties.bind(it)?.toString() } +
                    booleanProps.mapNotNull { properties.bind(it)?.toString() }
                lines.forEachIndexed { index, value ->
                    Text(
                        text = value,
                        style = if (index == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics {
                            contentDescription = value
                            if (index == 0) heading()
                        },
                    )
                }
                if (children) {
                    properties.bindChildReferences(childProperty)?.forEach { reference ->
                        val state = observeA2uiComponentState(reference)
                        if (state is A2uiComponentState.Success) {
                            androidx.a2ui.compose.ui.A2uiComponent(state.component)
                        }
                    }
                }
            }
        }
    }
}
