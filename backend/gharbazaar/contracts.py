import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTRACTS = ROOT / "contracts"
SOURCE_VERSION = "gharbazaar-fixtures-2026.10"
ESTIMATE_DISCLAIMER = "Nutrition values are estimates, not medical advice."
MEDICAL_DISCLAIMER = "This is not medical advice. Please consult a qualified professional."
MEDICAL_TERMS = (
    "diabetes", "cholesterol", "kidney", "cancer", "pregnant", "pregnancy",
    "thyroid", "blood pressure", "prescription", "disease", "diagnose",
)


def load_json(relative: str):
    return json.loads((CONTRACTS / relative).read_text())


def recipes():
    return load_json("nutrition/recipes.json")


def catalog():
    return load_json("catalog.json")


def photos():
    return load_json("photos/analyses.json")
