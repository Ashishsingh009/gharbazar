# Ghar Bazaar Technical Specification

Status: Draft architecture contract  
Version: 1.1  
Supports: `intent.md` v1.1

## 1. Architecture

```text
Android App
├── Native Compose Shell
├── A2UI Surface Host
├── Ghar Bazaar A2UI Catalog
├── Domain Layer
├── Room + DataStore
├── WorkManager Sync
└── HTTPS/streaming transport

Cloud Run
├── FastAPI transport adapter
├── Google ADK + Gemini agent
├── Domain tools
├── Firestore repositories
└── Observability and policy checks
```

The Android client is authoritative for presentation safety, local state, and device capabilities. The backend is authoritative for synced domain state, policy validation, and future commerce operations.

## 2. Android modules

The committed application id and namespace are `com.ashish.gharbazaar`. Gradle project names follow the module tree below. Kotlin packages live under `com.ashish.gharbazaar.<module>`.

```text
:app
:shell
:a2ui-catalog
:a2ui-runtime
:feature-onboarding
:feature-mealplan
:feature-grocery
:domain
:data-local
:data-remote
:sync
:localization
```

`:domain` is a plain Kotlin module and does not depend on Android or A2UI. `:app` depends on `:shell`. Feature modules do not depend on `:app`.

Required Android dependencies are pinned in the version catalog at `1.0.0-alpha01` (A2UI Compose, 23 September 2026):

```kotlin
implementation("androidx.a2ui:a2ui-model:1.0.0-alpha01")
implementation("androidx.a2ui:a2ui-engine:1.0.0-alpha01")
implementation("androidx.a2ui.compose:compose-runtime:1.0.0-alpha01")
implementation("androidx.a2ui.compose:compose-ui:1.0.0-alpha01")
implementation("androidx.compose.material3:material3-a2ui:1.0.0-alpha01")
```

Android modules compile against API 37.2 because A2UI `1.0.0-alpha01` requires compileSdk 37.1 or newer. `minSdk` stays 31.

The Ghar Bazaar catalog id is `https://gharbazaar.app/a2ui/catalog/v1`. Transport messages use A2UI protocol `v0.9` and include a numeric `sequence` field. The client discards a message when its sequence is less than or equal to the last applied sequence for that surface. The official parser ignores unknown envelope fields, so `sequence` does not break protocol parsing.

