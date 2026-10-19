import os

from fastapi import FastAPI, HTTPException

from gharbazaar.tools import ToolError
from gharbazaar.transport import Transport

app = FastAPI(title="Ghar Bazaar agent")
transport = Transport()


@app.get("/health")
def health():
    return {
        "ok": True,
        "liveAgent": bool(os.getenv("GEMINI_API_KEY")),
        "firestore": bool(os.getenv("FIRESTORE_PROJECT")),
    }


@app.post("/v1/commands")
def commands(body: dict):
    try:
        return transport.handle(body)
    except ToolError as error:
        raise HTTPException(status_code=400, detail=error.message) from error


def live_adapters_enabled() -> bool:
    return bool(os.getenv("GEMINI_API_KEY") and os.getenv("FIRESTORE_PROJECT"))
