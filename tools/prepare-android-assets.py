"""Stage the tracker and its local scripts for the Android build."""

from pathlib import Path
import shutil


root = Path(__file__).resolve().parents[1]
assets = root / "app/build/generated/trackerAssets"
assets.mkdir(parents=True, exist_ok=True)
page = (root / "index.html").read_text(encoding="utf-8")
start = page.index("    <!-- Tailwind CSS -->")
end = page.index("    <style>", start)
local_css = '    <link rel="stylesheet" href="tracker.css">\n'
(assets / "index.html").write_text(page[:start] + local_css + page[end:], encoding="utf-8")
for name in ("platform-files.js", "payslip.js"):
    shutil.copyfile(root / name, assets / name)
