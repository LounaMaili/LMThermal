#!/usr/bin/env python3
"""Reject HT-301 types/protocol dimensions in the shared camera/presentation boundary.

This supplements behavioral/parity tests: a passing simulator alone cannot reveal
an unused fallback to a specific camera. Driver implementations and the Android
composition root are intentionally excluded, as documented in CAMERA_MODULE_ARCHITECTURE.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SHARED_FILES = [
    "core/src/main/kotlin/org/lmthermal/core/CelsiusPresentation.kt",
    "core/src/main/kotlin/org/lmthermal/core/ImageCoordinates.kt",
    "core/src/main/kotlin/org/lmthermal/core/NativeRoi.kt",
    "core/src/main/kotlin/org/lmthermal/core/LatestFrameState.kt",
    "app/src/main/java/org/lmthermal/app/CameraText.kt",
    "app/src/main/java/org/lmthermal/app/CelsiusPresenter.kt",
    "app/src/main/java/org/lmthermal/app/RoiPresenter.kt",
    "app/src/main/java/org/lmthermal/app/MainActivity.kt",
    "app/src/main/java/org/lmthermal/app/ThermalScreen.kt",
    "app/src/main/java/org/lmthermal/camera/AndroidCameraCoordinator.kt",
]
SHARED_FILES += [str(path.relative_to(ROOT)) for path in
                 (ROOT / "core/src/main/kotlin/org/lmthermal/camera").rglob("*.kt")
                 if "ht301" not in path.parts]
FORBIDDEN = re.compile(
    r"\b(?:Ht301\w*|RadiometricMeasurement|RadiometricSession|NativeEquivalentThermometry|"
    r"FrameParameters|Raw14\w*|PreviewRenderer|Uvc\w*|YUYV|RAW14|384|288|292|224256|221184)\b"
)


def main():
    failures = []
    for relative in sorted(SHARED_FILES):
        # Keep line numbers while excluding explanatory KDoc/comments.
        source = (ROOT / relative).read_text(encoding="utf-8")
        source = re.sub(r"/\*.*?\*/|//[^\n]*", lambda match: "\n" * match[0].count("\n"), source, flags=re.S)
        for line, text in enumerate(source.splitlines(), 1):
            if FORBIDDEN.search(text):
                failures.append(f"{relative}:{line}: {text.strip()}")
    if failures:
        print("Camera-specific dependency in a shared boundary:", *failures, sep="\n", file=sys.stderr)
        return 1
    print(f"Camera module boundary check passed ({len(SHARED_FILES)} shared sources).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
