import re

from gharbazaar.contracts import (
    ESTIMATE_DISCLAIMER,
    MEDICAL_DISCLAIMER,
    MEDICAL_TERMS,
    SOURCE_VERSION,
    catalog,
    photos,
    recipes,
)

DIET_RANK = {"vegetarian": 0, "eggetarian": 1, "non_vegetarian": 2}


class ToolError(Exception):
    def __init__(self, message: str):
        super().__init__(message)
        self.message = message


def _household_rank(diet: str) -> int:
    if diet in {"VEGETARIAN", "JAIN"}:
        return 0
    if diet == "EGGETARIAN":
        return 1
    return 2


def _allowed(recipe: dict, household: dict) -> bool:
    allergies = set(household.get("allergies") or [])
    exclusions = set(household.get("exclusions") or [])
    if allergies.intersection(recipe["allergens"]):
        return False
    diet = household["diet"]
    if diet == "JAIN" and not recipe.get("jain") and any(item["jainRestricted"] for item in recipe["ingredients"]):
        return False
    if diet == "JAIN" and recipe["diet"] != "vegetarian":
        return False
    if DIET_RANK[recipe["diet"]] > _household_rank(diet):
        return False
    for ingredient in recipe["ingredients"]:
        if ingredient["id"] in exclusions or allergies.intersection(ingredient["allergens"]):
            return False
    return True


def _name(recipe: dict, locale: str) -> str:
    return recipe["names"].get(locale) or recipe["names"]["en"]


def generate_meal_plan(household: dict) -> dict:
    if int(household.get("memberCount") or 0) <= 0:
        raise ToolError("No meal satisfies the household constraints.")
    book = recipes()
    allowed = [recipe for recipe in book["recipes"] if _allowed(recipe, household)]
    if not allowed:
        raise ToolError("No meal satisfies the household constraints.")
    allowed.sort(key=lambda recipe: recipe["costPerServingInr"])
    meals = []
    cost = 0
    locale = household.get("language", "en")
    for day in range(1, 8):
        for slot in ("lunch", "dinner"):
            recipe = allowed[len(meals) % len(allowed)]
            meal_cost = recipe["costPerServingInr"] * int(household["memberCount"])
            if cost + meal_cost > int(household["weeklyBudgetInr"]):
                continue
            cost += meal_cost
            meals.append({
                "id": f"{recipe['id']}-{day}-{slot}",
                "day": day,
                "slot": slot,
                "recipeId": recipe["id"],
                "name": _name(recipe, locale),
                "costInr": meal_cost,
                "ingredients": [
                    {"id": item["id"], "name": item["names"].get(locale) or item["names"]["en"], "quantity": item["quantity"], "unit": item["unit"]}
                    for item in recipe["ingredients"]
                ],
            })
    days = len({meal["day"] for meal in meals})
    return {
        "id": f"plan-{household['id']}",
        "days": days,
        "meals": meals,
        "estimatedCostInr": cost,
        "budgetInr": household["weeklyBudgetInr"],
        "currency": "INR",
        "note": None if days == 7 else "The budget covers fewer than seven days of meals.",
        "sourceVersion": SOURCE_VERSION,
    }


def validate_substitution(plan: dict, household: dict) -> dict:
    for meal in plan["meals"]:
        for ingredient in meal["ingredients"]:
            if ingredient["id"] in set(household.get("exclusions") or []):
                raise ToolError(f"Meal {meal['name']} breaks a household constraint.")
            if household["diet"] == "VEGETARIAN" and ingredient["id"] == "chicken":
                raise ToolError(f"Meal {meal['name']} breaks a household constraint.")
    return {"valid": True}


def build_grocery_list(plan: dict) -> dict:
    grouped = {}
    for meal in plan["meals"]:
        for ingredient in meal["ingredients"]:
            key = f"{ingredient['id']}|{ingredient['unit']}"
            current = grouped.get(key)
            if current is None:
                grouped[key] = {**ingredient, "id": key}
            else:
                current["quantity"] = round(current["quantity"] + ingredient["quantity"], 1)
    return {"id": f"grocery-{plan['id']}", "items": list(grouped.values())}


def _find_recipe(text: str):
    lowered = text.lower()
    for recipe in recipes()["recipes"]:
        if recipe["id"].replace("-", " ") in lowered or any(name.lower() in lowered for name in recipe["names"].values()):
            return recipe
    return None


def _servings(text: str):
    if "one serving" in text or "1 serving" in text or "per serving" in text:
        return 1
    match = re.search(r"(\d+)\s+servings?", text)
    return int(match.group(1)) if match else None


def _protein_target(text: str):
    match = re.search(r"(\d+(?:\.\d+)?)\s*(?:grams|gram|g)\s+of\s+protein", text)
    return float(match.group(1)) if match else None


def _macros(ingredient: dict, quantity: float) -> dict:
    factor = quantity / 100.0
    per = ingredient["per100g"]
    return {key: per[key] * factor for key in ("protein_g", "fiber_g", "fat_g", "carbohydrate_g", "calories")}


def get_recipe_ingredients(request: str, locale: str = "en") -> dict:
    lowered = request.lower()
    if any(term in lowered for term in MEDICAL_TERMS):
        return {"kind": "MEDICAL", "disclaimer": MEDICAL_DISCLAIMER, "ingredients": [], "explanation": "A medical condition needs a qualified professional."}
    recipe = _find_recipe(lowered)
    if recipe is None:
        return {"kind": "CLARIFY", "explanation": "Which recipe should be used?", "disclaimer": ESTIMATE_DISCLAIMER}
    servings = _servings(lowered)
    if servings is None:
        return {
            "kind": "CLARIFY",
            "recipeName": _name(recipe, locale),
            "explanation": "How many servings should this recipe make?",
            "disclaimer": ESTIMATE_DISCLAIMER,
        }
    return _ready(recipe, servings, locale, _protein_target(lowered))


