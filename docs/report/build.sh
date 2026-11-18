#!/usr/bin/env bash
# Builds Quell-Project-Report.pdf from the Markdown files in content/.
#
# Needs: pandoc 3, Node.js with Playwright (Chromium), Python 3 with pypdf,
# and the Geist and Inter fonts installed (the diagrams are SVG and use them by name).
# The diagrams themselves are rendered from docs/diagrams/*.puml with PlantUML.
set -euo pipefail
cd "$(dirname "$0")"

pandoc content/*.md \
  --from markdown \
  --to html5 \
  --standalone \
  --template style/template.html \
  --section-divs \
  --columns 100 \
  --output report.html

node style/render.js report.html Quell-Project-Report.pdf
