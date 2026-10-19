package com.ashish.gharbazaar.shell

import android.content.Context
import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.model.processor.processInput
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.a2ui.A2uiSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ashish.gharbazaar.a2ui.catalog.gharBazaarCatalog
import com.ashish.gharbazaar.a2ui.catalog.materialBasicCatalog
import com.ashish.gharbazaar.a2ui.runtime.AllowlistInterceptor
import com.ashish.gharbazaar.data.local.LocalRepository
import com.ashish.gharbazaar.domain.CatalogGate
import com.ashish.gharbazaar.domain.ESTIMATE_DISCLAIMER
import com.ashish.gharbazaar.domain.GateResult
import com.ashish.gharbazaar.domain.GroceryList
import com.ashish.gharbazaar.domain.Household
import com.ashish.gharbazaar.domain.LanguagePack
import com.ashish.gharbazaar.domain.MealPlan
import com.ashish.gharbazaar.domain.MetricLog
import com.ashish.gharbazaar.domain.PhotoAnalysis
import com.ashish.gharbazaar.domain.PlanOutcome
import com.ashish.gharbazaar.domain.RecipeAnswer
import com.ashish.gharbazaar.domain.RecipeBook
import com.ashish.gharbazaar.domain.StoredProfile
import com.ashish.gharbazaar.domain.SyncOperation
import com.ashish.gharbazaar.domain.analyzeFoodPhoto
import com.ashish.gharbazaar.domain.answerRecipe
import com.ashish.gharbazaar.domain.applySubstitution
import com.ashish.gharbazaar.domain.confirmPhoto
import com.ashish.gharbazaar.domain.correctPortion
import com.ashish.gharbazaar.domain.editGroceryItem
import com.ashish.gharbazaar.domain.generateMealPlan
import com.ashish.gharbazaar.domain.groceryFromIngredients
import com.ashish.gharbazaar.domain.groceryFromMeals
import com.ashish.gharbazaar.domain.photoAudit
import com.ashish.gharbazaar.domain.projectMealPlan
import com.ashish.gharbazaar.domain.savePhoto
import com.ashish.gharbazaar.feature.grocery.GroceryScreen
import com.ashish.gharbazaar.feature.mealplan.MealPlanScreen
import com.ashish.gharbazaar.feature.mealplan.PhotoScreen
import com.ashish.gharbazaar.feature.mealplan.RecipeScreen
import com.ashish.gharbazaar.feature.onboarding.OnboardingScreen
import com.ashish.gharbazaar.localization.ContractAssets
import com.ashish.gharbazaar.sync.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppGraph(context: Context) {
    val appContext = context.applicationContext
    val repository = LocalRepository(appContext)
    val assets = ContractAssets(appContext)
    val book: RecipeBook = assets.recipeBook()
    val catalogJson: String = assets.catalog()
    val photos: String = assets.photos()
    val gate = CatalogGate(catalogJson)
    val metrics = MetricLog()

    fun pack(locale: String): LanguagePack = assets.pack(locale)
}

