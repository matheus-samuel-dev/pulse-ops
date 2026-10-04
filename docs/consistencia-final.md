# Revisão final de consistência operacional

## Auditoria do estado recebido

O fluxo normal já separava o seed demonstrativo, protegia os recursos por proprietário e persistia verificações, incidentes e eventos. A revisão adicional encontrou divergências remanescentes que precisam ser corrigidas antes da validação final:

- Relatórios reconstruía eventos a partir de quatro famílias de tabelas, enquanto a trilha consultava `operational_events`. Implantações e qualidade não gravavam eventos nessa trilha.
- Filtros de sistema, estado e impacto do relatório só alteravam uma lista parcial no navegador; os KPIs continuavam globais.
- Disponibilidade tinha cálculos/precisões separados e o relatório apresentava uma taxa geral diferente da média entre sistemas do painel.
- O gráfico do detalhe usava a página de checks, sem a janela das métricas; paginar mudava o gráfico e podia mudar o texto de última verificação.
- Qualidade tinha overview independente da janela selecionada e representava ausência de relatórios com médias zero no backend.
- O catálogo de integrações ainda incluía Arena Predict, PlaySpace e outros sistemas de negócio. Configurar um conector exigia escolher um sistema monitorado; os checks desse sistema eram usados como teste de conexão.
- Monitoramento usava apenas um intervalo global. Não havia primeira execução automática após salvar o cadastro.
- Tipo de falha do probe não era persistido. Uma resposta 5xx transitória já mudava o estado para indisponível.
- Implantações não tinham coluna de origem; a timeline chamava relatórios importados de pipeline sem essa evidência.
- Alertas não continham relação com o evento que os produziu e não permitiam voltar à entidade nem marcar novamente como não lidos.

## Decisões de escopo

As telas existentes serão mantidas. Conta/proprietário continua sendo o espaço pessoal; não será introduzido um módulo de workspaces. As consultas operacionais compartilharão regras e escopo no backend. Conexões de AI Web Auditor e Nexus Flow terão seus próprios destinos e testes, sem criar aplicações monitoradas implicitamente. Implantações continuarão como registros manuais explícitos; não será simulada uma conexão com GitHub.

## Implementação e fonte de verdade

`OperationalReadModel` centraliza proprietário, sistemas, ambiente, janela, amostras válidas, disponibilidade, incidentes, implantações e último relatório de CI. Dashboard, relatórios e qualidade reutilizam essas regras. O frontend exibe as respostas; não reconstrói disponibilidade, cobertura ou incidentes a partir de listas parciais.

O painel usa uma única requisição `GET /api/dashboard`, com relógio fixo e leitura transacional REPEATABLE_READ. Respostas separadas obtidas em instantes diferentes podem incluir checks novos entre as requisições; não há promessa de congelar o banco entre telas. Sob o mesmo escopo e conjunto persistido, as fórmulas e resultados são iguais.

Os relatórios consultam a timeline persistida, com paginação no servidor. Período, ambiente e sistema filtram tanto os KPIs como os eventos. Não há filtros locais que mudam a tabela e deixam o indicador global. Exportação informa que exporta a página consultada. Recursos excluídos ficam na trilha de auditoria, mas não contaminam o relatório dos sistemas atuais.

Cada cadastro representa uma aplicação em um ambiente. Antes da primeira evidência é possível corrigir o ambiente. Depois de receber checks, incidentes, implantações ou relatórios de testes, API e trigger PostgreSQL impedem reatribuir seu histórico a outro ambiente; cadastre uma segunda observação para outro ambiente.

### Regras das métricas

