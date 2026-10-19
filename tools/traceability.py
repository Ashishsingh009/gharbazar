#!/usr/bin/env python3
"""REL-001 and REL-002 checks for the Ghar Bazaar contracts."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CURSOR = ROOT / ".cursor"
CONTRACTS = ROOT / "contracts"

REQUIREMENT_RE = re.compile(r"\b((?:FR|NFR|A2UI)-\d+)\b")
TASK_RE = re.compile(r"^### (TASK-\d+)\b", re.M)
ID_RE = re.compile(
    r"\b(?:FR|NFR|A2UI|DATA|API|SEC|NUT|REL|TEST)-\d+\b"
)
TASK_STATUS = {
    "Todo",
    "In progress",
    "Blocked",
    "Ready for validation",
    "Complete",
}
VALIDATION_STATUS = {
    "Not started",
    "In progress",
    "Validated",
    "Partially validated",
    "Blocked",
}


def fail(messages: list[str]) -> None:
    for message in messages:
        print(f"traceability: {message}", file=sys.stderr)
    sys.exit(1)


def requirement_ids(text: str, kind: str) -> set[str]:
    return set(re.findall(rf"\b({kind}-\d+)\b", text))


def matrix_rows(validation: str) -> dict[str, tuple[str, str]]:
    rows: dict[str, tuple[str, str]] = {}
    for line in validation.splitlines():
        if not line.startswith("| FR-") and not line.startswith("| NFR-"):
            continue
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if len(cells) < 5:
            continue
        rows[cells[0]] = (cells[3], cells[-1])
    return rows


def check_tasks(implement: str, errors: list[str]) -> None:
    blocks = re.split(r"(?=^### TASK-)", implement, flags=re.M)
    seen = False
    for block in blocks:
        match = TASK_RE.search(block)
        if not match:
            continue
        seen = True
        task = match.group(1)
        if "References:" not in block or not ID_RE.search(block):
            errors.append(f"{task} does not reference a requirement or specification ID")
        status = re.search(r"Status:\s*(.+)", block)
        if not status or status.group(1).strip() not in TASK_STATUS:
            errors.append(f"{task} has a status outside the ledger")
    if not seen:
        errors.append("no tasks found in implement.md")


def check_language_packs(errors: list[str]) -> None:
    en = json.loads((CONTRACTS / "languages" / "en.json").read_text())
    hi = json.loads((CONTRACTS / "languages" / "hi.json").read_text())
    if set(en["strings"]) != set(hi["strings"]):
        errors.append("English and Hindi language packs do not share string keys")
    for key, value in en["strings"].items():
        if not str(value).strip() or not str(hi["strings"][key]).strip():
            errors.append(f"empty language string: {key}")
    if "a11y_generate_plan" not in en["strings"]:
        errors.append("missing accessibility label a11y_generate_plan")


def check_catalog(errors: list[str]) -> None:
    catalog = json.loads((CONTRACTS / "catalog.json").read_text())
    if catalog["catalogId"] != "https://gharbazaar.app/a2ui/catalog/v1":
        errors.append("catalog id drifted from spec.md")
    if catalog["protocolVersion"] != "v0.9":
        errors.append("protocol version drifted from spec.md")
    for name, spec in catalog["components"].items():
        if not spec.get("required"):
            errors.append(f"{name} has no required properties")
    expected_actions = {
        "edit_household_preferences",
        "select_meal",
        "accept_substitution",
        "reject_substitution",
        "build_grocery_list",
        "edit_grocery_item",
        "regenerate_meal_plan",
        "save_meal_plan",
        "get_recipe_ingredients",
        "adjust_recipe_to_nutrition_target",
        "analyze_food_photo",
        "confirm_photo_analysis",
    }
    if set(catalog["actions"]) != expected_actions:
        errors.append("catalog actions do not match spec.md")
    recipes = json.loads((CONTRACTS / "nutrition" / "recipes.json").read_text())
    ids = {recipe["id"] for recipe in recipes["recipes"]}
    if "chicken-butter-masala" not in ids:
        errors.append("nutrition dataset is missing chicken-butter-masala")
    if recipes["sourceVersion"] != catalog["sourceVersion"]:
        errors.append("nutrition sourceVersion does not match the catalog")
    for fixture in (CONTRACTS / "fixtures" / "a2ui").glob("*.json"):
        json.loads(fixture.read_text())


def main() -> None:
    intent = (CURSOR / "intent.md").read_text()
    spec = (CURSOR / "spec.md").read_text()
    implement = (CURSOR / "implement.md").read_text()
    validation = (CURSOR / "validation.md").read_text()
    errors: list[str] = []

    rows = matrix_rows(validation)
    for kind, source in (("FR", intent), ("NFR", intent)):
        for requirement in sorted(requirement_ids(source, kind)):
            if requirement not in rows:
                errors.append(f"{requirement} has no validation matrix row")
            elif rows[requirement][1] not in VALIDATION_STATUS:
                errors.append(f"{requirement} validation status is not allowed")
    for a2ui in sorted(requirement_ids(spec, "A2UI")):
        if a2ui not in validation:
            errors.append(f"{a2ui} has no validation reference")
    if "FR-009" not in rows or "TEST-018" not in rows["FR-009"][0]:
        errors.append("FR-009 matrix row is missing TEST-018")
    check_tasks(implement, errors)
    check_language_packs(errors)
    check_catalog(errors)
    if errors:
        fail(errors)
    print("traceability: ok")


if __name__ == "__main__":
    main()
