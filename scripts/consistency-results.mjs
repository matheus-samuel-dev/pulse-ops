import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const evidence = path.join(root, 'docs/evidence/consistency');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const backendLog = read('docs/evidence/consistency/backend-verify-final.log');
const backendTotals = [...backendLog.matchAll(/Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)/g)].at(-1);
if (!backendTotals || !backendLog.includes('BUILD SUCCESS')) throw new Error('Backend verify did not succeed');
const coverageRows = read('backend/target/site/jacoco/jacoco.csv').trim().split(/\r?\n/).slice(1).map(row => row.split(','));
const coverage = (missedIndex, coveredIndex) => {
  const missed = coverageRows.reduce((sum, row) => sum + Number(row[missedIndex]), 0);
  const covered = coverageRows.reduce((sum, row) => sum + Number(row[coveredIndex]), 0);
  return { missed, covered, percent: Number((100 * covered / (covered + missed)).toFixed(2)) };
};
const frontendLog = read('docs/evidence/consistency/frontend-coverage.log');
const frontendTests = frontendLog.match(/Tests\s+(\d+) passed \((\d+)\)/);
const frontendCoverage = Object.fromEntries([...frontendLog.matchAll(/(Statements|Branches|Functions|Lines)\s+:\s+([\d.]+)%/g)].map(match => [match[1].toLowerCase(), Number(match[2])]));
const e2e = filename => {
  const data = JSON.parse(read(`docs/evidence/consistency/${filename}`));
  return { passed: data.stats.expected, failed: data.stats.unexpected, flaky: data.stats.flaky, skipped: data.stats.skipped, durationMs: data.stats.duration };
};
const results = {
  generatedAt: new Date().toISOString(),
  backend: { tests: Number(backendTotals[1]), failures: Number(backendTotals[2]), errors: Number(backendTotals[3]), skipped: Number(backendTotals[4]), coverage: { instructions: coverage(3, 4), branches: coverage(5, 6), lines: coverage(7, 8) } },
  frontend: { passed: Number(frontendTests?.[1]), total: Number(frontendTests?.[2]), coverage: frontendCoverage },
  operationsE2e: e2e('e2e-results.json'),
  consistencyE2e: e2e('e2e-consistency.json'),
  browserAudit: JSON.parse(read('docs/evidence/consistency/browser-audit.json')),
  restart: JSON.parse(read('docs/evidence/consistency/restart-persistence.json')),
};
fs.writeFileSync(path.join(evidence, 'final-results.json'), JSON.stringify(results, null, 2));

const baseline = JSON.parse(read('docs/evidence/consistency/baseline.json'));
const current = {};
function walk(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    if (entry.isSymbolicLink()) continue;
    const absolute = path.join(directory, entry.name);
    const relative = path.relative(root, absolute).replaceAll('\\', '/');
    if (['node_modules', 'target', 'dist', '.git', '.review', '.review-final'].includes(entry.name) || relative.startsWith('docs/evidence') || relative.startsWith('frontend/coverage')) continue;
    if (entry.isDirectory()) walk(absolute);
    else current[relative] = crypto.createHash('sha256').update(fs.readFileSync(absolute)).digest('hex');
  }
}
walk(root);
const inventory = {
  created: Object.keys(current).filter(file => !(file in baseline)).sort(),
  modified: Object.keys(current).filter(file => file in baseline && current[file] !== baseline[file]).sort(),
  removed: Object.keys(baseline).filter(file => !(file in current)).sort(),
};
fs.writeFileSync(path.join(evidence, 'changed-files.json'), JSON.stringify(inventory, null, 2));
const sections = Object.entries(inventory).map(([kind, files]) => `## ${{created:'Criados',modified:'Alterados',removed:'Removidos'}[kind]} (${files.length})\n\n${files.length ? files.map(file => `- \`${file}\``).join('\n') : 'Nenhum.'}`);
fs.writeFileSync(path.join(root, 'docs/arquivos-consistencia.md'), '# Inventário da revisão de consistência\n\nComparação SHA-256 com o estado recebido nesta revisão, antes das alterações. Arquivos de build, dependências, scripts temporários e evidências geradas ficam fora do inventário de código. As evidências desta execução estão em `docs/evidence/consistency`. O inventário da revisão anterior permanece em `docs/revisao-pulseops.md`.\n\n' + sections.join('\n\n') + '\n');
console.log(JSON.stringify({ backend: results.backend, frontend: results.frontend, operations: results.operationsE2e, consistency: results.consistencyE2e, visits: results.browserAudit.evidence.length, inventory: Object.fromEntries(Object.entries(inventory).map(([key, files]) => [key, files.length])) }, null, 2));
