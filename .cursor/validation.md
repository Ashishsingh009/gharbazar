# Ghar Bazaar Validation Plan

Status values: `Not started`, `In progress`, `Validated`, `Partially validated`, `Blocked`

## 1. Traceability matrix

| Requirement | Specification | Implementation | Validation | Status |
|---|---|---|---|---|
| FR-001 | A2UI-001, API-001 | TASK-006, TASK-009 | TEST-001, TEST-002 | Not started |
| FR-002 | DATA-001 | TASK-005 | TEST-003 | Not started |
| FR-003 | A2UI-001, API-001 | TASK-006, TASK-009 | TEST-004 | Not started |
| FR-004 | A2UI-001, API-001 | TASK-007, TASK-009 | TEST-005 | Not started |
| FR-005 | DATA-003 | TASK-004, TASK-011 | TEST-006 | Not started |
| FR-006 | Localization | TASK-005, TASK-012 | TEST-007 | Not started |
| FR-007 | DATA-002 | TASK-005 | TEST-008 | Not started |
| FR-008 | A2UI-003, API-002 | TASK-013 | TEST-009 | Not started |
| FR-009 | A2UI-004, API-003 | TASK-015 | TEST-015, TEST-016 | Not started |
| FR-010 | A2UI-005, API-004 | TASK-016 | TEST-019, TEST-020, TEST-021 | Not started |
| NFR-001 | A2UI-001 | TASK-001, TASK-002 | TEST-010 | Not started |
| NFR-002 | DATA-003 | TASK-011 | TEST-006 | Not started |
| NFR-003 | SEC-001 | TASK-002, TASK-013 | TEST-011 | Not started |
| NFR-004 | SEC-002 | TASK-013 | TEST-012 | Not started |
| NFR-005 | Localization | TASK-012 | TEST-013 | Not started |
| NFR-006 | API-001 | TASK-008, TASK-013 | TEST-014 | Not started |
| NFR-007 | A2UI-004, API-003, NUT-001 | TASK-015 | TEST-016, TEST-017 | Not started |
| NFR-008 | A2UI-005, API-004, NUT-002 | TASK-016 | TEST-021, TEST-022 | Not started |

## 2. Test cases

### TEST-001 Generate weekly meal plan

References: `FR-001`, `A2UI-001`, `API-001`

Given a four-person vegetarian household with a ₹2,500 budget, when the user requests a plan, the backend returns a valid meal-plan domain object and the Android catalog renders it with seven days, INR budget data, and no unsupported component.

### TEST-002 Meal-plan constraints

References: `FR-001`, `API-001`

Verify that allergies, exclusions, Jain preferences, and vegetarian preferences are respected. A hard constraint failure must produce an error or clarification request, never a silently invalid meal.

### TEST-003 Household onboarding

References: `FR-002`, `DATA-001`

Verify that a new anonymous user can select a language, configure household size, save dietary preferences, close the app, and restore the profile.

### TEST-004 Substitution flow

References: `FR-003`, `A2UI-001`

Verify that accepting and rejecting a substitution updates the local plan and produces the correct grocery-list ingredient state.

### TEST-005 Grocery-list generation

References: `FR-004`, `A2UI-001`

Verify that accepted meals produce grouped grocery items, duplicate ingredients are consolidated, units are localized, and every item remains editable.

### TEST-006 Offline synchronization

References: `FR-005`, `NFR-002`, `DATA-003`

Verify cached rendering, offline grocery edits, queued mutations, retry behavior, duplicate replay protection, and preservation of local drafts after server failure.

### TEST-007 Localization

References: `FR-006`, `NFR-005`

Verify English and Hindi UI, meal names, ingredient names, INR formatting, pluralization, accessibility labels, and long-text layout. Confirm language packs can be added without changing domain APIs.

### TEST-008 Anonymous-to-account upgrade

References: `FR-007`

Verify that local household data can be associated with an authenticated account without loss or unintended disclosure.

