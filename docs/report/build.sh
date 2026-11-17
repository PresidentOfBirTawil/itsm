#!/usr/bin/env bash
# Builds the project report: Quell-Project-Report.md (from parts/ and the report notes),
# then Quell-Project-Report.pdf (Chromium) and Quell-Project-Report.docx (editable, pandoc).
# Needs: python3 with pypdf, pandoc, node with playwright.
set -euo pipefail
cd "$(dirname "$0")"

python3 build-report.py

# Front matter (cover and summary) goes before the table of contents; the rest is numbered by hand.
python3 - <<'PY'
text = open('Quell-Project-Report.md').read()
cut = text.index('# 1. Problem analysis')
open('.front.md', 'w').write(text[:cut])
open('.body.md', 'w').write(text[cut:])
PY
MD=markdown+pipe_tables+fenced_divs+bracketed_spans+implicit_figures+link_attributes
pandoc .front.md -f $MD -t html5 -o .front.html
pandoc .body.md -f $MD -t html5 --standalone --template=tools/template.html --toc --toc-depth=2 \
  --include-before-body=.front.html --css=tools/report.css --metadata pagetitle="Quell · Project report" \
  -o .report.html
node tools/render-pdf.js .report.html .cover.pdf .rest.pdf

# Page 1 (the cover) without the page-number footer, then everything after it.
python3 - <<'PY'
from pypdf import PdfReader, PdfWriter
cover, rest = PdfReader('.cover.pdf'), PdfReader('.rest.pdf')
out = PdfWriter()
out.add_page(cover.pages[0])
for page in rest.pages[1:]:
    out.add_page(page)
out.add_metadata({'/Title': 'Quell: Intelligent IT Service Management & Incident Resolution System. Project report',
                  '/Subject': 'CS F213 / MAC F212 Object-Oriented Programming, Semester I 2026-2027',
                  '/Author': 'Quell project team'})
with open('Quell-Project-Report.pdf', 'wb') as f:
    out.write(f)
print('pages:', len(out.pages))
PY

# An editable copy for filling in the team details in Word or LibreOffice.
pandoc Quell-Project-Report.md -f $MD -t docx --toc --toc-depth=2 --resource-path=. -o Quell-Project-Report.docx

rm -f .front.md .body.md .front.html .report.html .cover.pdf .rest.pdf
ls -la Quell-Project-Report.*
