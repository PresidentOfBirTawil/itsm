// Renders the report HTML to PDF with Chromium (Playwright), in two passes:
// the first pass finds the page of every heading from the PDF bookmarks,
// the second pass writes those page numbers into the contents and the "at a glance" page.
//
// Usage: node style/render.js report.html Quell-Project-Report.pdf
// Needs: Playwright (Chromium) for Node, and Python 3 with pypdf.

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const [input, output] = process.argv.slice(2);
if (!input || !output) {
  console.error('Usage: node style/render.js <report.html> <output.pdf>');
  process.exit(2);
}

const OUTLINE_PY = `
import sys, json
from pypdf import PdfReader
r = PdfReader(sys.argv[1])
out = []
def walk(items):
    for it in items:
        if isinstance(it, list):
            walk(it)
        else:
            out.append([it.title, r.get_destination_page_number(it) + 1])
walk(r.outline)
print(json.dumps({"pages": len(r.pages), "outline": out}))
`;

const METADATA_PY = `
import sys, json
from pypdf import PdfReader, PdfWriter
from pypdf.generic import NameObject, TextStringObject
src, dst, titles_file = sys.argv[1], sys.argv[2], sys.argv[3]
titles = json.load(open(titles_file))
writer = PdfWriter(clone_from=PdfReader(src))
writer.add_metadata({
    "/Title": "Quell: Intelligent IT Service Management and Incident Resolution System",
    "/Author": "Yatharth Soni (2025ADPS1461H), Group 94",
    "/Subject": "CS F213 / MAC F212 Object-Oriented Programming, Semester I 2026-2027: project report",
    "/Keywords": "IT service management, incident management, object-oriented design, Java, Spring Boot",
})
# Chromium sometimes doubles a heading's text in its bookmark, or joins wrapped lines without
# a space; put back the heading text as it is in the document (same order as the outline).
index = 0
def fix(item):
    global index
    while item is not None:
        item = item.get_object()
        if index < len(titles) and titles[index]:
            item[NameObject("/Title")] = TextStringObject(titles[index])
        index += 1
        if "/First" in item:
            fix(item["/First"])
        item = item.get("/Next")
root = writer._root_object
if "/Outlines" in root and "/First" in root["/Outlines"].get_object():
    fix(root["/Outlines"].get_object()["/First"])
writer.page_mode = "/UseOutlines"
writer.write(dst)
`;

function outlineOf(pdf) {
  return JSON.parse(execFileSync('python3', ['-c', OUTLINE_PY, pdf], { encoding: 'utf8' }));
}

// Builds the contents page and the page references; with no page numbers yet, shows a dash.
function prepare(pages) {
  const norm = (s) => s.replace(/\s+/g, ' ').trim();
  // Chromium may double a bookmark's text or drop the spaces between wrapped lines: compare without spaces.
  const key = (s) => s.replace(/\s+/g, '');
  const same = (heading, title) => { const h = key(heading), t = key(title); return t === h || t === h + h; };
  const headings = [...document.querySelectorAll('h1, h2, h3, h4')];
  const titles = [];
  if (pages) {
    let from = 0;
    for (const [title, page] of pages) {
      const i = headings.findIndex((h, k) => k >= from && same(h.textContent, title));
      if (i >= 0) { headings[i].dataset.page = page; from = i + 1; titles.push(norm(headings[i].textContent)); }
      else titles.push(null);
    }
  }
  const pageOf = (el) => (el && el.dataset.page) || '—';

  const toc = document.querySelector('.toc');
  if (toc) {
    toc.innerHTML = '';
    let chapter = 0, appendix = 0, part = '';
    for (const section of document.querySelectorAll('section.level1.chapter, section.level1.appendix')) {
      const h1 = section.querySelector(':scope > h1');
      const isAppendix = section.classList.contains('appendix');
      const thisPart = isAppendix ? 'Appendices' : 'Report';
      if (thisPart !== part) {
        part = thisPart;
        const label = document.createElement('div');
        label.className = 'toc-part';
        label.textContent = part;
        toc.appendChild(label);
      }
      const number = isAppendix ? String.fromCharCode(65 + appendix++) : String(++chapter).padStart(2, '0');
      const entry = document.createElement('a');
      entry.className = 'toc-entry' + (isAppendix ? ' appx' : '');
      entry.href = '#' + section.id;
      const subs = isAppendix ? [] : [...section.querySelectorAll('section.level2 > h2')].map((h) => norm(h.textContent));
      entry.innerHTML =
        `<span class="n">${number}</span><span class="t"></span><span class="p">${pageOf(h1)}</span>` +
        (subs.length ? '<span class="subs"></span>' : '');
      entry.querySelector('.t').textContent = norm(h1.textContent);
      if (subs.length) entry.querySelector('.subs').textContent = subs.join('  ·  ');
      toc.appendChild(entry);
    }
  }
  for (const ref of document.querySelectorAll('[data-page-of]')) {
    const target = document.querySelector(ref.dataset.pageOf);
    const heading = target && (target.matches('h1, h2, h3, h4') ? target : target.querySelector('h1, h2, h3, h4'));
    ref.textContent = pageOf(heading);
  }
  return titles;
}

async function load(page) {
  await page.goto('file://' + path.resolve(input), { waitUntil: 'load' });
  await page.evaluate(async () => {
    await document.fonts.ready;
    await Promise.all([...document.images].map((img) =>
      img.complete ? null : new Promise((ok) => { img.onload = img.onerror = ok; })));
  });
  const broken = await page.evaluate(() =>
    [...document.images].filter((img) => !img.naturalWidth).map((img) => img.getAttribute('src')));
  if (broken.length) throw new Error('Images that did not load: ' + broken.join(', '));
}

const PDF_OPTIONS = { preferCSSPageSize: true, printBackground: true, outline: true, tagged: true };

(async () => {
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage();
    await load(page);

    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'quell-report-'));
    const first = path.join(tmp, 'pass1.pdf');
    await page.evaluate(prepare, null);
    await page.pdf({ ...PDF_OPTIONS, path: first });
    const pass1 = outlineOf(first);

    await page.evaluate(prepare, pass1.outline);
    const second = path.join(tmp, 'pass2.pdf');
    await page.pdf({ ...PDF_OPTIONS, path: second });
    const pass2 = outlineOf(second);

    const moved = pass2.outline.filter(([t, p], i) => !pass1.outline[i] || pass1.outline[i][1] !== p);
    if (moved.length) console.warn('Warning: page numbers moved in the second pass:', moved.slice(0, 5));
    const titles = await page.evaluate(prepare, pass2.outline);
    const unmatched = pass2.outline.filter((entry, i) => !titles[i]).map(([t]) => t);
    if (unmatched.length) console.warn('Warning: bookmarks without a heading:', unmatched);
    const titlesFile = path.join(tmp, 'titles.json');
    fs.writeFileSync(titlesFile, JSON.stringify(titles));

    execFileSync('python3', ['-c', METADATA_PY, second, output, titlesFile]);
    fs.rmSync(tmp, { recursive: true, force: true });
    console.log(`${output}: ${pass2.pages} pages, ${pass2.outline.length} bookmarks`);
  } finally {
    await browser.close();
  }
})().catch((e) => { console.error(e); process.exit(1); });
