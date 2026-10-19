import json

from gharbazaar.contracts import catalog
from gharbazaar.tools import ToolError

CATALOG_ID = "https://gharbazaar.app/a2ui/catalog/v1"


class Transport:
    def __init__(self):
        self.sequences = {}
        self.results = {}
        self.plans = {}
        self.profiles = {}
        self.metrics = []

    def handle(self, body: dict) -> dict:
        if any(key.lower() in {"clienttotal", "price"} for key in body.get("payload", {})):
            raise ToolError("Client totals are not accepted.")
        installation = body.get("installationId")
        key = body.get("idempotencyKey")
        if not installation or not key:
            raise ToolError("Identity and idempotency key are required.")
        if key in self.results:
            return self.results[key]
        command = body["command"]
        payload = body.get("payload") or {}
        if command == "generate_meal_plan":
            plan = __import__("gharbazaar.tools", fromlist=["generate_meal_plan"]).generate_meal_plan(payload["household"])
            self.profiles[installation] = payload["household"]
            messages = self._project(plan)
            result = {"plan": plan, "a2ui": messages}
        elif command == "get_recipe_ingredients":
            from gharbazaar.tools import get_recipe_ingredients
            result = get_recipe_ingredients(payload["request"], payload.get("locale", "en"))
        elif command == "adjust_recipe_to_nutrition_target":
            from gharbazaar.tools import adjust_recipe_to_nutrition_target
            result = adjust_recipe_to_nutrition_target(payload["request"], payload.get("locale", "en"))
        elif command == "analyze_food_photo":
            from gharbazaar.tools import analyze_food_photo
            result = analyze_food_photo(payload.get("imageRef", ""), bool(payload.get("authorized")))
            self.metrics.append({"name": "photo", "attributes": {"audit": result["audit"]}})
        elif command == "build_grocery_list":
            from gharbazaar.tools import build_grocery_list
            result = build_grocery_list(payload["plan"])
        elif command == "save_meal_plan":
            from gharbazaar.tools import save_meal_plan
            stored = self.plans.get(installation, 0)
            saved = save_meal_plan(payload["plan"], int(payload.get("version", 1)), stored)
            self.plans[installation] = saved["version"]
            result = saved
        elif command == "validate_substitution":
            from gharbazaar.tools import validate_substitution
            result = validate_substitution(payload["plan"], payload["household"])
        elif command == "get_household_profile":
            from gharbazaar.tools import get_household_profile
            result = get_household_profile(self.profiles, installation)
        elif command == "get_pantry_snapshot":
            from gharbazaar.tools import get_pantry_snapshot
            result = get_pantry_snapshot(self.profiles, installation)
        elif command == "update_grocery_item":
            from gharbazaar.tools import update_grocery_item
            result = update_grocery_item(payload["item"])
        else:
            raise ToolError("Unsupported command.")
        self.metrics.append({"name": command, "installation": "redacted"})
        wrapped = {"ok": True, "result": result}
        self.results[key] = wrapped
        return wrapped

    def _project(self, plan: dict) -> list:
        messages = [
            {"version": "v0.9", "sequence": 1, "createSurface": {"surfaceId": "meal-plan", "catalogId": CATALOG_ID}},
        ]
        components = [{
            "id": "root",
            "component": "MealPlanHeader",
            "title": "Weekly plan",
            "dayCount": plan["days"],
            "children": ["budget"],
        }, {
            "id": "budget",
            "component": "BudgetSummary",
            "currency": "INR",
            "estimatedInr": plan["estimatedCostInr"],
            "budgetInr": plan["budgetInr"],
        }]
        messages.append({"version": "v0.9", "sequence": 2, "updateComponents": {"surfaceId": "meal-plan", "components": components}})
        for message in messages:
            self.accept_message(message)
        return messages

    def accept_message(self, message: dict) -> None:
        spec = catalog()
        if message.get("version") != spec["protocolVersion"]:
            raise ToolError("Unsupported protocol version.")
        sequence = message.get("sequence")
        if not isinstance(sequence, int):
            raise ToolError("Missing sequence.")
        envelope = [key for key in ("createSurface", "updateComponents", "updateDataModel", "deleteSurface") if key in message]
        if len(envelope) != 1:
            raise ToolError("Malformed message.")
        body = message[envelope[0]]
        surface = body.get("surfaceId")
        previous = self.sequences.get(surface)
        if previous is not None and sequence <= previous:
            raise ToolError("Stale or duplicate message.")
        if "createSurface" in message and body.get("catalogId") != spec["catalogId"]:
            raise ToolError("Unsupported catalog version.")
        if "updateComponents" in message:
            seen = set()
            for component in body.get("components", []):
                if component["id"] in seen:
                    raise ToolError("Duplicate component id.")
                seen.add(component["id"])
                if component["component"] not in spec["components"]:
                    raise ToolError("Unknown component.")
        self.sequences[surface] = sequence


def redact_log(record: dict) -> str:
    hidden = {"diet", "allergies", "token", "authorization", "email", "image"}
    return json.dumps({key: value for key, value in record.items() if key not in hidden})
