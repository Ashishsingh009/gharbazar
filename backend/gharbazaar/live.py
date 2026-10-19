"""Optional Google ADK adapter. Contract tests use the fixture transport instead."""

import os


def try_load_adk():
    if not os.getenv("GEMINI_API_KEY"):
        return None
    try:
        import google.adk  # noqa: F401
    except ImportError:
        return None
    return {"status": "configured"}