- Janelas móveis: últimas 24 horas, 7 dias ou 30 dias, calculadas pelo relógio do backend. Os limites incluem os timestamps inicial e final. O painel começa em 24h, relatórios em 7d e qualidade em 30d; o período está visível e pode ser igualado para comparar telas.
- Disponibilidade: `checks com sucesso / checks válidos × 100`. Timeout, DNS, conexão recusada e HTTP diferente do esperado são falhas reais. `SECURITY_POLICY` e `UNEXPECTED` (erro interno do verificador) são excluídos do denominador. A API separa tentativas totais, válidas e excluídas. Registros legados sem classificação permanecem no conjunto medido, sem inventar uma classificação retrospectiva.
- Exige cinco checks válidos na janela. Abaixo desse número o percentual é `null`, exibido como **Sem dados**. A configuração Jackson existente omite campos nulos no JSON; ausência do campo tem o mesmo significado, nunca é convertida em zero. Zero tentativas também produz histórico e latência vazios e última verificação ausente. Uma medição válida de 0% continua significando que todas as amostras válidas falharam.
- Disponibilidade geral: média simples dos percentuais dos sistemas que têm amostras suficientes, incluindo histórico de sistemas pausados. Não é a razão global de todos os checks nem uptime contínuo. Precisão da API: três casas decimais; apresentação PT-BR arredondada.
- Latência: `response_time_ms` de respostas HTTP efetivas, inclusive HTTP 4xx/5xx. Falha de transporte sem resposta não gera HTTP/latência inventados. Média, mínimo, máximo e p95 usam amostras da janela. Gráfico do detalhe consulta a janela inteira, independentemente da página do histórico.
- Última verificação: check persistido mais recente de toda a história. Não muda ao paginar nem ao selecionar uma janela sem amostras. Estado atual é a projeção persistida atualizada na mesma transação do check; manutenção, pausa e alteração de configuração são sobreposições explícitas.
- Incidentes dos agregados: iniciados na janela e ainda não resolvidos. Por isso os cards indicam **Incidentes ativos na janela**. A página Incidentes oferece também **Todo o histórico**, que inclui incidentes iniciados antes da janela.
- Qualidade: último relatório de CI por sistema dentro da janela, ordenado de forma determinística. Contagens vêm do relatório; linhas e ramificações vêm dos percentuais importados. O índice explicitamente identificado usa `60% cobertura de linhas + 40% cobertura de ramificações`; não é resultado do AI Web Auditor. Sem relatório, médias/índice são nulos e classificação é `NO_DATA`.
- Implantações: registros existentes e timestamp próprio, com origem `MANUAL`. Dados anteriores que não possuíam origem recebem `LEGACY_API`, identificando a limitação histórica. Duração ausente continua nula. Não existe provedor GitHub conectado implicitamente.

### Monitoramento e incidentes

Cada sistema tem URL, endpoint, ambiente, ativo, intervalo de 30 a 86.400 segundos, timeout, limite de latência, meta de disponibilidade e manutenção. O listener executa a primeira verificação no backend após o commit; salvar configuração relevante também agenda uma verificação. O scheduler só executa sistemas cujo intervalo venceu. A execução externa fica fora da transação; o resultado é persistido com bloqueio do registro e descartado se o alvo/configuração mudou durante a requisição.

Uma resposta esperada dentro do limite é **Operacional**. A primeira falha real é **Degradado**, sem incidente. Três falhas consecutivas produzem **Indisponível** e um incidente automático, com início na primeira falha. Cinco falhas elevam a severidade. Duas verificações consecutivas saudáveis recuperam o estado e resolvem o incidente automático. HTTP esperado acima do limite é degradação de latência. Destino bloqueado pela política SSRF é **Configuração inválida**, sem atribuir indisponibilidade à aplicação. Manutenção e pausa suspendem checks; o histórico permanece.

Check, projeção de estado, eventos, incidente e alerta relacionado são persistidos atomicamente. Alertas carregam IDs do evento, sistema e recurso; permitem lido/não lido e navegação à entidade real. O badge consulta a quantidade não lida do backend ao navegar e após operações de leitura. Não há promessa de notificações em tempo real sem nova consulta.

### Banco e migrations

