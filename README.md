# Pulse Ops — Central de operações

O Pulse Ops acompanha a saúde das suas aplicações a partir de verificações HTTP reais, registra incidentes e eventos e integra serviços externos. **Pulse Ops observa; Nexus Flow executa workflows; AI Web Auditor analisa aplicações web.**

Uma conta nova começa vazia. Não há sistemas de exemplo, histórico simulado ou métricas preenchidas automaticamente no ambiente normal.

## Executar localmente

Requisitos: Docker Desktop com Compose v2 e aproximadamente 4 GB de memória disponíveis. Os builds iniciais precisam acessar os registries oficiais de Maven, npm e Docker.

```powershell
docker compose build
docker compose up -d --wait --wait-timeout 180
```

Abra [http://localhost:3000/cadastro](http://localhost:3000/cadastro), crie sua conta e cadastre um sistema. Os parâmetros locais têm valores padrão; copiar `.env.example` é necessário apenas para personalizar a implantação. Não use os segredos locais em produção.

- Interface: `http://localhost:3000`.
- Backend: `http://localhost:18083`; banco: `localhost:55432`. Ambos vinculados apenas ao loopback do host.
- Saúde: `/healthz` na interface e `/actuator/health` no backend.
- Contratos: `http://localhost:18083/swagger-ui.html` e `/api-docs`.
- Banco persistente: volume `pulseops-postgres-data`. Migrations Flyway incrementais; `ddl-auto=validate`.
- Para parar sem perder os dados: `docker compose down`. **Não use `down -v` em uma instalação com dados importantes.**

Em uma base antiga, a migration V5 desativa e oculta somente os sistemas identificados como exemplos do seed original. Os históricos ficam preservados. Recursos reais legados sem proprietário continuam acessíveis ao administrador; não são atribuídos automaticamente a uma conta nova.

## Primeiro acesso

1. Criar conta e entrar; o cadastro também inicia a sessão.
2. Cadastrar nome, URL base e endpoint (`/` para verificar uma aplicação web), ambiente, intervalo, HTTP esperado, tempo limite, limite de latência e meta de disponibilidade.
3. Executar **Verificar agora** ou aguardar o monitoramento automático.
4. Consultar HTTP, tempo de resposta, motivo do estado e histórico.
5. Disponibilidade e comparação com a meta aparecem após cinco verificações válidas no período selecionado. Incidentes e eventos surgem de operações efetivamente realizadas.

O backend agenda a primeira verificação após salvar o sistema. O scheduler tem ciclo padrão de dez segundos e atraso inicial de cinco segundos no Compose; cada sistema possui seu próprio intervalo. A página Configurações exibe os valores ativos da implantação.

## Métricas e regras

| Informação | Origem |
| --- | --- |
| HTTP e latência | Resposta do endpoint; falha de transporte não recebe HTTP ou latência de resposta inventados |
| Última verificação / falha | Registros persistidos de verificações |
| Estado e motivo | Última condição calculada, código HTTP, limite de latência e falhas recentes |
| Disponibilidade | Verificações bem-sucedidas / verificações válidas no período, mínimo cinco amostras válidas; bloqueios de segurança e falhas internas do verificador são excluídos |
| Média, mínimo, máximo e p95 | Tempos medidos em respostas HTTP do período |
| Meta de disponibilidade | Disponibilidade amostral comparada à meta configurada; ausência de histórico é explicitada |
| Qualidade de testes | Relatórios enviados por pipelines a `POST /api/quality/reports` |
| Resultado de auditoria | Resposta efetiva do AI Web Auditor, separada de cobertura de testes |

Disponibilidade amostral **não mede continuamente o tempo online**. A latência elevada pode tornar o sistema degradado mesmo com HTTP esperado. Não são calculados CPU, memória, custo, tráfego ou outras métricas sem um produtor real.

- **Operacional:** resposta esperada dentro do limite de latência.
- **Degradado:** resposta lenta, falhas recentes ou resposta inesperada ainda sem atingir a condição de indisponibilidade.
- **Indisponível:** três falhas consecutivas, inclusive respostas HTTP inesperadas ou falhas de transporte. A primeira falha degrada o estado e não abre incidente.
- **Aguardando verificação:** nenhum resultado ou alvo alterado.
- **Monitoramento pausado:** cadastro inativo; checks manuais e automáticos são bloqueados.
- **Em manutenção:** suspensão explícita de checks, mantendo o histórico.
- **Configuração inválida:** destino rejeitado pela política de segurança; não gera incidente de indisponibilidade.

Cada cadastro possui intervalo de 30 a 86.400 segundos, timeout e limite de latência. A primeira verificação é agendada pelo backend após o commit; o navegador pode ser fechado. O scheduler consulta os sistemas a cada 10 segundos e executa somente os que atingiram seu intervalo. Disponibilidade usa verificações válidas persistidas e exige cinco amostras; bloqueios de segurança e falhas internas do verificador são excluídos do denominador e contabilizados separadamente. Consulte as regras e a matriz de fontes na [revisão de consistência](docs/consistencia-final.md).

Incidentes automáticos são abertos após três falhas consecutivas, com início na primeira falha observada; cinco falhas elevam a severidade. Duas respostas consecutivas saudáveis resolvem o incidente automático. Incidentes manuais têm título, contexto, severidade, início e fluxo **Aberto → Investigando → Resolvido**. Não existe exclusão de incidentes para apagar o histórico operacional.

Eventos possuem data, sistema, tipo, severidade, descrição e origem. A exclusão de um sistema preserva o evento com seu nome e proprietário, evitando perder o registro da operação.

## Arquitetura

- `backend`: Java 21, Spring Boot 3.5, Spring Security, JPA, PostgreSQL 16, Flyway, Reactor Netty, JUnit/Mockito/MockMvc/Testcontainers e JaCoCo.
- `frontend`: React 19, TypeScript, Vite, MUI, React Router, Axios, React Hook Form/Zod, Recharts, Vitest/Testing Library e Playwright.
- Controllers validam contratos; services mantêm regras; repositories aplicam o escopo da conta. O transporte externo fica separado da transação de persistência do monitoramento.
- Uma aplicação Spring concentra scheduler, eventos e integrações. Não há editor de workflows ou engine de auditorias dentro do Pulse Ops.
- Deploys são registros operacionais e transições; a interface não executa uma implantação de infraestrutura.

## Conta e segurança

Cadastro público cria um desenvolvedor que gerencia **seus próprios sistemas**. Administradores podem gerenciar a equipe e recursos reais globais. Leituras, agregações e mutações de sistemas e recursos relacionados respeitam o proprietário, inclusive consultas por UUID.

Senhas usam BCrypt. JWT possui issuer, expiração e versão de sessão validada no banco. A sessão é armazenada na aba por padrão; a opção de lembrar utiliza armazenamento local. A inicialização valida o usuário no backend. Logout revoga os tokens anteriores da conta; troca de e-mail exige a senha atual e emite uma nova sessão. Não há refresh token, recuperação de senha ou verificação de e-mail implementados; a interface não oferece essas ações.

A interface usa política CSP, proteção contra framing, cabeçalhos de segurança e limite de requisições de login/cadastro no Nginx. Nenhum segredo de integração é devolvido ao navegador; credenciais externas são cifradas com AES-256-GCM.

URLs externas passam por validação de protocolo, credenciais, endpoint, IP e DNS. O cliente fixa os endereços validados e não segue redirecionamentos. Redes privadas, loopback, metadados e faixas reservadas são bloqueados no ambiente normal; o perfil `prod` também impede ativar a permissão ampla de redes privadas.

## Produção

Crie `.env` a partir de `.env.example` e configure, antes de implantar:

- `SPRING_PROFILES_ACTIVE=prod`.
- `JWT_SECRET` aleatório de pelo menos 32 caracteres e `INTEGRATIONS_ENCRYPTION_KEY` diferente, igualmente forte.
- `POSTGRES_PASSWORD` exclusivo do ambiente. Alterar a variável não muda a senha de um volume PostgreSQL existente: faça a rotação no banco e atualize a configuração em conjunto.
- `CORS_ALLOWED_ORIGINS=https://seu-dominio` com origens explícitas.
- HTTPS no proxy externo, domínio, backups do PostgreSQL e retenção de dados/logs conforme a infraestrutura.
- `DEMO_SEED_ENABLED=false`, `DEMO_READ_ONLY=false`, `VITE_DEMO_MODE=false`, `MONITORING_ALLOW_PRIVATE_NETWORKS=false`.

O backend recusa os valores de exemplo conhecidos no perfil `prod`, chaves compartilhadas entre JWT e integrações, CORS com wildcard e flags inseguras. A chave das integrações deve ser preservada junto dos backups; mudá-la sem recifrar as credenciais exige reconfigurar os tokens salvos.

A implementação foi validada localmente. Publicação, TLS, backups e integrações com credenciais reais dependem da sua infraestrutura; não são comprovados por um build local.

## Integrações

Configure a URL própria do AI Web Auditor ou Nexus Flow em **Integrações → Configurar**. Um conector não é um sistema monitorado. O teste de conexão produz um registro em `integration_probes`, sem alterar os checks das aplicações; ações usam a credencial cifrada do conector. Implantações são registros manuais explícitos; não há importação automática de GitHub implementada.

Consulte [contratos e configuração](docs/integrations.md) para AI Web Auditor, Nexus Flow e callback autenticado. GETs consultam registros existentes; nenhuma leitura fabrica resultados ou dispara auditorias.

## Testes

Backend: Java 21 ou superior, Maven e Docker ativo para PostgreSQL Testcontainers.

```powershell
cd backend
mvn clean verify
```

Frontend: Node.js 22.12 ou superior.

```powershell
cd frontend
npm ci
npm run lint
npm run test:run
npm run build
```

E2E com ambiente isolado, a partir da raiz:

```powershell
docker compose -f docker-compose.yml -f docker-compose.test.yml up -d --build --wait --wait-timeout 180
cd frontend
npx playwright install chromium
npm run test:e2e
```

O ambiente E2E tem nome, rede, portas e volume próprios: interface `3001`, API `18084`, banco `55433`. Apenas essa composição inclui `tests/fixtures/server.mjs` e permite endpoints privados controlados. Ela não deve ser usada em produção. As respostas 200/503/lentas e contratos de integração são produzidos por um servidor real de teste, não por fallbacks da aplicação.

Os testes do navegador cobrem criação de conta, persistência de sessão, CRUD de sistemas, checks, indisponibilidade, incidentes, eventos, perfil, logout, integrações, teclado e doze páginas em 1920, 1366, 1024, 768, 430, 390 e 360 px. Relatórios, logs e capturas da revisão estão em `docs/evidence`; o resultado detalhado e o inventário ficam em [docs/revisao-pulseops.md](docs/revisao-pulseops.md).

Depois da suíte manual, execute também o cenário com monitoramento automático e reinício, a partir da raiz:

```powershell
docker compose -f docker-compose.yml -f docker-compose.test.yml -f docker-compose.consistency.yml up -d --wait --wait-timeout 180
cd frontend
npx playwright test --config playwright.consistency.config.ts
```

Esse teste fecha o navegador, verifica o check produzido pelo backend, altera o endpoint para HTTP 500, comprova incidente e consistência entre consultas e reinicia somente os serviços do projeto `pulseops-e2e`, preservando seu volume. Os resultados desta revisão, matriz de fontes e pendências ficam em [docs/consistencia-final.md](docs/consistencia-final.md), com evidências em `docs/evidence/consistency`.
