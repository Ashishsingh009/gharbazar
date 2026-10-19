package com.ashish.gharbazaar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DomainTest {
    private val contracts = contractRoot()
    private val book = RecipeBook.load(contracts)
    private val catalog = File(contracts, "catalog.json").readText()
    private val photos = File(contracts, "photos/analyses.json").readText()
    private val english = parseLanguagePack(File(contracts, "languages/en.json").readText())
    private val hindi = parseLanguagePack(File(contracts, "languages/hi.json").readText())

    private fun household(
        diet: Diet = Diet.VEGETARIAN,
        budget: Int = 2500,
        allergies: Set<String> = emptySet(),
        exclusions: Set<String> = emptySet(),
        language: String = "en",
        members: Int = 4,
    ) = Household(
        id = "home",
        name = "Sharma",
        memberCount = members,
        region = "Maharashtra",
        weeklyBudgetInr = budget,
        diet = diet,
        allergies = allergies,
        exclusions = exclusions,
        language = language,
        members = List(members) { "Member ${it + 1}" },
    )

    @Test
    fun test001WeeklyMealPlanRendersSevenDaysInInr() {
        val outcome = generateMealPlan(household(), book) as PlanOutcome.Ready
        assertEquals(7, outcome.plan.days)
        assertEquals("INR", outcome.plan.currency)
        assertTrue(outcome.plan.estimatedCostInr <= 2500)
        assertTrue(outcome.plan.meals.all { meal -> meal.ingredients.none { it.diet == "non_vegetarian" } })
    }

    @Test
    fun test002HardConstraintsFailClosed() {
        val silent = MealPlan(
            id = "bad",
            days = 7,
            meals = listOf(
                Meal(
                    id = "m",
                    day = 1,
                    slot = "lunch",
                    recipeId = "chicken-butter-masala",
                    name = "Chicken butter masala",
                    costInr = 100,
                    ingredients = listOf(
                        Ingredient("chicken", "Chicken", 100.0, "g", "non_vegetarian", emptySet(), false),
                    ),
                ),
            ),
            estimatedCostInr = 100,
            budgetInr = 2500,
            note = "fixture",
        )
        assertTrue(validateMealPlan(household(), silent, book) is PlanOutcome.Rejected)
        assertTrue(generateMealPlan(household(members = 0), book) is PlanOutcome.Rejected)
        val jain = generateMealPlan(household(diet = Diet.JAIN), book) as PlanOutcome.Ready
        assertTrue(jain.plan.meals.all { meal -> meal.ingredients.none { it.jainRestricted } })
    }

    @Test
    fun test003HouseholdProfileRestores() {
        val store = MemoryHouseholdStore()
        val profile = StoredProfile(household(language = "hi"), "install-1")
        store.save(profile)
        val restored = MemoryHouseholdStore().let {
            it.save(store.load()!!)
            it.load()
        }
        assertEquals("hi", restored?.household?.language)
        assertEquals(4, restored?.household?.memberCount)
        assertEquals("install-1", restored?.installationId)
    }

    @Test
    fun test004SubstitutionUpdatesGrocery() {
        val plan = (generateMealPlan(household(), book) as PlanOutcome.Ready).plan
        val substitution = plan.meals.firstNotNullOf { it.substitution }
        val accepted = applySubstitution(plan, substitution.id, true, book, "en")
        val rejected = applySubstitution(plan, substitution.id, false, book, "en")
        assertTrue(accepted.meals.filter { it.substitution?.id == substitution.id }.all { meal ->
            meal.ingredients.none { it.id == substitution.ingredientId }
        })
        assertTrue(rejected.meals.any { meal -> meal.ingredients.any { it.id == substitution.ingredientId } })
        assertFalse(groceryFromMeals(accepted).items.any { it.id.startsWith("paneer|") })
    }

    @Test
    fun test005GroceryConsolidatesEditableItems() {
        val plan = (generateMealPlan(household(), book) as PlanOutcome.Ready).plan
        val list = groceryFromMeals(plan)
        val rice = list.items.filter { it.name == "Rice" }
        assertEquals(1, rice.size)
        assertTrue(rice.first().quantity > 50)
        assertEquals("g", rice.first().unit)
        val edited = editGroceryItem(list, rice.first().id, purchased = true, note = "organic")
        assertEquals("organic", edited.items.first { it.id == rice.first().id }.note)
        assertTrue(edited.items.first { it.id == rice.first().id }.purchased)
    }

    @Test
    fun test006OfflineSyncPreservesDrafts() {
        val store = MemoryHouseholdStore()
        store.save(StoredProfile(household(), "install-1"))
        val plan = (generateMealPlan(household(), book) as PlanOutcome.Ready).plan
        val remote = SyncRemote { operation ->
            when {
                operation.idempotencyKey == "fail" -> RemoteAck(RemoteStatus.FAIL)
                operation.idempotencyKey == "stale" -> RemoteAck(RemoteStatus.STALE)
                else -> RemoteAck(RemoteStatus.ACK, 2)
            }
        }
        val coordinator = SyncCoordinator(remote)
        coordinator.enqueue(SyncOperation("ok", "grocery", 1, 10, "note"))
        coordinator.enqueue(SyncOperation("ok", "grocery", 1, 11, "duplicate"))
        coordinator.enqueue(SyncOperation("fail", "grocery", 1, 12, "draft"))
        val report = coordinator.flush()
        assertEquals(1, report.acknowledged.size)
        assertEquals(1, coordinator.pending().size)
        assertTrue(report.localDraftPreserved)
        assertEquals("draft", coordinator.pending().single().payload)
        assertNotNull(store.load())
    }

    @Test
    fun test007LocalizationPacks() {
        assertTrue(english.inr(2500).contains("2,500"))
        assertTrue(hindi.inr(2500).isNotBlank())
        assertEquals("1 meal", english.meals(1))
        assertEquals("2 meals", english.meals(2))
        assertEquals("ग्राम", hindi.unit("g"))
        assertEquals(english.strings.keys, hindi.strings.keys)
        assertTrue(hindi.text("onboarding_title").length > english.text("onboarding_title").length)
    }

    @Test
    fun test008AccountUpgradeDoesNotDisclose() {
        val store = MemoryHouseholdStore()
        store.save(StoredProfile(household(), "install-1"))
        val result = UnconfiguredAccountLink().link("install-1", "account")
        assertTrue(result is LinkResult.Unavailable)
        assertEquals("Sharma", store.load()?.household?.name)
    }

    @Test
    fun test009ActionSafety() {
        assertFalse(evaluateAction(catalog, "place_order", "install-1", false, 2, 1).allowed)
        assertFalse(evaluateAction(catalog, "delete_everything", "install-1", true, 2, 1).allowed)
        assertFalse(evaluateAction(catalog, "save_meal_plan", null, true, 2, 1).allowed)
        assertFalse(evaluateAction(catalog, "save_meal_plan", "install-1", true, 1, 4).allowed)
        assertTrue(evaluateAction(catalog, "save_meal_plan", "install-1", false, 4, 4).allowed)
        assertTrue(rejectClientTotal(mapOf("clientTotal" to 10)))
    }

    @Test
    fun test010And011CatalogFixtures() {
        val gate = CatalogGate(catalog)
        val valid = org.json.JSONArray(File(contracts, "fixtures/a2ui/meal_plan_en.json").readText())
        for (index in 0 until valid.length()) {
            assertTrue(gate.accept(valid.getJSONObject(index).toString()) is GateResult.Accepted)
        }
        val hindiGate = CatalogGate(catalog)
        val hindi = org.json.JSONArray(File(contracts, "fixtures/a2ui/meal_plan_hi.json").readText())
        for (index in 0 until hindi.length()) {
            assertTrue(hindiGate.accept(hindi.getJSONObject(index).toString()) is GateResult.Accepted)
        }
        val invalid = org.json.JSONObject(File(contracts, "fixtures/a2ui/invalid.json").readText())
        val setup = CatalogGate(catalog)
        assertTrue(setup.accept(valid.getJSONObject(0).toString()) is GateResult.Accepted)
        assertTrue(setup.accept(invalid.getJSONObject("unknownComponent").toString()) is GateResult.Rejected)
        assertTrue(setup.accept(invalid.getJSONObject("unknownProperty").toString()) is GateResult.Rejected)
        assertTrue(setup.accept(invalid.getJSONObject("invalidBinding").toString()) is GateResult.Rejected)
        assertTrue(setup.accept(invalid.getJSONObject("duplicateIds").toString()) is GateResult.Rejected)
        assertTrue(CatalogGate(catalog).accept(invalid.getJSONObject("unsupportedCatalog").toString()) is GateResult.Rejected)
        assertTrue(CatalogGate(catalog).accept(invalid.getString("malformed")) is GateResult.Rejected)
        assertTrue(setup.accept(invalid.getJSONObject("stale").toString()) is GateResult.Rejected)
    }

    @Test
    fun test012PrivacyRedaction() {
        val redacted = redactText("diet=jain token=Bearer abc.def email=a@b.co")
        assertFalse(redacted.contains("abc.def"))
        assertFalse(redacted.contains("a@b.co"))
        val fields = redactFields(mapOf("diet" to "jain", "screen" to "plan", "authorization" to "secret"))
        assertFalse(fields.containsKey("diet"))
        assertFalse(fields.containsKey("authorization"))
    }

    @Test
    fun test013AccessibilityLabels() {
        assertTrue(AccessibilityPolicy.MIN_TOUCH_TARGET_DP >= 48)
        listOf("a11y_generate_plan", "a11y_grocery_item", "a11y_accept_swap").forEach { key ->
            assertTrue(english.text(key).isNotBlank())
            assertTrue(hindi.text(key).isNotBlank())
        }
    }

    @Test
    fun test014ObservabilityWithoutSensitiveContent() {
        val log = MetricLog()
        assertTrue(log.record("agent_latency", "corr-1", 120, mapOf("tool" to "generate_meal_plan")))
        assertFalse(log.record("sync", "corr-1", 10, mapOf("diet" to "jain")))
        assertEquals(1, log.events().size)
        assertEquals("corr-1", log.events().single().correlationId)
    }

    @Test
    fun test015RecipeAsksForServings() {
        val answer = answerRecipe("Give me all the ingredients for chicken butter masala", book, "en")
        assertEquals(RecipeKind.CLARIFY, answer.kind)
        assertEquals("Chicken butter masala", answer.recipeName)
        assertNull(answer.servings)
    }

    @Test
    fun test016ProteinTargetIsTransparent() {
        val answer = answerRecipe(
            "Give me all the ingredients for chicken butter masala; I want 50 grams of protein from one serving.",
            book,
            "en",
        )
        assertEquals(RecipeKind.READY, answer.kind)
        assertEquals(1, answer.servings)
        assertTrue(answer.ingredients.size >= 5)
        assertEquals(book.sourceVersion, answer.estimate?.sourceVersion)
        assertTrue((answer.estimate?.macros?.proteinGrams ?: 0.0) in 49.0..51.0)
        assertNotNull(answer.differenceGrams)
        assertTrue(answer.adjusted)
        assertTrue(answer.explanation.contains(book.sourceVersion))
    }

    @Test
    fun test017MedicalRequestsStayNonClinical() {
        val answer = answerRecipe("chicken butter masala for my diabetes, one serving", book, "en")
        assertEquals(RecipeKind.MEDICAL, answer.kind)
        assertEquals(MEDICAL_DISCLAIMER, answer.disclaimer)
        assertTrue(answer.ingredients.isEmpty())
    }

    @Test
    fun test018RecipeIngredientsBecomeGroceryItems() {
        val answer = answerRecipe(
            "Give me all the ingredients for chicken butter masala for 2 servings",
            book,
            "hi",
        )
        val grocery = groceryFromIngredients("chicken-butter-masala", answer.ingredients, answer.estimate?.servingSize)
        val chicken = grocery.items.first { it.name == "चिकन" }
        assertEquals("g", chicken.unit)
        assertTrue(chicken.quantity >= 200)
        assertEquals("2 serving", grocery.servingsNote)
    }

    @Test
    fun test019UnreadablePhotoRecovers() {
        val denied = analyzeFoodPhoto("fixture:thali", false, photos, "en")
        assertTrue(denied.foods.isEmpty())
        val broken = analyzeFoodPhoto("unreadable", true, photos, "en")
        assertTrue(broken.message.contains("another photo"))
        assertFalse(broken.imageRetained)
    }

    @Test
    fun test020PhotoMacrosIncludeConfidence() {
        val analysis = analyzeFoodPhoto("fixture:thali", true, photos, "en")
        assertEquals("medium", analysis.confidence)
        assertEquals("visible plate", analysis.estimate?.servingSize)
        assertTrue(analysis.foods.all { !it.exactWeight })
        val macros = analysis.estimate!!.macros
        assertTrue(macros.proteinGrams > 0)
        assertTrue(macros.fiberGrams > 0)
        assertTrue(macros.fatGrams > 0)
        assertTrue(macros.carbohydrateGrams > 0)
        assertTrue(macros.calories > 0)
    }

    @Test
    fun test021PhotoCorrectionRequiresConfirmation() {
        val analysis = analyzeFoodPhoto("fixture:ambiguous", true, photos, "en")
        assertNull(analysis.estimate)
        assertTrue(analysis.needsConfirmation)
        val corrected = correctPortion(analysis, "mixed-plate", 200.0, photos)
        assertEquals(200.0, corrected.foods.single().estimatedGrams)
        assertNotNull(corrected.estimate)
        assertFalse(savePhoto(corrected).saved)
        val saved = savePhoto(confirmPhoto(corrected))
        assertTrue(saved.saved)
        assertFalse(saved.imageRetained)
    }

    @Test
    fun test022ImageAuditOmitsTheReference() {
        val analysis = analyzeFoodPhoto("fixture:thali", true, photos, "en")
        val audit = photoAudit(analysis)
        assertFalse(audit.contains("fixture:thali"))
        assertFalse(analysis.imageRetained)
    }

    @Test
    fun verticalSliceSavesAGroceryList() {
        val list = verticalSlice(household(), book)
        assertTrue(list.items.isNotEmpty())
        assertTrue(list.items.none { it.name.equals("Paneer", ignoreCase = true) })
    }
}
