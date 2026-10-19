# Ghar Bazaar Product Intent

Status: Draft product source of truth  
Version: 1.0  
Last updated: 2026-10-18

## 1. Product vision

Ghar Bazaar is a multilingual, AI-powered household meal and grocery planning assistant for Indian families.

It turns household size, budget, dietary preferences, pantry context, and regional food preferences into a practical weekly meal plan and an editable grocery list.

The product is not a grocery-delivery marketplace in its first release. It is the planning and decision layer that can later connect users to existing grocery and retail platforms.

## 2. Product principles

- Useful before clever: every recommendation must help the household decide, cook, or shop.
- Indian by default: support regional cuisines, ingredients, units, dietary practices, and budgets in INR.
- Local-first: the last successful plan and grocery list remain useful without connectivity.
- Human control: AI may recommend, but users approve substitutions and future commercial actions.
- Native Android experience: dynamic content should feel like a normal Compose application.
- Trust over monetization: sponsored or affiliate recommendations must be disclosed.
- Safe generative UI: agents may request only approved A2UI components and actions.

## 3. Target users

Primary users:

- Urban and semi-urban Indian households.
- Working couples planning weekly meals.
- Parents managing different family preferences.
- Budget-conscious households.
- Vegetarian, Jain, egg, allergy-sensitive, and other dietary-restricted users.
- Users who prefer Hindi or another Indian language.

Secondary users:

- Nutrition and wellness partners.
- Regional grocery retailers.
- Consumer packaged goods brands.
- Employee wellness and family-benefit providers.

## 4. User problems

- Deciding what to cook repeatedly takes time and mental effort.
- Household preferences are difficult to reconcile.
- Grocery lists are often incomplete or disconnected from meal plans.
- Ingredient substitutions are hard to judge culturally and nutritionally.
- Food budgets are difficult to track across a week.
- Existing food and grocery apps optimize ordering, not household planning.
- English-first experiences do not serve all Indian households equally.

## 5. MVP scope

The MVP includes:

- Household onboarding.
- Household size and food preferences.
- Dietary constraints including vegetarian, egg, Jain, allergies, and exclusions.
- Weekly meal-plan generation.
- Budget-aware recommendations in INR.
- Indian and regional meal recommendations.
- Ingredient substitutions.
- Photo-based nutrition estimation as an estimate-only beta capability.
- Recipe-level ingredient and nutrition requests.
- Editable grocery-list generation.
- Local-first cached plans and grocery lists.
- Optional account upgrade and cloud synchronization.
- English and Hindi validation.
- Localization-ready architecture for additional Indian languages.

## 6. Explicit non-goals for MVP

- Retailer checkout or payment processing.
- Live grocery inventory or delivery logistics.
- Family collaboration and multi-user roles.
- Medical nutrition advice.
- Fully voice-first interaction.
- Building a grocery delivery fleet or marketplace.

## 7. Primary user journey

```text
Install app
  -> Choose language
  -> Configure household
  -> Set budget and food preferences
  -> Generate weekly meal plan
  -> Review meals
  -> Accept or reject substitutions
  -> Build grocery list
  -> Edit or share list
  -> Save plan locally
```

The app must provide useful fallback states when the network, agent, language content, or generated UI is unavailable.

## 8. Functional requirements

### FR-001 Weekly meal plan

The user can generate a weekly meal plan from household size, budget, dietary preferences, pantry context, and regional food preferences.

Acceptance intent:

- The plan represents seven days or clearly explains a shorter result.
- Meals respect hard dietary restrictions.
- Estimated cost is shown in INR.
- The user can regenerate or modify the plan.

### FR-002 Household preferences

The user can create and edit household preferences, including members, cuisine preferences, exclusions, allergies, and meal constraints.

### FR-003 Substitutions

The user can review, accept, or reject suggested ingredient substitutions.

### FR-004 Grocery list

The user can generate an editable grocery list from an accepted meal plan.

### FR-005 Offline use

The user can view the last successful plan and edit the grocery list without network access.

### FR-006 Localization

The app supports localized UI and food content through language packs. English and Hindi are the first validation languages; additional major Indian languages are added through staged rollout.

### FR-007 Optional account

The user can use the app anonymously and optionally upgrade to an account for backup and cross-device synchronization.

### FR-008 Safe actions

The app requires explicit native confirmation before future ordering, payment, account, or other irreversible actions.

### FR-009 Recipe ingredients and nutrition targets

The user can ask for the ingredients and quantities for a specific recipe, including a per-serving nutrition target such as “Give me all the ingredients for chicken butter masala; I want 50 grams of protein from one serving.”

Acceptance intent:

- The app identifies the requested recipe and serving count.
- It returns a complete ingredient list with quantities and localized units.
- It shows the estimated protein per serving and the assumptions used.
- It adjusts quantities or explains when the target cannot be met without changing the recipe materially.
- It clearly labels nutrition values as estimates, not medical advice.

### FR-010 Photo nutrition analysis

The user can upload or capture a food photo and receive an estimated nutrition breakdown for the visible food, including protein, fiber, fat, carbohydrates, and calories where supported.

Acceptance intent:

- The user can choose an image through the Android system photo picker or camera flow.
- The result identifies visible food items and estimated portions when possible.
- The result displays grams of protein, fiber, fat, carbohydrates, and calories per detected serving or plate.
- The app shows confidence, assumptions, and an editable correction path.
- The user can confirm or correct detected foods and portion sizes before saving the result.
- The app clearly states that a photo cannot provide exact nutrition values.

## 9. Non-functional requirements

### NFR-001 Native Android experience

Dynamic meal and grocery experiences render through Jetpack Compose and an approved AndroidX A2UI catalog.

### NFR-002 Local-first behavior

The last successful plan and grocery list remain accessible offline.

### NFR-003 Safety

Unknown A2UI components, unsupported actions, invalid tool arguments, and untrusted client totals are rejected.

### NFR-004 Privacy

Collect the minimum data required for planning and synchronization. Sensitive household data must not be written to ordinary logs.

### NFR-005 Accessibility

Interactive experiences expose meaningful labels, state, focus order, and touch targets in every supported language.

### NFR-006 Observability

The product records privacy-safe product and reliability metrics for agent latency, A2UI rejection, synchronization, crashes, and user outcomes.

### NFR-007 Nutrition transparency

Nutrition estimates must expose their serving size, ingredient assumptions, calculation source or version, and confidence or uncertainty where available.

### NFR-008 Image privacy

Food images are processed only with user permission, are not retained longer than necessary by default, and are excluded from ordinary logs and analytics.

## 10. Business intent

The initial product is free to maximize household adoption and weekly retention.

Potential future revenue streams:

1. Grocery referral or affiliate commissions.
2. Premium household subscription.
3. Retailer-branded meal-planning products.
4. Nutrition and wellness partnerships.
5. Clearly disclosed sponsored recommendations.
6. B2B APIs and white-label integrations.

The business should validate repeated meal-plan and grocery-list usage before relying on subscriptions or commerce revenue.

## 11. Success metrics

- Weekly active households.
- Four-week household retention.
- Meal plans generated per household.
- Grocery lists created per household.
- Grocery-list completion rate.
- Substitution acceptance rate.
- Offline usage and sync success rate.
- Language usage by locale.
- User-reported usefulness.
- Premium conversion when introduced.
- Referred grocery transactions when introduced.

## 12. Trust boundaries

The Android shell owns navigation, permissions, authentication, offline state, security, and irreversible actions.

A2UI owns dynamic presentation and structured interaction only. The agent cannot execute arbitrary Android code, access device APIs directly, bypass server validation, or claim commercial actions completed without confirmation.
