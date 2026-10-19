#!/usr/bin/env python3
"""Reject committed secrets. REL security scan for local and CI runs."""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
PATTERNS = [
    re.compile(r"BEGIN (RSA |OPENSSH )?PRIVATE KEY"),
    re.compile(r"AKIA[0-9A-Z]{16}"),
    re.compile(r"AIza[0-9A-Za-z\-_]{35}"),
    re.compile(r"(?i)(api[_-]?key|secret|password)\s*[:=]\s*['\"][^'\"]+['\"]"),
]
SKIP = {".git", ".gradle", "build", ".idea"}


def main() -> None:
    failures = []
    for path in ROOT.rglob("*"):
        if not path.is_file() or any(part in SKIP for part in path.parts):
            continue
        if path.suffix in {".webp", ".jar", ".png"}:
            continue
        try:
            text = path.read_text(errors="ignore")
        except OSError:
            continue
        for pattern in PATTERNS:
            if pattern.search(text):
                failures.append(f"{path.relative_to(ROOT)} matches {pattern.pattern}")
    if failures:
        print("\n".join(failures), file=sys.stderr)
        sys.exit(1)
    print("secret scan: ok")


if __name__ == "__main__":
    main()