- V10: intervalo/manutenção, estados/falhas, origem de implantação/CI, referências dos eventos/alertas e destino próprio das conexões; cria `integration_probes` e índices.
- V11: vincula eventos a checks, incidentes, implantações e relatórios que realmente existem. Usa seus timestamps e IDs; não gera operações simuladas. Preserva snapshots de recursos removidos e eventos de configuração.
- V12: amplia a coluna de estado para suportar `CONFIGURATION_REQUIRED`; um teste com bloqueio SSRF encontrou e corrigiu a incompatibilidade anterior.
- V13: impede reclassificar evidências existentes por alteração de ambiente.

O teste de integração também identificou erro PostgreSQL na consulta de datas opcionais da trilha; o tipo dos parâmetros nulos passou a ser explícito. As migrations foram exercitadas em PostgreSQL novo e no volume já existente, sem recriar o banco.

No backend, os controllers alterados foram `DashboardController`, `MetricsController`, `IncidentController`, `ReportController`, `QualityController`, `NotificationController` e `EventController`. O contrato de sistemas mudou por seus DTOs e service. Serviços centrais: `OperationalReadModel`, `DashboardService`, `OperationalReportService`, `QualityService`, `MonitoredSystemService`, `MonitoringService`, `MonitoringPersistenceService`, `MonitoringBatchService`, `InitialMonitoringListener`, `AvailabilityService`, `SlaService`, `EventRecorder`, serviços de incidentes/implantações/alertas e o conjunto de integração (`ConnectionService`, `IntegrationService`, `IntegrationCheckService`, `IntegrationProbeRecorder`, `IntegrationActivityService`, `IntegrationActionService`). Entidades relevantes: `MonitoredSystem`, `HealthCheck`, `OperationalEvent`, `Notification`, `Deployment`, `TestReport`, `IntegrationConnection` e a nova `IntegrationProbe`. Repositories e DTOs foram ajustados para escopo, origem, parâmetros por janela e valores ausentes. O mapper antigo de conexão baseado no estado de uma aplicação foi removido junto de seu teste obsoleto, substituído por testes de probes independentes.

### Dados fictícios e atribuições removidas

| Local | Problema recebido | Substituição |
| --- | --- | --- |
| `service/integration/IntegrationCatalog` e configuração normal | Aplicações de negócio apareciam como conectores | Catálogo apenas AI Web Auditor/Nexus Flow; URL própria persistida |
| `IntegrationService` / `IntegrationCheckService` | Check da aplicação associada comprovava conexão do conector | HTTP real próprio e `integration_probes`; zero alteração em `health_checks` da aplicação |
| `IntegrationActivityService` | Histórico reconstruído atribuía CI/monitoramento a integrações | Eventos `INTEGRATION_*` persistidos com origem do conector |
| `OperationalReportService` | Lista sintetizada e fonte de pipeline presumida | `operational_events` com `resource_id`, origem e estado registrados |
| `DeploymentTimeline`, `DeploymentCard` e fluxo de implantação | Texto GitHub Actions sem provedor, duração padrão de 180 segundos | Origem manual/legada explícita; duração desconhecida preservada como nula |
| `QualityService` / `QualityPage` | Média zero e destaque sem distinguir ausência de CI | `NO_DATA`, valores nulos, fórmula e origem do relatório explícitas |
| `SystemDetailPage` | Gráfico preenchido a partir da página de checks | Endpoint de latência por janela; vazio quando não houve resposta HTTP |
| `ReportsPage` | Filtros locais pareciam alterar todos os dados | Escopo aplicado no backend aos indicadores e à timeline |
| Compose / `application.yml` / `.env.example` | Associações legadas por system-id confundiam integração e sistema | Variáveis retiradas; conexões antigas recebem cópia do destino na migration |

Nesta revisão não foi encontrada nova geração aleatória de métricas na experiência normal. Seeds demonstrativos antigos já estavam isolados pela revisão anterior: `DevelopmentDataSeeder` depende de ativação explícita, as três flags de demo são falsas por padrão e o perfil `prod` recusa seed/mode inseguro. V5 preserva e oculta exemplos legados. Marina Costa e demais usuários de demonstração não são criados no acesso normal. Fixtures/valores controlados existem somente em testes; o servidor de teste executa HTTP real em rede e volume separados.

