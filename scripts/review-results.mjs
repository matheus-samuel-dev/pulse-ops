import fs from 'node:fs';
const evidence = 'docs/evidence';
const xmlFiles = fs.readdirSync('backend/target/surefire-reports').filter(file=>file.startsWith('TEST-')&&file.endsWith('.xml'));
const totals={tests:0,failures:0,errors:0,skipped:0};
for(const file of xmlFiles){const xml=fs.readFileSync(`backend/target/surefire-reports/${file}`,'utf8');const suite=xml.match(/<testsuite\b[^>]+>/)?.[0]??'';for(const field of Object.keys(totals))totals[field]+=Number(suite.match(new RegExp(`${field}="([^"]+)"`))?.[1]??0);}
const jacoco=fs.readFileSync('backend/target/site/jacoco/jacoco.xml','utf8');
function counters(xml){const result={};for(const type of ['LINE','BRANCH']){const all=[...xml.matchAll(new RegExp(`<counter type="${type}" missed="(\\d+)" covered="(\\d+)"`, 'g'))];const last=all.at(-1);if(last){const missed=Number(last[1]),covered=Number(last[2]);result[type]={covered,missed,percentage:Number((100*covered/(covered+missed)).toFixed(2))};}}return result;}
const serviceCounts={LINE:{covered:0,missed:0},BRANCH:{covered:0,missed:0}};
for (const match of jacoco.matchAll(/<package name="(com\/pulseops\/service[^"]*)">([\s\S]*?)<\/package>/g)){const counts=counters(match[2]);for(const type of ['LINE','BRANCH']){serviceCounts[type].covered+=counts[type]?.covered??0;serviceCounts[type].missed+=counts[type]?.missed??0;}}
for(const type of ['LINE','BRANCH']){const n=serviceCounts[type];n.percentage=Number((100*n.covered/(n.covered+n.missed)).toFixed(2));}
const front=fs.readFileSync(`${evidence}/frontend-tests.log`,'utf8');
const e2e=JSON.parse(fs.readFileSync(`${evidence}/e2e-results.json`,'utf8'));
const audit=JSON.parse(fs.readFileSync(`${evidence}/npm-audit-production.json`,'utf8'));
const results={date:new Date().toISOString(),backend:{...totals,reportFiles:xmlFiles.length,coverage:counters(jacoco),servicesCoverage:serviceCounts,build:fs.readFileSync(`${evidence}/backend-verify.log`,'utf8').includes('BUILD SUCCESS')},frontend:{tests:Number(front.match(/Tests\s+(\d+) passed/)?.[1]),files:Number(front.match(/Test Files\s+(\d+) passed/)?.[1]),coverage:JSON.parse(fs.readFileSync(`${evidence}/frontend-coverage/coverage-summary.json`,'utf8')).total,build:fs.readFileSync(`${evidence}/frontend-build.log`,'utf8').includes('built in'),lint:!fs.readFileSync(`${evidence}/frontend-lint.log`,'utf8').includes('error'),productionVulnerabilities:audit.metadata?.vulnerabilities},e2e:e2e.stats};
fs.writeFileSync(`${evidence}/validation-summary.json`,JSON.stringify(results,null,2));console.log(JSON.stringify(results,null,2));
