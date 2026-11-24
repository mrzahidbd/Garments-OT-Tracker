"""Check the release UI and Android file picker on the clean CI emulator."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True, timeout=45)


def read_window():
    adb("shell", "uiautomator", "dump", "/sdcard/ot-window.xml")
    return ET.fromstring(adb("exec-out", "cat", "/sdcard/ot-window.xml"))


def find_label(label, tap=False, scroll=False):
    for attempt in range(10):
        window = read_window()
        for node in window.iter("node"):
            text = node.get("text", "") + " " + node.get("content-desc", "")
            if label not in text:
                continue
            x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", node.get("bounds", "")))
            if x2 <= x1 or y2 <= y1:
                continue
            if tap:
                adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            return
        if scroll and attempt:
            size = re.findall(r"(\d+)x(\d+)", adb("shell", "wm", "size"))[-1]
            w, h = map(int, size)
            adb("shell", "input", "swipe", str(w // 2), str(h * 3 // 4),
                str(w // 2), str(h // 3), "300")
        time.sleep(0.7)
    labels = [n.get("text") or n.get("content-desc") for n in window.iter("node")]
    print("Last visible labels:", [x for x in labels if x][-25:])
    raise RuntimeError("Visible control not found: " + label)


adb("shell", "svc", "wifi", "disable")
adb("shell", "svc", "data", "disable")
start = adb("shell", "am", "start", "-W", "-n", "com.mrzahidbd.garmentsot/.MainActivity")
if "Status: ok" not in start:
    raise RuntimeError(start)
find_label("আমার হাজিরা ও OT")
find_label("শেখা শুরু করুন", tap=True)
find_label("হাজিরা ও OT অ্যাপে ফিরুন")
adb("shell", "input", "keyevent", "KEYCODE_BACK")
find_label("আমার হাজিরা ও OT")
find_label("সেটিংস", tap=True)
find_label("ব্যাকআপ নিন", tap=True, scroll=True)
for attempt in range(8):
    window = read_window()
    if any("documentsui" in n.get("package", "").lower() for n in window.iter("node")):
        print("Offline home, learning, back navigation and Android backup picker passed.")
        break
    time.sleep(1)
else:
    print(adb("shell", "dumpsys", "window", "displays")[-3000:])
    raise RuntimeError("Android's document picker did not open")
