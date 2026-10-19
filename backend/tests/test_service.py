import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from gharbazaar.api import app
from gharbazaar.tools import (
    ToolError,
    analyze_food_photo,
    build_grocery_list,
    generate_meal_plan,
    get_recipe_ingredients,
    redact_fields,
)
from gharbazaar.transport import Transport

ROOT = Path(__file__).resolve().parents[2]
client = TestClient(app)


def household(**overrides):
    base = {
        "id": "home",
        "memberCount": 4,
        "diet": "VEGETARIAN",
        "weeklyBudgetInr": 2500,
        "allergies": [],
        "exclusions": [],
        "language": "en",
    }
    base.update(overrides)
    return base


def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["liveAgent"] is False


def test_weekly_plan_and_idempotency():
    body = {
        "installationId": "install-1",
        "idempotencyKey": "plan-1",
        "command": "generate_meal_plan",
        "payload": {"household": household()},
    }
    first = client.post("/v1/commands", json=body)
    second = client.post("/v1/commands", json=body)
    assert first.status_code == 200
    assert first.json() == second.json()
    plan = first.json()["result"]["plan"]
    assert plan["days"] == 7
    assert plan["currency"] == "INR"
    assert plan["estimatedCostInr"] <= 2500
    assert first.json()["result"]["a2ui"][0]["createSurface"]["catalogId"].endswith("/v1")


def test_constraint_failure():
    with pytest.raises(ToolError):
        generate_meal_plan(household(memberCount=0))


def test_recipe_clarify_protein_and_medical():
    clarify = get_recipe_ingredients("Give me all the ingredients for chicken butter masala")
    assert clarify["kind"] == "CLARIFY"
    ready = get_recipe_ingredients("Give me all the ingredients for chicken butter masala; I want 50 grams of protein from one serving.")
    assert ready["kind"] == "READY"
    assert 49 <= ready["proteinGrams"] <= 51
    assert ready["sourceVersion"] == "gharbazaar-fixtures-2026.10"
    assert "estimate" in ready["disclaimer"].lower() or "estimates" in ready["disclaimer"]
    medical = get_recipe_ingredients("chicken butter masala for my diabetes, one serving")
    assert medical["kind"] == "MEDICAL"
    assert medical["ingredients"] == []


def test_recipe_grocery_keeps_quantities():
    ready = get_recipe_ingredients("ingredients for chicken butter masala for 2 servings", "hi")
    grocery = build_grocery_list({"id": "recipe", "meals": [{"ingredients": ready["ingredients"]}]})
    chicken = next(item for item in grocery["items"] if item["name"] == "चिकन")
    assert chicken["unit"] == "g"
    assert chicken["quantity"] >= 200


def test_photo_privacy_and_correction_gate():
    denied = None
    with pytest.raises(ToolError):
        analyze_food_photo("fixture:thali", False)
    analysis = analyze_food_photo("fixture:thali", True)
    assert analysis["macros"]["protein_g"] > 0
    assert analysis["imageRetained"] is False
    assert "fixture:thali" not in analysis["audit"]
    ambiguous = analyze_food_photo("fixture:ambiguous", True)
    assert ambiguous["macros"] is None
    with pytest.raises(ToolError):
        analyze_food_photo("unreadable", True)
    assert denied is None


def test_client_total_and_catalog_rejection():
    response = client.post("/v1/commands", json={
        "installationId": "install-1",
        "idempotencyKey": "bad-total",
        "command": "generate_meal_plan",
        "payload": {"household": household(), "clientTotal": 10},
    })
    assert response.status_code == 400
    transport = Transport()
    with pytest.raises(ToolError):
        transport.accept_message({"version": "v0.9", "sequence": 1, "createSurface": {"surfaceId": "meal-plan", "catalogId": "https://example.invalid/v9"}})
    transport.accept_message({"version": "v0.9", "sequence": 1, "createSurface": {"surfaceId": "meal-plan", "catalogId": "https://gharbazaar.app/a2ui/catalog/v1"}})
    with pytest.raises(ToolError):
        transport.accept_message({"version": "v0.9", "sequence": 1, "createSurface": {"surfaceId": "meal-plan", "catalogId": "https://gharbazaar.app/a2ui/catalog/v1"}})


def test_redaction_and_catalog_contract():
    assert "diet" not in redact_fields({"diet": "jain", "screen": "plan"})
    catalog_doc = json.loads((ROOT / "contracts" / "catalog.json").read_text())
    assert "analyze_food_photo" in catalog_doc["actions"]