@Composable
fun GharBazaarShell(graph: AppGraph) {
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    var household by remember { mutableStateOf<Household?>(null) }
    var plan by remember { mutableStateOf<MealPlan?>(null) }
    var grocery by remember { mutableStateOf<GroceryList?>(null) }
    var recipe by remember { mutableStateOf<RecipeAnswer?>(null) }
    var photo by remember { mutableStateOf<PhotoAnalysis?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var locale by remember { mutableStateOf("en") }
    val pack = graph.pack(locale)
    val parser = remember { A2uiMessageParser() }
    val processor = remember {
        A2uiMessageProcessor(
            catalogs = listOf(materialBasicCatalog(), gharBazaarCatalog()),
            interceptors = listOf(AllowlistInterceptor(graph.catalogJson) { household?.id }),
        )
    }
    val surfaces by processor.activeSurfaces.collectAsState()
    LaunchedEffect(processor) {
        withContext(Dispatchers.Default) { processor.collectMessages() }
    }
    LaunchedEffect(Unit) {
        val profile = graph.repository.loadProfile()
        household = profile?.household
        locale = profile?.household?.language ?: graph.repository.language()
        plan = graph.repository.loadPlan()
        grocery = graph.repository.loadGrocery()
        ready = true
    }
    if (!ready) {
        Text(pack.text("app_name"))
        return
    }
    if (household == null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(pack.text("onboarding_language"))
            NavigationBar {
                NavigationBarItem(selected = locale == "en", onClick = { locale = "en" }, label = { Text(pack.text("a11y_language_english")) }, icon = { Text("EN") })
                NavigationBarItem(selected = locale == "hi", onClick = { locale = "hi" }, label = { Text(pack.text("a11y_language_hindi")) }, icon = { Text("हि") })
            }
            OnboardingScreen(graph.pack(locale)) { created ->
                scope.launch {
                    val installationId = graph.repository.installationId()
                    graph.repository.saveProfile(StoredProfile(created, installationId))
                    household = created
                    locale = created.language
                }
            }
        }
        return
    }
    val home = household ?: return
    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("tab_plan", "tab_grocery", "tab_recipe", "tab_photo").forEachIndexed { index, key ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        label = { Text(pack.text(key)) },
                        icon = { Text(pack.text(key).take(1)) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            surfaces.firstOrNull()?.let { surface -> A2uiSurface(surface) }
            status?.let { Text(it) }
            when (tab) {
                0 -> MealPlanScreen(pack, plan, status, onGenerate = {
                    scope.launch {
                        val started = System.currentTimeMillis()
                        when (val outcome = generateMealPlan(home, graph.book)) {
                            is PlanOutcome.Rejected -> status = outcome.message
                            is PlanOutcome.Ready -> {
                                plan = outcome.plan
                                graph.repository.savePlan(outcome.plan)
                                projectMealPlan(outcome.plan).forEach { message ->
                                    when (val gate = graph.gate.accept(message)) {
                                        is GateResult.Rejected -> status = pack.text("unknown_component")
                                        is GateResult.Accepted -> processor.processInput(parser, message)
                                    }
                                }
                                graph.metrics.record("agent_latency", "local", System.currentTimeMillis() - started, mapOf("tool" to "generate_meal_plan"))
                                status = pack.text("sync_saved")
                            }
                        }
                    }
                }, onAccept = { id ->
                    scope.launch { plan = plan?.let { applyAndSave(graph, it, id, true, home.language) } }
                }, onReject = { id ->
                    scope.launch { plan = plan?.let { applyAndSave(graph, it, id, false, home.language) } }
                }, onGrocery = {
                    scope.launch {
                        val list = plan?.let(::groceryFromMeals) ?: return@launch
                        grocery = list
                        graph.repository.saveGrocery(list)
                        graph.repository.enqueue(SyncOperation("grocery-${list.id}", "grocery", 1, System.currentTimeMillis(), "{}"))
                        SyncWorker.schedule(graph.appContext)
                        tab = 1
                    }
                })
                1 -> GroceryScreen(pack, grocery, offline = true, onToggle = { id, purchased ->
                    scope.launch {
                        val updated = grocery?.let { editGroceryItem(it, id, purchased = purchased) } ?: return@launch
                        grocery = updated
                        graph.repository.saveGrocery(updated)
                    }
                })
                2 -> RecipeScreen(pack, recipe, onAsk = { request ->
                    recipe = answerRecipe(request, graph.book, home.language)
                }, onAddGrocery = {
                    val answer = recipe ?: return@RecipeScreen
                    scope.launch {
                        val list = groceryFromIngredients("recipe", answer.ingredients, answer.estimate?.servingSize)
                        grocery = list
                        graph.repository.saveGrocery(list)
                        tab = 1
                    }
                })
                else -> PhotoScreen(pack, photo, onReference = { reference, authorized ->
                    photo = analyzeFoodPhoto(reference, authorized, graph.photos, home.language)
                    graph.metrics.record("photo", "local", 1, mapOf("audit" to photoAudit(photo!!)))
                }, onCorrect = { foodId, grams ->
                    photo = photo?.let { correctPortion(it, foodId, grams, graph.photos) }
                }, onConfirm = {
                    photo = photo?.let { savePhoto(confirmPhoto(it)) }
                    status = ESTIMATE_DISCLAIMER
                })
            }
        }
    }
}

private suspend fun applyAndSave(graph: AppGraph, plan: MealPlan, id: String, accept: Boolean, locale: String): MealPlan {
    val updated = applySubstitution(plan, id, accept, graph.book, locale)
    graph.repository.savePlan(updated)
    return updated
}