Reference: [Android Agentic UI in Jetpack Compose](https://developer.android.com/develop/ui/compose/agentic)

## 3. A2UI boundary

### A2UI-001 Dynamic meal-plan surface

Supports: `FR-001`, `FR-003`, `FR-004`, `NFR-001`, `NFR-003`

The Android client renders dynamic meal and grocery experiences through a versioned application catalog layered on the Material 3 A2UI catalog.

Catalog components:

- `HouseholdSummary`
- `MealPlanHeader`
- `BudgetSummary`
- `MealCard`
- `IngredientSwapRow`
- `GroceryListSummary`
- `PreferenceSelector`
- `AgentStatusMessage`
- `RecipeIngredientList`
- `NutritionTargetSummary`
- `PhotoNutritionSummary`
- `MacroBreakdown`

Catalog actions:

- `edit_household_preferences`
- `select_meal`
- `accept_substitution`
- `reject_substitution`
- `build_grocery_list`
- `edit_grocery_item`
- `regenerate_meal_plan`
- `save_meal_plan`
- `get_recipe_ingredients`
- `adjust_recipe_to_nutrition_target`
- `analyze_food_photo`
- `confirm_photo_analysis`

Unknown components and unsupported actions are rejected before UI state mutation.

### A2UI-002 Surface lifecycle

Supports: `FR-001`, `NFR-001`

The transport adapter accepts validated incremental A2UI messages for surface creation, component updates, and data-model updates. The client must be able to discard stale or duplicate messages using surface and sequence metadata.

### A2UI-003 Native action interception

Supports: `FR-008`, `NFR-003`

All actions pass through an Android interceptor that checks the action allowlist, current local state, authentication state, and confirmation policy before dispatching a server command.

### A2UI-004 Recipe nutrition surface

Supports: `FR-009`, `NFR-001`, `NFR-007`

The client renders a recipe ingredient list and nutrition target summary. The surface must show recipe name, servings, ingredient quantities, estimated protein per serving, target difference, and assumptions. It must not imply clinical accuracy.

### A2UI-005 Photo nutrition surface

Supports: `FR-010`, `NFR-001`, `NFR-007`, `NFR-008`

The client renders a photo-analysis result with detected food items, estimated portions, macro breakdown, confidence, assumptions, and correction controls. The result remains visibly marked as an estimate until the user confirms the detected foods and portions.

## 4. Domain model

Core entities:

```text
Household
HouseholdMember
DietaryPreference
MealPlan
Meal
Recipe
Ingredient
Substitution
NutritionEstimate
NutritionTarget
FoodPhotoAnalysis
DetectedFood
MacroBreakdown
GroceryList
GroceryItem
SyncOperation
```

The domain model is independent of A2UI. A2UI is a projection of domain state and user interaction, not the source of business truth.

## 5. Local persistence

### DATA-001 Room

Supports: `FR-001`, `FR-004`, `FR-005`, `FR-007`

Room stores cached household data, meal plans, grocery lists, accepted substitutions, and pending sync operations.

### DATA-002 DataStore

Supports: `FR-006`, `FR-007`

DataStore stores language preference, feature flags, anonymous installation identity, and privacy preferences.

The anonymous installation id is the MVP identity (FR-007). `AccountLink` is an adapter behind the same repository. It stays unavailable until an identity provider is configured, and it must not upload household data before the user opts in.

### DATA-003 Sync queue

Supports: `FR-005`, `NFR-002`

Mutations created offline are stored with an idempotency key, entity version, timestamp, and retry state. WorkManager retries them when connectivity returns.

## 6. Backend and agent

### API-001 Agent service

Supports: `FR-001`, `FR-003`, `FR-004`, `NFR-003`

The backend is a Python Google ADK service deployed to Cloud Run behind an authenticated HTTPS API.

The agent may call only typed domain tools:

```text
get_household_profile()
get_pantry_snapshot()
generate_meal_plan()
validate_substitution()
build_grocery_list()
save_meal_plan()
update_grocery_item()
get_recipe_ingredients()
adjust_recipe_to_nutrition_target()
analyze_food_photo()
```

Tools validate inputs and return structured domain objects. The agent cannot directly write Firestore or emit unapproved UI components.

### API-002 Command processing

Supports: `FR-008`, `NFR-003`

Every mutation includes an authenticated user or installation identity and idempotency key. Server validation is authoritative for dietary constraints, budget calculations, ownership, and future commerce operations.

### API-003 Recipe nutrition calculation

Supports: `FR-009`, `NFR-007`

The agent may call the typed tools `get_recipe_ingredients` and `adjust_recipe_to_nutrition_target`. The tools must normalize ingredients, servings, units, and nutrition assumptions before returning results. The backend must distinguish recipe-source values from calculated estimates.

Nutrition numbers in the MVP come from the versioned dataset `gharbazaar-fixtures-2026.10` shipped with the service. Every `NutritionEstimate` records `sourceVersion`. Gemini may propose candidates when `GEMINI_API_KEY` is configured. Typed tools are the only writers of domain results. A missing key uses the fixture agent; contract tests do not call a live model.

### API-004 Food photo analysis

Supports: `FR-010`, `NFR-007`, `NFR-008`

The backend accepts a user-authorized food image reference and returns detected food candidates, estimated portions, macro estimates, confidence, and correction requirements. It must not infer exact weight from pixels when the image lacks sufficient evidence.

## 7. Data flow

```text
User interaction
  -> Android action interceptor
  -> Authenticated command
  -> ADK agent and typed domain tool
  -> Domain validation and Firestore
  -> Validated A2UI update
  -> Android catalog renderer
```

The client can optimistically update safe local edits such as grocery-item notes, but must wait for server acknowledgement for synchronized or commercial outcomes.

## 8. Offline and error behavior

The app must:

- Render the last cached successful plan offline.
- Permit local grocery-list edits offline.
- Display pending synchronization state.
- Retry using WorkManager.
- Present native retry and recovery UI for transport errors.
- Preserve the user’s local draft if the agent fails.
- Never display a false success state.
- Explain when nutrition data is unavailable, ambiguous, or estimated.
- Ask for food or portion confirmation when visual evidence is insufficient.

## 9. Localization

Supports: `FR-006`, `NFR-005`

The language-pack schema supports English, Hindi, Bengali, Telugu, Marathi, Tamil, Gujarati, Kannada, Malayalam, Punjabi, Odia, Assamese, and Urdu.

All UI copy, food names, ingredients, units, errors, and accessibility labels use localization keys or localized domain content. The first validation fixtures are English and Hindi.

## 10. Security and privacy

### SEC-001 Catalog and action safety

Supports: `NFR-003`, `FR-008`

- Validate A2UI messages against the local catalog.
- Allowlist actions.
- Validate tool arguments server-side.
- Never trust client-supplied prices or totals.
- Require native confirmation for irreversible operations.

### SEC-002 Data protection

Supports: `NFR-004`

- Use Android Keystore-backed token storage.
- Minimize personally identifiable data.
- Redact household data from logs.
- Apply Firestore ownership rules.
- Keep model prompts and telemetry free of unnecessary sensitive data.

### NUT-001 Nutrition policy

Supports: `FR-009`, `NFR-007`

Nutrition output is informational. It must not diagnose, treat, or prescribe for a medical condition. If a user provides a medical target or condition, the app should recommend consulting a qualified professional rather than presenting the result as medical guidance.

### NUT-002 Photo-estimation policy

Supports: `FR-010`, `NFR-007`, `NFR-008`

Photo analysis is estimate-only. Results must include the serving basis, confidence or uncertainty, recognized foods, macro values, and a correction flow. The app must not claim laboratory-grade accuracy or use the result as medical diagnosis.

### NFR-005 Accessibility

Supports: `NFR-005`

All native and A2UI-rendered controls expose localized semantics, labels, focus order, and touch targets.

### NFR-006 Observability

Supports: `NFR-006`

The client and backend emit privacy-safe correlation, latency, failure, synchronization, and catalog-rejection metrics.

## 11. Performance targets

- Cached meal plans appear immediately when available.
- First useful streamed A2UI state appears within five seconds under normal network conditions.
- Local grocery edits respond without waiting for the network.
- A2UI catalog validation does not block ordinary native navigation.

## 12. Architecture decisions

Binding decisions for this repository. External ADR drafts are not part of the repo, so these statements are the contract.

- ADR-001. The Android client owns presentation safety, local state, permissions, and irreversible confirmation. A2UI is a projection of domain state. Unknown components and actions are rejected before UI state changes.
- ADR-002. Room and DataStore hold the last successful plan and grocery list. Offline edits are queued with an idempotency key. The UI never shows a false success state.
- ADR-003. The backend is a FastAPI service whose agent may call only the typed tools in API-001. Firestore and Gemini sit behind interfaces. Fixture adapters run in local and CI environments. Live adapters run only when deployment secrets are present.
- ADR-004. The MVP does not perform retailer checkout, payment, or delivery. Commercial actions require native confirmation and stay disabled.
