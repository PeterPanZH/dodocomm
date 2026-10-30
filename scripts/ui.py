#!/usr/bin/env python3
"""Tiny adb UI automation helper for sms2bark verification (uiautomator based).

Usage:
  ui.py dump [out.xml]             # dump current UI hierarchy, print path
  ui.py nodes [--clickable]        # list nodes with text/content-desc + center coords
  ui.py find <text>                # print "x y" center of first node whose text/desc contains <text>
  ui.py tap-text <text>            # find + tap that node, print what was tapped
  ui.py tap <x> <y>                # raw tap
  ui.py swipe <x1> <y1> <x2> <y2> [ms]
  ui.py wait-text <text> [timeout] # poll dump until text appears (default 15s)
  ui.py press <keycode>            # e.g. BACK, HOME
  ui.py screenshot <file.png>      # adb exec-out screencap
All adb calls respect $ADB if set; else use ANDROID_HOME/platform-tools/adb.
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

SMS2BARK = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WS = os.path.dirname(SMS2BARK)
ADB = os.environ.get("ADB") or os.path.join(
    os.environ.get("ANDROID_HOME", os.path.expanduser("~/Library/Android/sdk")),
    "platform-tools", "adb",
)
VERIFY = os.environ.get("VERIFY") or os.path.join(SMS2BARK, "verify")


def adb(*args, timeout=30):
    return subprocess.run([ADB, *args], capture_output=True, text=True, timeout=timeout)


def dump(out_path=None):
    os.makedirs(VERIFY, exist_ok=True)
    out_path = out_path or os.path.join(VERIFY, "ui.xml")
    tmp = "/sdcard/window_dump.xml"
    r = adb("shell", "uiautomator", "dump", tmp, timeout=60)
    if r.returncode != 0:
        sys.stderr.write("uiautomator dump failed: " + (r.stderr or r.stdout) + "\n")
        return None
    with open(out_path, "wb") as f:
        p = subprocess.run([ADB, "exec-out", "cat", tmp], capture_output=True, timeout=60)
        f.write(p.stdout)
    return out_path


def iter_nodes(path):
    try:
        tree = ET.parse(path)
    except Exception as e:
        sys.stderr.write("parse failed: %s\n" % e)
        return []
    out = []
    for n in tree.iter("node"):
        text = n.get("text") or ""
        desc = n.get("content-desc") or ""
        bounds = n.get("bounds") or ""
        clickable = n.get("clickable") == "true"
        cls = n.get("class") or ""
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        cx = cy = None
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
        out.append({"text": text, "desc": desc, "clickable": clickable, "cls": cls,
                    "editable": "EditText" in cls, "bounds": bounds, "x": cx, "y": cy})
    return out


def find(nodes, needle):
    needle = needle.lower()
    for n in nodes:
        if needle in (n["text"] or "").lower() or needle in (n["desc"] or "").lower():
            return n
    return None


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    cmd = sys.argv[1]
    if cmd == "dump":
        p = dump(sys.argv[2] if len(sys.argv) > 2 else None)
        print(p)
        return 0 if p else 1
    if cmd == "nodes":
        clickable = "--clickable" in sys.argv
        p = dump()
        nodes = iter_nodes(p)
        for n in nodes:
            if not (n["text"] or n["desc"]):
                continue
            if clickable and not n["clickable"]:
                continue
            print("(%4d,%4d) click=%d text=%r desc=%r" % (n["x"] or -1, n["y"] or -1,
                  n["clickable"], n["text"], n["desc"]))
        return 0
    if cmd == "find":
        nodes = iter_nodes(dump())
        n = find(nodes, sys.argv[2])
        if not n:
            print("NOT_FOUND")
            return 1
        print("%d %d | text=%r desc=%r bounds=%s" % (n["x"], n["y"], n["text"], n["desc"], n["bounds"]))
        return 0
    if cmd == "tap-text":
        nodes = iter_nodes(dump())
        n = find(nodes, sys.argv[2])
        if not n:
            print("NOT_FOUND: %s" % sys.argv[2])
            return 1
        adb("shell", "input", "tap", str(n["x"]), str(n["y"]))
        print("TAPPED %r at (%d,%d)" % (n["text"] or n["desc"], n["x"], n["y"]))
        return 0
    if cmd == "tap":
        adb("shell", "input", "tap", sys.argv[2], sys.argv[3])
        print("TAPPED (%s,%s)" % (sys.argv[2], sys.argv[3]))
        return 0
    if cmd == "swipe":
        ms = sys.argv[6] if len(sys.argv) > 6 else "300"
        adb("shell", "input", "swipe", *sys.argv[2:6], ms)
        print("SWIPED")
        return 0
    if cmd == "press":
        adb("shell", "input", "keyevent", sys.argv[2])
        print("PRESSED %s" % sys.argv[2])
        return 0
    if cmd == "wait-text":
        needle = sys.argv[2]
        timeout = float(sys.argv[3]) if len(sys.argv) > 3 else 15.0
        t0 = time.time()
        while time.time() - t0 < timeout:
            n = find(iter_nodes(dump()), needle)
            if n:
                print("FOUND %r at (%d,%d)" % (n["text"] or n["desc"], n["x"], n["y"]))
                return 0
            time.sleep(0.7)
        print("TIMEOUT waiting for %r" % needle)
        return 1
    if cmd == "screenshot":
        os.makedirs(os.path.dirname(os.path.abspath(sys.argv[2])), exist_ok=True)
        with open(sys.argv[2], "wb") as f:
            r = subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True, timeout=60)
            f.write(r.stdout)
        print(os.path.abspath(sys.argv[2]))
        return 0
    if cmd == "edits":
        nodes = iter_nodes(dump())
        idx = 0
        for n in nodes:
            if not n["editable"]:
                continue
            print("[%d] (%4d,%4d) text=%r bounds=%s" % (idx, n["x"], n["y"], n["text"], n["bounds"]))
            idx += 1
        return 0
    if cmd == "fill-field":
        # fill-field <edit-index> <text> [deletes]   (taps the EditText, clears it, types text)
        idx = int(sys.argv[2])
        value = sys.argv[3]
        deletes = int(sys.argv[4]) if len(sys.argv) > 4 else 40
        nodes = [n for n in iter_nodes(dump()) if n["editable"]]
        if idx >= len(nodes):
            print("NO_FIELD index=%d found=%d" % (idx, len(nodes)))
            return 1
        n = nodes[idx]
        adb("shell", "input", "tap", str(n["x"]), str(n["y"]))
        time.sleep(0.6)
        adb("shell", "input", "keyevent", "123")  # MOVE_END
        adb("shell", "input", "keyevent", *(["67"] * deletes))  # DEL xN
        adb("shell", "input", "text", value)
        time.sleep(0.4)
        print("FILLED[%d] %r (was %r at %s)" % (idx, value, n["text"], n["bounds"]))
        return 0
    if cmd == "tap-tab":
        # tap-tab <label>: taps the bottom-navigation item whose text matches (y > 2100)
        nodes = iter_nodes(dump())
        label = sys.argv[2]
        for n in nodes:
            if (n["y"] or 0) > 2100 and label in (n["text"], n["desc"], (n["text"] + n["desc"])):
                adb("shell", "input", "tap", str(n["x"]), str(n["y"]))
                print("TAPPED_TAB %r at (%d,%d)" % (n["text"] or n["desc"], n["x"], n["y"]))
                return 0
        print("TAB_NOT_FOUND %r" % label)
        return 1
    if cmd == "scroll-to":
        # scroll-to <text> [max_swipes]: swipe up until text is visible in the dump
        needle = sys.argv[2]
        max_swipes = int(sys.argv[3]) if len(sys.argv) > 3 else 6
        for i in range(max_swipes + 1):
            n = find(iter_nodes(dump()), needle)
            if n:
                print("VISIBLE %r at (%d,%d) after %d swipes" % (n["text"] or n["desc"], n["x"], n["y"], i))
                return 0
            adb("shell", "input", "swipe", "540", "1900", "540", "800", "300")
            time.sleep(0.8)
        print("NOT_FOUND %r after %d swipes" % (needle, max_swipes))
        return 1
    if cmd == "clear-field":
        idx = int(sys.argv[2])
        deletes = int(sys.argv[3]) if len(sys.argv) > 3 else 40
        nodes = [n for n in iter_nodes(dump()) if n["editable"]]
        if idx >= len(nodes):
            print("NO_FIELD index=%d found=%d" % (idx, len(nodes)))
            return 1
        n = nodes[idx]
        adb("shell", "input", "tap", str(n["x"]), str(n["y"]))
        time.sleep(0.5)
        adb("shell", "input", "keyevent", "123")
        adb("shell", "input", "keyevent", *(["67"] * deletes))
        print("CLEARED[%d]" % idx)
        return 0
    print(__doc__)
    return 1


if __name__ == "__main__":
    sys.exit(main())
