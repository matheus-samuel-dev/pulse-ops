import { test, expect, type Page } from '@playwright/test';
import fs from 'node:fs/promises';
const password = 'Portfolio@2026';
async function signup(page: Page) {
  const email = `e2e-${Date.now()}-${Math.floor(Math.random() * 100000)}@example.org`;
  await page.goto('/cadastro');
  await page.getByLabel('Nome', { exact: true }).fill('Operador E2E');
  await page.getByLabel('E-mail', { exact: true }).fill(email);
  await page.getByLabel('Senha', { exact: true }).fill(password);
  await page.getByLabel('Confirmar senha').fill(password);
  await page.getByRole('button', { name: 'Criar conta', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Visão geral', exact: true })).toBeVisible();
  return email;
}
async function createSystem(page: Page, name = 'Aplicação controlada', endpoint = '/health') {
  await page.goto('/sistemas');
  await page.getByRole('button', { name: 'Cadastrar sistema', exact: true }).first().click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Nome do sistema').fill(name);
  await dialog.getByLabel('URL base').fill('http://fixture:8080');
  await dialog.getByLabel('Endpoint de saúde').fill(endpoint);
  await dialog.getByRole('button', { name: 'Cadastrar sistema', exact: true }).click();
  await expect(dialog).toBeHidden();
  await page.getByText(name, { exact: true }).click();
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();
  return page.url().split('/').pop()!;
}
async function check(page: Page) {
  const response = page.waitForResponse(response => response.url().endsWith('/checks') && response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Verificar agora' }).click();
  expect((await response).status()).toBe(201);
  await expect(page.getByRole('table', { name: 'Histórico de verificações' })).toBeVisible();
}
test('primeiro acesso, sessão, CRUD, verificação, perfil e logout com persistência', async ({ page }) => {
  const email = await signup(page);
  await expect(page.getByText('Cadastre seu primeiro sistema', { exact: false }).first()).toBeVisible();
  await page.reload(); await expect(page.getByRole('heading', { name: 'Visão geral', exact: true })).toBeVisible();
  const id = await createSystem(page); await check(page);
  await expect(page.getByRole('table', { name: 'Histórico de verificações' }).getByText('200', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Editar', exact: true }).click();
  const dialog = page.getByRole('dialog'); await dialog.getByLabel('Nome do sistema').fill('Aplicação renomeada');
  await dialog.getByRole('button', { name: 'Salvar alterações' }).click(); await expect(dialog).toBeHidden();
  await page.reload(); await expect(page.getByRole('heading', { name: 'Aplicação renomeada', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Abrir menu da conta', exact: true }).click();
  await page.getByRole('menuitem', { name: 'Meu perfil' }).click();
  await page.getByLabel('Nome', { exact: true }).fill('Operador atualizado');
  await page.getByRole('button', { name: 'Salvar alterações', exact: true }).click();
  await expect(page.getByText('Perfil atualizado com sucesso.')).toBeVisible();
  await page.reload(); await expect(page.getByLabel('Nome', { exact: true })).toHaveValue('Operador atualizado');
  await page.getByRole('button', { name: 'Abrir menu da conta', exact: true }).click();
  await page.getByRole('menuitem', { name: 'Sair', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/); await page.goto('/sistemas'); await expect(page).toHaveURL(/\/login$/);
  await page.getByLabel('E-mail', { exact: true }).fill(email); await page.getByLabel('Senha', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Entrar no PulseOps' }).click();
  await expect(page.getByRole('heading', { name: 'Visão geral', exact: true })).toBeVisible();
  await page.goto(`/sistemas/${id}`); await expect(page.getByRole('heading', { name: 'Aplicação renomeada' })).toBeVisible();
  await page.getByRole('button', { name: 'Excluir', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Excluir sistema' }).click(); await expect(page).toHaveURL(/\/sistemas$/);
  await expect(page.getByText('Aplicação renomeada', { exact: true })).toHaveCount(0);
});
test('indisponibilidade real, incidentes automáticos e edição do contexto', async ({ page }) => {
  await signup(page); await createSystem(page, 'Serviço indisponível', '/status/503');
  for (let i=0; i<3; i++) await check(page);
  await page.goto('/incidentes');
  await page.getByRole('button', { name: /Abrir detalhes do incidente/ }).click();
  await expect(page.getByText('Automático').first()).toBeVisible();
  await page.getByRole('button', { name: 'Editar contexto' }).click();
  const dialog = page.getByRole('dialog', { name: 'Editar incidente' }); await dialog.getByLabel('Descrição').fill('Endpoint retornou HTTP 503 nas verificações controladas.');
  await dialog.getByRole('button', { name: 'Salvar contexto' }).click(); await expect(dialog).toBeHidden();
  await page.getByRole('button', { name: 'Investigar', exact: true }).click(); await expect(page.getByText('Investigação iniciada', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Resolver incidente' }).click(); await expect(page.getByText('Operação normalizada')).toBeVisible();
  await page.goto('/auditoria');
  const stateChanges = page.getByRole('heading', { name: 'Estado do sistema alterado', exact: true });
  await expect(stateChanges).toHaveCount(2); await expect(stateChanges.first()).toBeVisible();
  await page.getByLabel('Buscar eventos').fill('resolvido'); await expect(page.getByText('Incidente resolvido', { exact: true })).toBeVisible();
});
test('configuração, teste HTTP e execução reais de integrações', async ({ page }) => {
  await signup(page); const target = await createSystem(page, 'Alvo da auditoria');
  await page.goto('/integracoes'); await page.getByRole('button', { name: 'Configurar AI Web Auditor' }).click();
  const config = page.getByRole('dialog'); await config.getByLabel('URL base da integração').fill('http://fixture:8080');
  await config.getByLabel('Token de acesso').fill('test-fixture-token');
  await config.getByLabel('URL pública da interface').fill('https://auditor.example.org');
  await config.getByRole('button', { name: 'Salvar configuração' }).click(); await expect(config).toBeHidden();
  // Reach the existing integration details, whose connection check invokes the backend HTTP probe.
  await page.getByRole('button', { name: 'Testar conexão com AI Web Auditor' }).click(); await expect(page.getByText('Conexão realizada com sucesso', { exact: false }).first()).toBeVisible();
  await page.keyboard.press('Escape');
  await page.goto(`/sistemas/${target}`); page.once('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: 'Solicitar auditoria' }).click(); await expect(page.getByText('Aguardando', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Consultar resultado' }).click(); await expect(page.getByText('Concluída', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Abrir relatório' })).toHaveAttribute('href', /https:\/\/auditor.example.org\/audits\//);
  await page.goto('/integracoes'); await page.getByRole('button', { name: 'Configurar Nexus Flow' }).click();
  const nexus = page.getByRole('dialog'); await nexus.getByLabel('URL base da integração').fill('http://fixture:8080');
  await nexus.getByRole('button', { name: 'Salvar configuração' }).click(); await expect(nexus).toBeHidden();
  await page.goto(`/sistemas/${target}`); await page.getByRole('button', { name: 'Acionar Nexus Flow' }).click();
  await expect(page.getByText('Recebida pelo Nexus Flow', { exact: true })).toBeVisible();
});
test('todas as páginas, teclado, tamanhos solicitados, console e rede', async ({ page }) => {
  test.setTimeout(300_000);
  const errors: string[] = []; const badRequests: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if(['error','warning'].includes(message.type())) errors.push(message.text()); });
  page.on('response', response => { if(response.status() >= 400) badRequests.push(`${response.status()} ${response.url()}`); });
  await signup(page); const id=await createSystem(page, 'Responsividade'); await check(page);
  const paths = ['/', '/sistemas', `/sistemas/${id}`, '/incidentes', '/deploys', '/qualidade', '/integracoes', '/alertas', '/relatorios', '/auditoria', '/perfil', '/configuracoes'];
  const evidence: object[]=[];
  for(const width of [1920,1366,1024,768,430,390,360]) {
    await page.setViewportSize({ width, height: 900 });
    for(const path of paths) {
      await page.goto(path); await page.waitForLoadState('networkidle');
      await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();
      if (path === '/configuracoes' && width === 1366) {
        const themeToggle = page.getByRole('switch', { name: 'Alternar tema claro e escuro' });
        await expect(themeToggle).toBeChecked(); await themeToggle.focus(); await page.keyboard.press('Space');
        await expect(themeToggle).not.toBeChecked(); await page.keyboard.press('Space'); await expect(themeToggle).toBeChecked();
      }
      const dimensions=await page.evaluate(()=>({ width:window.innerWidth, scroll:document.documentElement.scrollWidth }));
      expect(dimensions.scroll, `${path} at ${width}px overflow`).toBeLessThanOrEqual(width + 1);
      evidence.push({path,width,overflow:dimensions.scroll-width});
      if ([360,1366].includes(width) && ['/configuracoes','/integracoes'].includes(path)) {
        await page.screenshot({path:`../docs/evidence/consistency/${path.slice(1)}-${width}.png`,fullPage:true});
      }
    }
    await page.goto('/'); await page.waitForLoadState('networkidle'); await page.getByRole('button',{name:'Abrir menu da conta',exact:true}).focus(); await page.keyboard.press('Enter');
    await expect(page.getByRole('menuitem',{name:'Meu perfil'})).toBeVisible(); await page.keyboard.press('Escape');
    await expect(page.getByRole('button',{name:'Abrir menu da conta',exact:true})).toBeFocused();
    await page.getByRole('button',{name:'Abrir menu da conta',exact:true}).click();
    await expect(page.getByRole('menuitem',{name:'Meu perfil'})).toBeVisible();
    await page.mouse.click(width - 10, 850);
    await expect(page.getByRole('menuitem',{name:'Meu perfil'})).toBeHidden();
    await expect(page.getByRole('button',{name:'Abrir menu da conta',exact:true})).toBeFocused();
    if (width === 1366) {
      const sidebarAccount = page.getByRole('button',{name:'Abrir menu da conta na barra lateral',exact:true});
      await sidebarAccount.focus(); await page.keyboard.press('Enter');
      await expect(page.getByRole('menuitem',{name:'Meu perfil'})).toBeVisible(); await page.keyboard.press('Escape');
      await expect(sidebarAccount).toBeFocused();
    }
    await page.screenshot({path:`../docs/evidence/consistency/ui-${width}.png`,fullPage:true});
    if(width<900) { await page.getByRole('button',{name:'Abrir menu',exact:true}).click(); await expect(page.getByRole('list',{name:'Navegação principal'}).last()).toBeVisible(); await page.keyboard.press('Escape'); }
  }
  await fs.writeFile('../docs/evidence/consistency/browser-audit.json',JSON.stringify({evidence,errors,badRequests},null,2));
  expect(errors).toEqual([]); expect(badRequests).toEqual([]);
});