Não foi acrescentado um novo modo de demonstração à interface. Quem optar pelo demo legado deve usar composição e banco separados e identificados; não ativar as flags em uma instalação operacional. Uma nova conta normal começa sem sistemas, checks, incidentes, implantações, relatórios ou conexões configuradas.

### Frontend e conta

Foram revisadas Visão geral, Sistemas, detalhe, Incidentes, Implantações, Qualidade, Integrações, Alertas, Relatórios, Trilha de auditoria e o shell. Estados não medidos não são convertidos em indisponibilidade. As ações exibidas chamam APIs ou navegam para recursos existentes. A pior incoerência estava em Integrações; a tela foi simplificada para duas conexões com propósito, configuração, estado, último teste, comunicação, erro e ações reais.

Cadastro valida nome/e-mail/senha/confirmação, cria conta proprietária e inicia a sessão. Login autentica no backend e registra atividade real. Senhas usam BCrypt; JWT tem expiração e versão de sessão validada no banco. Inicialização verifica a sessão; logout revoga os tokens da conta. Perfil salva nome e e-mail com senha atual quando necessário. A preferência de tema funciona e fica no armazenamento local do navegador, sem prometer sincronização entre dispositivos. Não existe entidade artificial de workspace: a conta delimita seu espaço pessoal. Equipe é acessível ao administrador e consulta usuários reais; desenvolvedores comuns não recebem uma equipe fictícia.

Avatar e rodapé da sidebar abrem o mesmo menu MUI, com perfil, configurações e sair. Teclado, Escape, clique fora, retorno de foco e drawer mobile entram nos testes de navegador. Os valores de monitoramento da implantação são exibidos como configuração do servidor; os parâmetros por sistema são editáveis no formulário.

Componentes/contratos relevantes: `AppShell`, `SystemFormDialog`, `SystemStatusChip`, formatadores operacionais, `SystemHealthTable`, apresentação de implantações, `ConnectionManager`, tipos em `api.ts` e services de dashboard, checks/métricas, relatórios, qualidade, incidentes e notificações. O inventário ao final lista cada arquivo, incluindo testes e configurações de build/Compose.

### Integrações e proveniência

AI Web Auditor: destino próprio + Bearer cifrado → teste HTTP → `POST /api/audits` para a URL do sistema → UUID/status persistidos → `GET /api/audits/{id}` → resultado efetivo e link público. Score externo não vira cobertura de CI.

Nexus Flow: ação manual ou evento `SYSTEM_DOWN` → execução/outbox persistida → webhook HTTP → estado **Recebida pelo Nexus Flow** → callback autenticado de progresso/conclusão. HTTP 2xx não é tratado como workflow concluído. O Pulse Ops não implementa o motor de workflows.

Os contratos, limites, cooldown, callback, credenciais e configuração externa estão em [integrations.md](integrations.md). Configuração, teste e execução usam escopo da conta; alteração de UUID por outro usuário é rejeitada. SSRF continua validando protocolo/IP/DNS, fixando endereços no transporte, bloqueando redirects e redes reservadas. A exceção de rede privada existe exclusivamente na infraestrutura controlada de testes; o perfil `prod` impede sua ativação ampla.

## Matriz de comprovação

Os nomes abaixo referem-se aos métodos de `backend/src/test/java/com/pulseops/integration/OperationalFlowIntegrationTest.java`, salvo indicação explícita de outra classe. `operations.spec.ts` e `consistency.spec.ts` são testes Playwright contra API e PostgreSQL dos containers.

