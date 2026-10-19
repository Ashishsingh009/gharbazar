# Ghar Bazaar Implementation Ledger

Status values: `Todo`, `In progress`, `Blocked`, `Ready for validation`, `Complete`

Tasks must reference a requirement or specification ID. Do not introduce new product behavior here; update `intent.md` and `spec.md` first.

## Foundation

### TASK-000 Check requirement traceability

References: `REL-001`, `REL-002`  
Status: Complete  
Owner: Platform  
Dependencies: None

- Lint requirement, specification, task, and test IDs in the contract Markdown.
- Fail when an `FR-*`, `NFR-*`, or `A2UI-*` requirement has no validation row.
- Fail when a task does not reference a requirement or specification ID.
- Validate catalog and language-pack JSON schemas used by the fixtures.

Done when: `python3 tools/traceability.py` exits 0.

### TASK-001 Create native Android foundation

References: `A2UI-001`, `NFR-001`, `NFR-006`  
Status: Complete  
Owner: Android  
Dependencies: None

- Create the Kotlin Android project and module structure.
- Configure Jetpack Compose and the pinned AndroidX A2UI dependencies.
- Add the baseline Material theme and build variants.
- Add a native shell with a placeholder A2UI surface.

Done when: debug build succeeds and the app launches into the native shell.

### TASK-002 Create A2UI catalog

References: `A2UI-001`, `A2UI-002`, `NFR-003`  
Status: Complete  
Owner: Android  
Dependencies: `TASK-001`

- Implement the Ghar Bazaar catalog components.
- Implement typed action definitions.
- Add catalog validation and unknown-component fallback UI.
- Add local fixture rendering.

Done when: all catalog fixtures render and invalid fixtures are rejected.

### TASK-003 Add contract fixtures

References: `A2UI-001`, `A2UI-002`, `TEST-001`  
Status: Complete  
Owner: Android  
Dependencies: `TASK-002`

- Add meal-plan, substitution, and grocery-list fixtures.
- Add English and Hindi fixture content.
- Add duplicate, stale, and malformed message fixtures.

Done when: fixtures can be used by automated catalog tests.

## Local data and user flow

### TASK-004 Implement local persistence

References: `DATA-001`, `DATA-002`, `DATA-003`, `FR-005`  
Status: Complete  
Owner: Android  
Dependencies: `TASK-001`

- Add Room entities and migrations.
- Add DataStore preferences.
- Add pending-operation queue.
- Add repository interfaces.

### TASK-005 Implement household onboarding

References: `FR-002`, `FR-006`, `FR-007`  
Status: Complete  
Owner: Android  
Dependencies: `TASK-004`

- Implement language selection.
- Implement household size and dietary preferences.
- Support anonymous installation identity.
- Store a local household profile.

### TASK-006 Implement meal-plan domain

References: `FR-001`, `FR-003`, `DATA-001`  
Status: Complete  
Owner: Android  
Dependencies: `TASK-004`

- Define domain models.
- Add budget and dietary-constraint validation.
- Add meal-plan repository interfaces.
- Map domain data into A2UI state.

### TASK-007 Implement grocery workflow

References: `FR-004`, `FR-005`, `A2UI-001`  
Status: Complete  
Owner: Android  
Dependencies: `TASK-006`

- Generate grocery items from accepted meals.
- Support local edits and purchased state.
- Add grocery-list A2UI surface.

### TASK-015 Implement recipe ingredient and nutrition workflow

References: `FR-009`, `A2UI-004`, `API-003`, `NUT-001`, `NFR-007`  
Status: Complete  
Owner: Product/Backend/Android  
Dependencies: `TASK-006`, `TASK-009`

- Parse recipe requests and serving targets.
- Add normalized ingredient and nutrition domain models.
- Implement `get_recipe_ingredients`.
- Implement `adjust_recipe_to_nutrition_target`.
- Add `RecipeIngredientList` and `NutritionTargetSummary` catalog components.
- Show protein target, estimated result, serving size, assumptions, and uncertainty.
- Add an informational nutrition disclaimer.

Done when: the chicken butter masala example returns a complete ingredient list and a transparent per-serving protein calculation.

### TASK-016 Implement photo nutrition workflow

References: `FR-010`, `A2UI-005`, `API-004`, `NUT-002`, `NFR-007`, `NFR-008`  
Status: Complete  
Owner: Backend/Android  
Dependencies: `TASK-002`, `TASK-013`, `TASK-015`

- Add Android photo-picker and camera permission flow.
- Create a privacy-safe image upload/reference flow.
- Implement `analyze_food_photo`.
- Add detected-food and portion-correction models.
- Add `PhotoNutritionSummary` and `MacroBreakdown` catalog components.
- Show protein, fiber, fat, carbohydrates, and calories with confidence and assumptions.
- Add explicit user confirmation before saving an analysis.
- Add image retention and deletion policy.

Done when: a user can analyze a food photo, correct a detected portion, review the macro breakdown, and save the confirmed estimate locally.

## Backend and agent

### TASK-008 Create Cloud Run agent service

References: `API-001`, `NFR-006`  
Status: Complete  
Owner: Backend  
Dependencies: None

- Create Python Google ADK service.
- Add FastAPI transport adapter.
- Configure environment-based secrets.
- Add structured logging and request correlation.

### TASK-009 Implement typed agent tools

References: `API-001`, `API-002`, `FR-001`, `FR-003`, `FR-004`  
Status: Complete  
Owner: Backend  
Dependencies: `TASK-008`

- Implement household profile retrieval.
- Implement meal-plan generation.
- Implement substitution validation.
- Implement grocery-list generation.
- Implement meal and grocery persistence.

### TASK-010 Implement A2UI transport

References: `A2UI-002`, `API-002`  
Status: Complete  
Owner: Backend  
Dependencies: `TASK-002`, `TASK-008`

- Stream validated incremental A2UI messages.
- Add surface and sequence metadata.
- Reject unsupported catalog versions.
- Add idempotency and replay protection.

## Synchronization, hardening, and release

### TASK-011 Implement synchronization

References: `DATA-003`, `NFR-002`  
Status: Complete  
Owner: Android  
Dependencies: `TASK-004`, `TASK-010`

- Add WorkManager retry policy.
- Implement server acknowledgement handling.
- Resolve stale entity versions.
- Preserve local drafts on failure.

### TASK-012 Implement localization framework

References: `FR-006`, `NFR-005`  
Status: Complete  
Owner: Android/Content  
Dependencies: `TASK-005`, `TASK-006`

- Implement language-pack loading.
- Add English and Hindi content QA fixtures.
- Add plural, unit, currency, and accessibility localization.
- Prepare regional language pack workflow.

### TASK-013 Add security and observability

References: `SEC-001`, `SEC-002`, `A2UI-003`, `NFR-006`  
Status: Complete  
Owner: Platform  
Dependencies: `TASK-010`, `TASK-011`

- Add action allowlisting.
- Add server authorization checks.
- Add privacy-safe telemetry.
- Add crash, latency, sync, and catalog rejection metrics.

### TASK-014 Complete vertical slice

References: `FR-001`, `FR-003`, `FR-004`, `TEST-001`  
Status: Complete  
Owner: Product team  
Dependencies: `TASK-007`, `TASK-010`, `TASK-011`

- Configure a household.
- Generate one meal plan.
- Render it through A2UI.
- Accept one substitution.
- Generate and locally save a grocery list.
- Run the complete validation suite.
