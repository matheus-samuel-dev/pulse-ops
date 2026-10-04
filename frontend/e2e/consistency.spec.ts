import {test,expect,request} from '@playwright/test';
import {randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import fs from 'node:fs/promises';
test('conta nova: monitoramento no backend, coerência e persistência após reinício',async({browser,baseURL})=>{
 const email=`consistency-${randomUUID()}@example.org`,password='Controlled@2026';
 let context=await browser.newContext({baseURL});let page=await context.newPage();
 const browserCheckRequests:string[]=[];page.on('request',r=>{if(r.method()==='POST'&&r.url().endsWith('/checks'))browserCheckRequests.push(r.url());});
 await page.goto('/cadastro');await page.getByLabel('Nome',{exact:true}).fill('Operador de consistência');await page.getByLabel('E-mail',{exact:true}).fill(email);await page.getByLabel('Senha',{exact:true}).fill(password);await page.getByLabel('Confirmar senha').fill(password);await page.getByRole('button',{name:'Criar conta',exact:true}).click();await expect(page.getByRole('heading',{name:'Visão geral',exact:true})).toBeVisible();
 const session=await page.evaluate(()=>JSON.parse(sessionStorage.getItem('pulseops.session')!));
 const api=await request.newContext({baseURL,extraHTTPHeaders:{Authorization:`Bearer ${session.token}`}});
 const get=async(path:string)=>{const r=await api.get(`/api${path}`);expect(r.status()).toBe(200);return r.json();};
 const empty=await get('/dashboard');expect(empty.summary.monitoredSystems).toBe(0);expect(empty.summary.averageAvailability??null).toBeNull();expect(empty.summary.averageCoverage??null).toBeNull();expect(empty.latency).toEqual([]);expect(await get('/incidents')).toEqual([]);expect(await get('/deployments')).toEqual([]);expect(await get('/quality/history')).toEqual([]);expect((await get('/integrations')).summary.connected).toBe(0);
 await page.goto('/sistemas');await page.getByRole('button',{name:'Cadastrar sistema',exact:true}).first().click();let dialog=page.getByRole('dialog');await dialog.getByLabel('Nome do sistema').fill('Serviço observado de verdade');await dialog.getByLabel('URL base').fill('http://fixture:8080');await dialog.getByLabel('Endpoint de saúde').fill('/health');await dialog.getByLabel('Intervalo de monitoramento').fill('30');await dialog.getByRole('button',{name:'Cadastrar sistema',exact:true}).click();await expect(dialog).toBeHidden();
 const systems=await get('/systems');expect(systems).toHaveLength(1);const id=systems[0].id;
 // No browser stays open to trigger health checks. Only the server scheduler/listener can write them.
 expect(browserCheckRequests).toEqual([]);await context.close();
 await expect.poll(async()=> (await get(`/systems/${id}/checks`)).totalElements,{timeout:30_000}).toBeGreaterThan(0);
 const initial=await get(`/systems/${id}`);expect(initial.status).toBe('OPERATIONAL');expect(initial.lastCheck.httpStatus).toBe(200);expect(initial.lastCheck.responseTimeMs).toBeGreaterThanOrEqual(0);
 context=await browser.newContext({baseURL});page=await context.newPage();
 const login=async()=>{await page.goto('/login');await page.getByLabel('E-mail',{exact:true}).fill(email);await page.getByLabel('Senha',{exact:true}).fill(password);await page.getByRole('button',{name:'Entrar no PulseOps'}).click();await expect(page.getByRole('heading',{name:'Visão geral',exact:true})).toBeVisible();};await login();
 await page.goto(`/sistemas/${id}`);await page.getByRole('button',{name:'Editar',exact:true}).click();dialog=page.getByRole('dialog');await dialog.getByLabel('Endpoint de saúde').fill('/status/500');await dialog.getByRole('button',{name:'Salvar alterações'}).click();await expect(dialog).toBeHidden();
 await expect.poll(async()=>(await get(`/systems/${id}/checks`)).content[0]?.httpStatus,{timeout:30_000}).toBe(500);
 for(let i=0;i<2;i++){const r=await api.post(`/api/systems/${id}/checks`);expect(r.status()).toBe(201);}
 const before={system:await get(`/systems/${id}`),checks:await get(`/systems/${id}/checks`),incidents:await get('/incidents'),dashboard:await get('/dashboard'),report:await get('/reports/operational?period=24h'),events:await get(`/events?systemId=${id}&size=100`)};
 expect(before.system.status).toBe('DOWN');expect(before.incidents).toHaveLength(1);expect(before.dashboard.summary.openIncidents).toBe(1);expect(before.report.kpis.activeIncidents).toBe(1);expect(before.report.kpis.totalHealthChecks).toBe(before.checks.totalElements);expect(before.dashboard.health[0].lastCheckedAt).toBe(before.system.lastCheck.checkedAt);expect(before.events.content.some((e:{type:string})=>e.type==='SYSTEM_DOWN')).toBe(true);
 await page.reload();await expect(page.getByRole('heading',{name:'Serviço observado de verdade',exact:true})).toBeVisible();await expect(page.getByRole('table',{name:'Histórico de verificações'}).getByText('500',{exact:true}).first()).toBeVisible();await page.screenshot({path:'../docs/evidence/consistency/estado-real-desktop.png',fullPage:true});await context.close();
 execFileSync('docker',['compose','-f','docker-compose.yml','-f','docker-compose.test.yml','-f','docker-compose.consistency.yml','restart','backend','frontend','postgres'],{cwd:'..',timeout:90_000,stdio:'pipe',windowsHide:true});
 await expect.poll(async()=>{try{return (await api.get('/api/account/me',{timeout:3000})).status();}catch{return 0;}},{timeout:90_000,intervals:[1000,2000,3000]}).toBe(200);
 context=await browser.newContext({baseURL});page=await context.newPage();await login();
 const after={system:await get(`/systems/${id}`),checks:await get(`/systems/${id}/checks`),incidents:await get('/incidents'),dashboard:await get('/dashboard'),report:await get('/reports/operational?period=24h')};
 expect(after.system.id).toBe(before.system.id);expect(after.checks.totalElements).toBeGreaterThanOrEqual(before.checks.totalElements);expect(after.checks.content.some((c:{id:string})=>c.id===before.system.lastCheck.id)).toBe(true);expect(after.incidents[0].id).toBe(before.incidents[0].id);expect(after.dashboard.summary.openIncidents).toBe(after.report.kpis.activeIncidents);expect(after.report.kpis.totalHealthChecks).toBe(after.checks.totalElements);expect(after.dashboard.health[0].lastCheckedAt).toBe(after.system.lastCheck.checkedAt);
 await fs.writeFile('../docs/evidence/consistency/restart-persistence.json',JSON.stringify({email,systemId:id,initialCheck:initial.lastCheck,browserCheckRequests,beforeChecks:before.checks.totalElements,afterChecks:after.checks.totalElements,incidentId:after.incidents[0].id,backendOnlyFirstCheck:true,consistencyAfterRestart:true},null,2));
 await page.goto(`/sistemas/${id}`);await expect(page.getByRole('heading',{name:'Serviço observado de verdade',exact:true})).toBeVisible();await expect(page.getByRole('table',{name:'Histórico de verificações'}).getByText('500',{exact:true}).first()).toBeVisible();await page.setViewportSize({width:390,height:844});await page.screenshot({path:'../docs/evidence/consistency/estado-real-mobile.png',fullPage:true});await context.close();await api.dispose();
});