| TELA | MÉTRICA/DADO | FONTE REAL | ENDPOINT | TABELA/ORIGEM | TESTE |
| --- | --- | --- | --- | --- | --- |
| Visão geral | Sistemas e estados/problemas | Cadastro e projeção da última condição | GET `/api/dashboard` | `monitored_systems` + `health_checks` | `emptyEvidenceInvariantAcrossEveryReadScreen`, `lastCheckLatencyHistoryAvailabilityAndEventShareEvidence` |
| Visão geral / Relatórios | Disponibilidade e cobertura médias | Fórmulas comuns por janela e conta | GET `/api/dashboard`; GET `/api/reports/operational?period=24h` | `health_checks`, `test_reports` | `lastCheckLatencyHistoryAvailabilityAndEventShareEvidence`, `reportFiltersApplyToAllKpisAndPersistedEvents` |
| Visão geral / Incidentes / Relatórios | Incidentes ativos na janela | Incidentes iniciados na janela, não resolvidos | GET `/api/dashboard`; GET `/api/incidents?period=24h`; GET `/api/reports/operational` | `incidents` | `transientFailureStableRecoveryAndAlertOriginsAreLinked` |
| Sistemas / Detalhe | Nome, URL, ambiente, intervalo, pausa, manutenção, meta | Configuração salva pela conta | GET/POST/PUT/DELETE `/api/systems/{id}`; POST criação em `/api/systems` | `monitored_systems` | `editPersistsAndDeletionPreservesEventSnapshots`, `pausedAndMaintenanceSystemsRetainMeasuredHistory`, `recordedEnvironmentCannotBeReassignedAndArchivedEventsDoNotPolluteReports` |
| Sistemas / Detalhe | Último check / estado / motivo / última falha | Check mais recente e projeção transacional | GET `/api/systems/{id}`; GET `/api/dashboard/health` | `health_checks`, `monitored_systems` | `lastCheckLatencyHistoryAvailabilityAndEventShareEvidence`, `consistency.spec.ts` |
| Detalhe | Checks, HTTP, sucesso, duração, falha | Probe HTTP do backend | POST `/api/systems/{id}/checks`; GET `/api/systems/{id}/checks?period=24h` | `health_checks` | `successfulProbeProducesActualHttpResultAndAvailability`, `slowEndpointRecordsTimeoutWithoutInventingHttpStatus`, `InitialMonitoringListenerTest`, `consistency.spec.ts` |
| Detalhe | Disponibilidade, amostras válidas/excluídas, meta | Checks válidos persistidos na janela | GET `/api/systems/{id}/metrics?period=24h` | `health_checks` + meta em `monitored_systems` | `noCheckMetricsAreAbsentInsteadOfZeroAvailability`, `blockedConfigurationIsNotInventedApplicationDowntime` |
| Visão geral / Detalhe | Gráfico de latência e distribuição de falhas | Respostas HTTP / falhas efetivas | GET `/api/dashboard`; GET `/api/systems/{id}/latency?period=24h` | `health_checks` | `emptyEvidenceInvariantAcrossEveryReadScreen`, `lastCheckLatencyHistoryAvailabilityAndEventShareEvidence`, `DashboardServiceTest` |
| Incidentes | Contexto, severidade, início, investigação, resolução | Criação manual ou regra de checks consecutivos | GET/POST `/api/incidents`; PATCH `/{id}`, `/{id}/investigating`, `/{id}/resolve` | `incidents`, `operational_events` | `repeatedFailuresOpenSingleIncidentAtFirstFailureThenRecover`, `manualIncidentSupportsContextInvestigationAndResolution`, `operations.spec.ts` |
| Relatórios | KPIs e histórico por 24h/7d/30d, ambiente e sistema | Mesmo conjunto persistido selecionado no backend | GET `/api/reports/operational?period=7d&environment=PRODUCTION&systemId=...&page=0` | `health_checks`, `incidents`, `deployments`, `test_reports`, `operational_events` | `reportFiltersApplyToAllKpisAndPersistedEvents`, `OperationalReportServiceTest`, `ReportsPage.test.tsx` |
| Trilha de auditoria | Login, cadastro/alteração, check, incidente, integração, implantação, qualidade | Eventos reais gravados na operação | GET `/api/events?systemId=...&page=0&size=30` | `operational_events`, IDs do recurso e snapshot | `manualDeploymentAndImportedQualityHaveRealProvenance`, `recordedEnvironmentCannotBeReassignedAndArchivedEventsDoNotPolluteReports`, `operations.spec.ts` |
| Implantações / Detalhe | Versão, ambiente, timestamp, estado, origem e duração opcional | Registro manual explícito ou legado identificado | GET/POST `/api/deployments`; PATCH `/{id}/start`, `/success`, `/failure`, `/rollback` | `deployments` (`source`, `duration_seconds`, `execution_url`) | `manualDeploymentAndImportedQualityHaveRealProvenance`, `DeploymentServiceTest` |
| Qualidade | Testes, aprovação, linhas, ramificações e índice | Relatório de CI importado, sem inferência de testes | POST `/api/quality/reports`; GET `/api/quality/overview?period=30d`; GET `/api/quality/history` | `test_reports`, origem `API_IMPORT` | `manualDeploymentAndImportedQualityHaveRealProvenance`, `emptyEvidenceInvariantAcrossEveryReadScreen`, `QualityServiceTest` |
| Integrações | Configuração, conexão, última comunicação e erros | Conexão própria, HTTP real e execução persistida | GET `/api/integrations`; GET/PUT/DELETE `/api/connections/{slug}`; POST `/api/integrations/{slug}/health-check` | `integration_connections`, `integration_probes`, `integration_runs` | `independentIntegrationProbeCannotChangeMonitoredSystemHistory`, `IntegrationServiceTest`, `IntegrationCheckServiceTest`, `IntegrationsPage.test.tsx`, `operations.spec.ts` |
| Detalhe / Integrações | Auditoria, UUID/status, score externo e relatório | Resposta efetiva AI Web Auditor | POST `/api/connections/ai-web-auditor/actions`; GET `/api/connections/runs`; POST `/api/connections/runs/{id}/refresh` | `integration_runs` + API externa | `integrationCredentialsAreEncryptedAndAuditResultComesFromRemote`, `malformedRemoteContractRecordsFailure`, `operations.spec.ts` |
| Detalhe / Integrações | Workflow e estado de execução | Webhook Nexus + callback autenticado | POST `/api/connections/nexus-flow/actions`; PATCH `/api/connections/runs/{id}` | `integration_runs` + evento/outbox + serviço externo | `nexusReceivesDurableEventAndCompletionRequiresCallback`, `integrationRunCannotBeRefreshedByAnotherAccount`, `operations.spec.ts` |
| Alertas / Topo | Não lidos, lido/não lido e entidade relacionada | Notificação ligada ao evento real | GET `/api/notifications`; PATCH `/{id}/read`, `/{id}/unread`, `/read-all` | `notifications`, `operational_events` | `transientFailureStableRecoveryAndAlertOriginsAreLinked`, `NotificationServiceTest` |
| Perfil / Menu / Sessão | Usuário, nome/e-mail e saída | Usuário autenticado e versão de sessão no banco | POST `/api/auth/register`, `/api/auth/login`; GET/PUT `/api/account/me`; POST `/api/account/logout` | `app_users` + JWT validado no banco | `profilePersistsAndLogoutRevokesJwt`, `emailChangeRequiresPasswordAndReissuesSession`, `operations.spec.ts` |
| Configurações / Menu | Preferência de tema | Escolha real neste navegador | Sem endpoint; preferência local explícita | `localStorage` pelo provider de tema | `PulseOpsThemeProvider.test.tsx`, `operations.spec.ts` |
| Configurações | Valores ativos do motor e equipe quando ADMIN | Properties reais / usuários reais, permissões | GET `/api/settings/monitoring`; CRUD `/api/users` restrito | Configuração do servidor; `app_users` | `UserControllerSecurityTest`, `operations.spec.ts` |
| Todas | Isolamento contra UUID de outra conta | Escopo de proprietário e permissões no backend | Todas as rotas privadas acima | `owner_id` / sistema proprietário | `accountsCannotReadOrMutateEachOthersSystemsHistoryOrIncidents`, `integrationRunCannotBeRefreshedByAnotherAccount` |