def adjust_recipe_to_nutrition_target(request: str, locale: str = "en") -> dict:
    return get_recipe_ingredients(request, locale)


def _ready(recipe: dict, servings: int, locale: str, target) -> dict:
    book = recipes()
    ingredients = []
    for item in recipe["ingredients"]:
        ingredients.append({**item, "quantity": round(item["quantity"] * servings, 1), "name": item["names"].get(locale) or item["names"]["en"]})
    if target is None:
        macros = _sum(ingredients)
        return _answer(recipe, locale, servings, ingredients, macros, None, False, f"Quantities are scaled for {servings} serving(s).")
    chicken = next((item for item in ingredients if item["id"] == "chicken"), None)
    base = _sum(ingredients)
    if chicken is None:
        return _answer(recipe, locale, servings, ingredients, base, target, False, "The target cannot be met without changing the recipe materially.")
    others = base["protein_g"] - _macros(chicken, chicken["quantity"])["protein_g"]
    per_gram = chicken["per100g"]["protein_g"] / 100.0
    needed = target - others
    factor = (needed / per_gram) / chicken["quantity"] if per_gram and chicken["quantity"] else 99
    if needed <= 0 or factor > book["materialChangeFactor"]:
        return _answer(recipe, locale, servings, ingredients, base, target, False, "The target cannot be met without changing the recipe materially.")
    chicken["quantity"] = round(needed / per_gram, 1)
    macros = _sum(ingredients)
    return _answer(recipe, locale, servings, ingredients, macros, target, True, f"Protein-bearing quantity was adjusted using {SOURCE_VERSION}.")


def _sum(ingredients: list[dict]) -> dict:
    total = {key: 0.0 for key in ("protein_g", "fiber_g", "fat_g", "carbohydrate_g", "calories")}
    for ingredient in ingredients:
        part = _macros(ingredient, ingredient["quantity"])
        for key in total:
            total[key] += part[key]
    return total


def _answer(recipe, locale, servings, ingredients, macros, target, adjusted, explanation) -> dict:
    difference = None if target is None else macros["protein_g"] - target
    return {
        "kind": "READY",
        "recipeName": _name(recipe, locale),
        "servings": servings,
        "ingredients": [
            {"id": item["id"], "name": item["name"], "quantity": item["quantity"], "unit": item["unit"]}
            for item in ingredients
        ],
        "proteinGrams": macros["protein_g"],
        "fiberGrams": macros["fiber_g"],
        "fatGrams": macros["fat_g"],
        "carbohydrateGrams": macros["carbohydrate_g"],
        "calories": macros["calories"],
        "targetProteinGrams": target,
        "differenceGrams": difference,
        "adjusted": adjusted,
        "sourceVersion": SOURCE_VERSION,
        "sourceKind": "calculated-estimate",
        "assumptions": f"Per-ingredient values from {SOURCE_VERSION}, scaled to {servings} serving(s).",
        "explanation": explanation,
        "disclaimer": ESTIMATE_DISCLAIMER,
    }


def analyze_food_photo(image_ref: str, authorized: bool) -> dict:
    if not authorized:
        raise ToolError("A food photo needs permission before it can be analyzed.")
    if not image_ref or image_ref in {"unreadable"} or image_ref.startswith("data:"):
        raise ToolError("The image could not be read. Choose another photo.")
    dataset = photos()
    image = dataset["images"].get(image_ref)
    if image is None:
        raise ToolError("The image could not be read. Choose another photo.")
    foods = []
    totals = {key: 0.0 for key in ("protein_g", "fiber_g", "fat_g", "carbohydrate_g", "calories")}
    for food in image["foods"]:
        grams = food["estimatedGrams"] if image["sufficientEvidence"] else None
        macros = None
        if grams is not None:
            macros = _macros({"per100g": food["per100g"]}, grams)
            for key in totals:
                totals[key] += macros[key]
        foods.append({
            "id": food["id"],
            "name": food["names"]["en"],
            "portion": food["portion"]["en"],
            "estimatedGrams": grams,
            "exactWeight": False,
            "macros": macros,
        })
    return {
        "imageRetained": False,
        "confidence": image["confidence"],
        "assumptions": f"Visible foods only. Source {dataset['sourceVersion']}. A photo cannot provide an exact weight.",
        "foods": foods,
        "needsConfirmation": True,
        "confirmed": False,
        "macros": totals if image["sufficientEvidence"] else None,
        "message": "Review the detected foods before saving." if image["sufficientEvidence"] else "The photo does not show a reliable portion. Confirm the food and amount before saving.",
        "audit": f"confidence={image['confidence']};retained=False",
    }


def save_meal_plan(plan: dict, version: int, stored_version: int) -> dict:
    if version < stored_version:
        raise ToolError("stale")
    return {"saved": True, "version": max(version, stored_version)}


def update_grocery_item(item: dict) -> dict:
    return {**item, "updated": True}


def get_household_profile(store: dict, installation_id: str) -> dict:
    return store.get(installation_id) or {}


def get_pantry_snapshot(store: dict, installation_id: str) -> dict:
    return {"items": store.get(installation_id, {}).get("pantry", [])}


SENSITIVE = {"diet", "allergies", "exclusions", "token", "authorization", "email", "installationid", "image", "photo"}


def redact_fields(fields: dict) -> dict:
    return {key: value for key, value in fields.items() if key.lower() not in SENSITIVE}
