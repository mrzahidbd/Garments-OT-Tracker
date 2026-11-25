"""Exercise the real offline release in instrumentation signed with its disposable CI key."""
import os
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
from zipfile import ZipFile

root = Path(__file__).resolve().parent / "android-check"
sdk = Path(os.environ["ANDROID_HOME"])
tools = sdk / "build-tools/36.0.0"
platform = sdk / "platforms/android-36/android.jar"
key = Path(os.environ["RUNNER_TEMP"]) / "device-test.p12"

def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)

with TemporaryDirectory() as scratch:
    work = Path(scratch)
    classes, dex = work / "classes", work / "dex"
    classes.mkdir()
    dex.mkdir()
    run("javac", "-encoding", "UTF-8", "-source", "8", "-target", "8", "-cp", platform,
        "-d", classes, root / "PhoneCheck.java")
    run("jar", "cf", work / "check.jar", "-C", classes, ".")
    run(tools / "d8", "--min-api", "23", "--lib", platform, "--output", dex, work / "check.jar")
    apk = work / "check.apk"
    run(tools / "aapt2", "link", "-I", platform, "--manifest", root / "AndroidManifest.xml", "-o", apk)
    with ZipFile(apk, "a") as archive:
        archive.write(dex / "classes.dex", "classes.dex")
    aligned = work / "aligned.apk"
    run(tools / "zipalign", "-f", "4", apk, aligned)
    run(tools / "apksigner", "sign", "--ks", key, "--ks-key-alias", "device-check",
        "--ks-pass", "env:OT_DEVICE_KEY_PASSWORD", aligned)
    run("adb", "install", "-r", "-t", aligned)
    run("adb", "shell", "svc", "wifi", "disable")
    run("adb", "shell", "svc", "data", "disable")
    result = subprocess.run(
        ["adb", "shell", "am", "instrument", "-w", "-r", "com.mrzahidbd.garmentsot.check/.PhoneCheck"],
        capture_output=True, check=True, text=True, timeout=130)
    print(result.stdout, flush=True)
    if "PASS: Android 16" not in result.stdout or "INSTRUMENTATION_CODE: -1" not in result.stdout:
        raise SystemExit("Release device check failed")
