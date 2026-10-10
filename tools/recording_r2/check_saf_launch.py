"""Launch only the R2 test-APK probe and reject its former blank/off-screen layout.

Requires the built test APK, paired wireless ADB and an unlocked device. Does not
open USB, click a picker, alter pairing or save private screenshots/XML in Git.
"""
import argparse
import json
import re
import subprocess
import xml.etree.ElementTree as ET


def inspect(xml):
    """Require visible instructions and a nonempty on-screen destination button."""
    root = ET.fromstring(xml)
    nodes = list(root.iter("node"))
    buttons = [n for n in nodes if n.get("text") == "Choose R2 test destination"]
    instructions = [n for n in nodes if "R2 diagnostic SAF probe" in n.get("text", "")]
    if len(buttons) != 1 or not instructions:
        raise ValueError("Probe instructions/button absent; check unlocked test Activity")
    bounds = [tuple(map(int, re.findall(r"\d+", n.get("bounds", "")))) for n in [instructions[0], buttons[0]]]
    for rect in bounds:
        if len(rect) != 4 or rect[0] >= rect[2] or rect[1] >= rect[3]:
            raise ValueError("Empty probe control bounds")
    button = buttons[0]
    if button.get("enabled") != "true" or button.get("clickable") != "true":
        raise ValueError("Probe destination button not usable")
    if bounds[0][3] > bounds[1][1]:
        raise ValueError("Instructions overlap fixed destination button")
    return {"test_only_probe_visible": True, "instructions_bounds": bounds[0], "button_bounds": bounds[1]}


def main():
    """Use normal ADB against the selected existing paired device, without pairing."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial")
    args = parser.parse_args()
    adb = [args.adb] + (["-s", args.serial] if args.serial else [])
    def shell(*command):
        return subprocess.check_output(adb + ["shell", *command], text=True, timeout=30)
    result = shell("am", "start", "-W", "-n", "org.lmthermal.app.test/org.lmthermal.app.r2.R2SafActivity")
    if "Status: ok" not in result:
        raise RuntimeError("Test probe did not launch")
    shell("uiautomator", "dump", "/data/local/tmp/lmthermal-r2-saf-layout.xml")
    print(json.dumps(inspect(shell("cat", "/data/local/tmp/lmthermal-r2-saf-layout.xml"))))


if __name__ == "__main__":
    main()