## Validação final

Execução final em **04/10/2026**, sem falhas, erros ou testes ignorados nas suítes abaixo. Os resultados anteriores permanecem históricos; a fonte desta execução é [final-results.json](evidence/consistency/final-results.json).

| Verificação | Executados | Aprovados | Falhas | Evidência |
| --- | ---: | ---: | ---: | --- |
| Backend, `mvn clean verify` | 355 | 355 | 0 | [backend-verify-final.log](evidence/consistency/backend-verify-final.log) |
| Frontend, Vitest com cobertura, 15 arquivos | 55 | 55 | 0 | [frontend-coverage.log](evidence/consistency/frontend-coverage.log) |
| E2E operacional, Chromium contra containers | 4 | 4 | 0 | [e2e-results.json](evidence/consistency/e2e-results.json), [relatório HTML](evidence/consistency/e2e-report/index.html) |
| E2E conta nova + monitoramento automático + reinício | 1 | 1 | 0 | [e2e-consistency.json](evidence/consistency/e2e-consistency.json), [persistência](evidence/consistency/restart-persistence.json) |

Total: **415 casos aprovados**. Os 355 de backend incluem 26 cenários de `OperationalFlowIntegrationTest` com PostgreSQL Testcontainers e HTTP controlado, além de repository integration tests e testes unitários/controllers/security. Não se somam novamente esses 26 ao total. A suíte operacional contém 84 visitas de tela: 12 páginas × 1920/1366/1024/768/430/390/360 px. Console/pageerror: **0**; respostas HTTP ≥400 nessa navegação: **0**; overflow horizontal do documento: **0**. [browser-audit.json](evidence/consistency/browser-audit.json) registra cada combinação. Menus passaram por mouse, teclado, clique fora, Escape, foco e drawer mobile.

