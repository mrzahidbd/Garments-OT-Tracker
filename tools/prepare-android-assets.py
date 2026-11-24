"""Stage only the two app pages and replace the online UI dependencies on Android."""

from pathlib import Path
import shutil


root = Path(__file__).resolve().parents[1]
assets = root / "app/src/main/assets"
assets.mkdir(parents=True, exist_ok=True)
page = (root / "index.html").read_text(encoding="utf-8")
start = page.index("    <!-- Tailwind CSS -->")
end = page.index("    <style>", start)
local_css = '    <link rel="stylesheet" href="tracker.css">\n'
(assets / "index.html").write_text(page[:start] + local_css + page[end:], encoding="utf-8")
for name in ("learning.html", "platform-files.js"):
    shutil.copyfile(root / name, assets / name)
