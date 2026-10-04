import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
const skip = new Set(['node_modules', 'target', 'dist', '.git', '.review', 'evidence']);
function walk(dir = '.') {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
    if (skip.has(entry.name)) return [];
    const file = path.join(dir, entry.name).replaceAll('\\', '/');
    return entry.isDirectory() ? walk(file) : [file];
  });
}
const files = Object.fromEntries(walk().filter(file => !file.endsWith('.log')).map(file => [file, crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')]));
fs.mkdirSync('docs/evidence', { recursive: true });
const baseline = 'docs/evidence/baseline-files.json';
if (!fs.existsSync(baseline)) fs.writeFileSync(baseline, JSON.stringify(files, null, 2));
else {
  const previous = JSON.parse(fs.readFileSync(baseline, 'utf8'));
  fs.writeFileSync('docs/evidence/changed-files.json', JSON.stringify({ created: Object.keys(files).filter(f => !previous[f] || f === 'scripts/review-manifest.mjs'), changed: Object.keys(files).filter(f => previous[f] && previous[f] !== files[f] && f !== 'scripts/review-manifest.mjs'), removed: Object.keys(previous).filter(f => !files[f]) }, null, 2));
}