Cobertura JaCoCo após execução limpa: **95,56% de linhas (2.476/2.591)**, **93,48% de instruções**, **77,58% de branches**. Gate de linhas ≥85% aprovado. Cobertura unitária V8 do frontend sobre todo o código incluído: **39,46% de linhas (369/935)**, **34,19% de statements**, **32,25% de branches**, **24,52% de funções**. A cobertura do frontend ainda é limitada; os E2Es não são contabilizados nesse percentual. [Cobertura frontend](evidence/consistency/frontend-coverage/index.html) permite identificar os trechos sem cobertura unitária.

Build frontend (`tsc -b && vite build`) e lint aprovados: [build](evidence/consistency/frontend-build-final.log), [lint](evidence/consistency/frontend-lint-final.log). Build backend aprovado pelo `clean verify`. `docker compose build` aprovou backend e frontend: [docker-build-validated.log](evidence/consistency/docker-build-validated.log). Compose normal: três serviços saudáveis; Compose isolado: quatro, incluindo fixture, saudáveis após reinício. [Estado normal](evidence/consistency/compose-normal-final.json), [estado de teste](evidence/consistency/compose-test-final.json), [migrations e retenção do banco existente](evidence/consistency/migrations-existing-db.log). Treze migrations passaram também em PostgreSQL vazio no Testcontainers. Os volumes existentes foram preservados.

Após a validação, somente o projeto isolado `pulseops-e2e` foi parado para encerrar verificações controladas e liberar recursos; seu volume permanece preservado. O ambiente normal continua disponível em `http://localhost:3000`, com backend/banco ativos. O README mostra como iniciar novamente a infraestrutura exclusiva de testes.

**Cenário A:** cadastro → respostas vazias em painel, checks, incidentes, implantações, qualidade e conexões → sistema HTTP 200 com intervalo 30s → navegador fechado sem enviar POST de check → resultado persistido pelo backend (HTTP 200, 31 ms) → endpoint alterado para HTTP 500 → três falhas → estado indisponível, evento e um incidente → painel e relatório concordam.

**Cenário B:** reinício dos containers backend/frontend/PostgreSQL do projeto isolado → login novamente → mesmo usuário/sistema/checks/incidente → histórico anterior recuperado e painel reconstruído. A execução comprovou quatro checks antes e cinco depois, com o mesmo incidente e um check novo efetivo do scheduler. [Desktop antes do reinício](evidence/consistency/estado-real-desktop.png), [mobile após reinício](evidence/consistency/estado-real-mobile.png).