### TEST-009 Action safety

References: `FR-008`, `A2UI-003`, `SEC-001`

Verify that unapproved actions, unauthenticated mutations, stale commands, and commercial actions without native confirmation are rejected.

### TEST-010 A2UI fixture rendering

References: `A2UI-001`, `A2UI-002`, `NFR-001`

Render valid meal-plan, substitution, and grocery-list fixtures on Android. Verify incremental updates, data bindings, loading states, and fallback rendering.

### TEST-011 Invalid A2UI payloads

References: `A2UI-001`, `NFR-003`

Verify rejection of unknown components, unknown properties, invalid bindings, unsupported catalog versions, duplicate IDs, and malformed messages.

### TEST-012 Privacy and logging

References: `NFR-004`, `SEC-002`

Verify that household dietary data, tokens, and user identifiers do not appear in ordinary logs or telemetry payloads.

### TEST-013 Accessibility

References: `NFR-005`

Verify semantics, focus order, touch targets, contrast, screen-reader labels, and localized accessibility strings.

### TEST-014 Reliability and observability

References: `NFR-006`, `API-001`

Verify correlation IDs, agent latency metrics, tool failures, A2UI rejection metrics, synchronization metrics, and crash reporting without sensitive content.

### TEST-015 Recipe ingredient request

References: `FR-009`, `A2UI-004`, `API-003`

Given the request “Give me all the ingredients for chicken butter masala,” verify that the app identifies the recipe, returns a complete ingredient list, includes quantities and units, and asks a clarification question when serving count is missing or ambiguous.

### TEST-016 Protein target per serving

References: `FR-009`, `NFR-007`, `API-003`

Given one serving and a 50-gram protein target, verify that the result shows serving size, estimated protein, target difference, adjusted quantities or an explanation, and the assumptions used in the calculation.

### TEST-017 Nutrition transparency and safety

References: `FR-009`, `NFR-007`, `NUT-001`

Verify that nutrition results are labelled as estimates, identify missing or uncertain nutrition data, do not claim medical accuracy, and route medical-condition requests to a qualified-professional disclaimer.

### TEST-018 Recipe-to-grocery conversion

References: `FR-009`, `FR-004`, `A2UI-004`

Verify that ingredients from a recipe request can be added to an editable grocery list without losing quantities, units, servings, or substitutions.

### TEST-019 Food photo upload

References: `FR-010`, `A2UI-005`, `API-004`

Verify that the user can select a photo through the Android photo picker, grant only the required permission, receive an analysis request state, and recover cleanly from an unsupported or unreadable image.

### TEST-020 Photo macro breakdown

References: `FR-010`, `A2UI-005`, `API-004`

Given a photo containing an identifiable meal, verify that the result displays detected foods, estimated portions, grams of protein, fiber, fat, carbohydrates, and calories, together with serving basis and confidence.

### TEST-021 Photo correction flow

References: `FR-010`, `NUT-002`

Verify that the user can correct a detected food or portion, recalculate the macro breakdown, and confirm the result before it is saved.

### TEST-022 Image privacy

References: `NFR-008`, `API-004`

Verify that images require user authorization, are not written to ordinary logs or analytics, follow retention/deletion rules, and are not retained by default after the analysis lifecycle ends.

## 3. Validation levels

- Unit: domain rules, cost calculation, localization, action policy.
- Contract: A2UI catalog, agent tools, API schema, language-pack schema.
- Android: Compose rendering, state binding, navigation, offline behavior.
- Backend: agent tools, authorization, persistence, idempotency.
- Integration: Android-to-Cloud-Run flow with fixture model responses.
- End-to-end: household setup through grocery-list completion.
- Security: dependency, secret, authorization, prompt-injection, and log-redaction checks.

## 4. Completion rule

A requirement is `Validated` only when its mapped test passes and evidence is linked from CI or a review artifact. A requirement without a validation mapping blocks release.
