import fs from 'node:fs';
import { createRequire } from 'node:module';
const require = createRequire(new URL('../frontend/package.json', import.meta.url));
const { chromium } = require('@playwright/test');
const browser = await chromium.launch({headless:true});
const page = await browser.newPage({viewport:{width:1366,height:900}});
const errors=[];const network=[];
page.on('pageerror',error=>errors.push(error.message));
page.on('console',message=>{if(['error','warning'].includes(message.type()))errors.push(message.text());});
page.on('response',response=>{if(response.status()>=400)network.push({status:response.status(),url:response.url()});});
try {
  await page.goto('http://localhost:3000/login');
  await page.getByLabel('E-mail',{exact:true}).fill(process.env.PULSEOPS_REVIEW_EMAIL);
  await page.getByLabel('Senha',{exact:true}).fill(process.env.PULSEOPS_REVIEW_PASSWORD);
  await page.getByRole('button',{name:'Entrar no PulseOps'}).click();
  await page.getByRole('heading',{name:'Visão geral',exact:true}).waitFor({timeout:30000});
  await page.goto('http://localhost:3000/sistemas');
  await page.getByRole('link',{name:'Abrir detalhes do sistema Portfólio público'}).click();
  await page.getByRole('heading',{name:'Portfólio público',exact:true}).waitFor();
  const response=page.waitForResponse(response=>response.url().endsWith('/checks')&&response.request().method()==='POST',{timeout:75000});
  await page.getByRole('button',{name:'Verificar agora'}).click();
  const checked=await response; if(checked.status()!==201)throw new Error(`Check HTTP ${checked.status()}`);
  const body=await checked.json();
  await page.getByRole('button',{name:'Verificar agora'}).waitFor({state:'visible'});await page.waitForLoadState('networkidle');
  await page.screenshot({path:'docs/evidence/monitoramento-publico.png',fullPage:true});
  const detail=page.url();
  await page.goto('http://localhost:3000/perfil'); await page.getByLabel('Nome',{exact:true}).fill('Revisão operacional validada');
  await page.getByRole('button',{name:'Salvar alterações',exact:true}).click();await page.getByText('Perfil atualizado com sucesso.').waitFor();
  await page.reload();await page.getByLabel('Nome',{exact:true}).waitFor();
  if(await page.getByLabel('Nome',{exact:true}).inputValue()!=='Revisão operacional validada')throw new Error('Profile was not persisted');
  const routes=['/','/sistemas','/incidentes','/deploys','/qualidade','/integracoes','/alertas','/relatorios','/auditoria','/perfil','/configuracoes'];
  for(const path of routes){await page.goto('http://localhost:3000'+path);await page.waitForLoadState('networkidle');await page.getByRole('heading',{level:1}).first().waitFor();}
  await page.getByRole('button',{name:'Abrir menu da conta',exact:true}).click();await page.getByRole('menuitem',{name:'Sair',exact:true}).click();await page.waitForURL('**/login');
  await page.goto(detail);await page.waitForURL('**/login');
  fs.writeFileSync('docs/evidence/live-review.json',JSON.stringify({check:{httpStatus:body.httpStatus,success:body.success,responseTimeMs:body.responseTimeMs,checkedAt:body.checkedAt},profilePersisted:true,logoutProtected:true,routes,errors,network},null,2));
  if(errors.length||network.length)throw new Error('Browser errors found; inspect live-review.json');
  console.log(JSON.stringify({httpStatus:body.httpStatus,success:body.success,pages:routes.length+1,errors:errors.length,networkFailures:network.length}));
}finally{await browser.close();}
