// Prints the report HTML to PDF with Chromium: the cover without a footer, the rest with page numbers.
const { chromium } = require('playwright');
const path = require('path');
const [htmlFile, coverPdf, bodyPdf] = process.argv.slice(2);
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.goto('file://' + path.resolve(htmlFile), { waitUntil: 'networkidle' });
  await page.evaluate(() => document.fonts.ready);
  const margin = { top: '18mm', bottom: '20mm', left: '16mm', right: '16mm' };
  await page.pdf({ path: coverPdf, printBackground: true, pageRanges: '1', preferCSSPageSize: true });
  await page.pdf({
    path: bodyPdf, printBackground: true, preferCSSPageSize: true, displayHeaderFooter: true,
    headerTemplate: '<div></div>',
    footerTemplate: '<div style="font-family: Inter, sans-serif; font-size: 7.5px; color: #71717a; width: 100%; padding: 0 16mm; display: flex; justify-content: space-between;"><span>Quell · Intelligent IT Service Management · Project report</span><span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>'
  });
  await browser.close();
})();