Também houve verificação manual no navegador do **alvo público real já cadastrado**: `https://matheus-samuel-dev.github.io/Portfolio/`. O backend recebeu HTTP 200, mediu **309 ms**, persistiu o check e exibiu histórico de **723 verificações reais na janela**. Não houve erro de console/rede nessa navegação. [JSON do monitoramento público](evidence/consistency/public-monitoring.json), [captura](evidence/consistency/public-monitoring.png). Esses números são uma fotografia do instante, não defaults do produto.

As capturas desktop/mobile foram inspecionadas: condição real, causa HTTP, amostras suficientes/insuficientes, incidente, origem e estados vazios permanecem legíveis. Integrações sem configuração mostram isso explicitamente e impedem o teste de conexão até salvar um destino.

Durante a execução inicial, um seletor E2E encontrou duas transições reais onde esperava um elemento único; foi corrigido para comprovar ambas. Outra asserção foi ajustada à serialização existente que omite nulos, mantendo a exigência de ausência de medição. A primeira tentativa do cenário automático também interrompeu um cadastro pendente; a execução subsequente e a execução final completa passaram sem mudança no código de autenticação. Falhas reais de SQL/schema encontradas durante implementação foram corrigidas e cobertas por regressão. Os números da tabela correspondem às execuções finais, não a tentativas intermediárias.

## Dependências externas e limites

1. **AI Web Auditor/Nexus Flow reais:** configurar URL HTTPS pública alcançável, endpoint de conexão, caminho de ação e token do serviço em Integrações. Testar conexão, executar ação sobre sistema próprio e consultar resultado; Nexus precisa implementar o callback descrito em `integrations.md`. Os contratos foram exercitados contra servidor controlado; isso não comprova credenciais ou workflows externos do usuário. O callback usa JWT da conta com expiração, sem token de serviço permanente.
2. **GitHub:** nenhum importador automático de Actions/webhooks foi implementado ou apresentado como conectado. A tela registra implantações manuais explícitas. Para mostrar execuções GitHub automaticamente falta implementar/configurar um adaptador real com credencial, associação sistema/repositório e origem verificável. Não há botão que prometa essa conexão hoje.
3. **CI:** o pipeline precisa produzir contagens/cobertura reais e enviar `POST /api/quality/reports` autenticado, com `systemId`, `totalTests`, `passedTests`, `failedTests`, `skippedTests`, `lineCoverage`, `branchCoverage` e `generatedAt`. O Pulse Ops importa o resumo e identifica `API_IMPORT`; não lê JUnit/JaCoCo diretamente nem verifica a execução do pipeline. Sem envio, a interface permanece vazia.
4. **Produção:** configurar perfil `prod`, secrets distintos e fortes, CORS explícito, HTTPS, domínio, backup do banco e da chave de criptografia. Executar os comandos do README, conferir `/actuator/health` e testar um alvo público real. A revisão não publicou o serviço nem validou TLS/backups da sua infraestrutura.
5. **Escala:** as consultas compartilhadas priorizam correção para uma carteira de aplicações. Parte da agregação ocorre em memória sobre registros já limitados ao proprietário/janela. Retenção, agregados SQL e particionamento precisam de medição antes de grandes volumes. Não há garantia de entrega exatamente uma vez ou política automática de retentativas de workflows.

## Arquivos e evidências

O inventário integral desta revisão, por comparação SHA-256 com o estado recebido, está em [arquivos-consistencia.md](arquivos-consistencia.md). O relatório anterior [revisao-pulseops.md](revisao-pulseops.md) mantém o histórico da primeira revisão; seus números de testes não são os resultados desta execução.

Nesta revisão: **19 arquivos criados, 114 alterados e 2 removidos**, além das evidências geradas. Logs intermediários foram reunidos em `docs/evidence/consistency`; scripts temporários de edição foram removidos. Dependências e outputs de build permanecem regeneráveis.
