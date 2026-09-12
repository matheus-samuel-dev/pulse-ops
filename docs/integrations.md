# Integrações do Pulse Ops

A área `/integracoes` (também acessível por `/integrations`) apresenta o catálogo do ecossistema, com destaque para AI Web Auditor, disponibilidade, saúde, atividade e detalhes. Reutiliza o layout, tokens, componentes MUI, autenticação e monitoramento existentes. Não adiciona uma segunda base de eventos nem um cadastro paralelo de sistemas.

## Dados e vínculos

O catálogo contém AI Web Auditor, Arena Predict, PlaySpace, LogiTrack, HelpDesk e Gestão Hospitalar. Nomes, descrições e ícones são metadados; métricas vêm exclusivamente dos registros persistidos.

Cada entrada procura um `MonitoredSystem` pelo UUID configurado. Sem UUID, procura pelo nome exato do catálogo, ignorando maiúsculas. Um UUID explícito inexistente não faz fallback. Sem vínculo, a interface mostra **Não configurado**. A URL pública sozinha não cria um vínculo nem comprova conexão.

Cadastre o destino de monitoramento pela área **Sistemas**, com uma conta ADMIN. Defina base URL, health endpoint, código HTTP esperado, timeout e limite de latência usando a validação existente. Para associar um sistema com outro nome, configure seu UUID no backend:

| Sistema | Vínculo com o cadastro | Link público opcional |
| --- | --- | --- |
| AI Web Auditor | `AI_WEB_AUDITOR_SYSTEM_ID` | `AI_WEB_AUDITOR_URL` |
| Arena Predict | `ARENA_PREDICT_SYSTEM_ID` | `ARENA_PREDICT_URL` |
| PlaySpace | `PLAYSPACE_SYSTEM_ID` | `PLAYSPACE_URL` |
| LogiTrack | `LOGITRACK_SYSTEM_ID` | `LOGITRACK_URL` |
| HelpDesk | `HELPDESK_SYSTEM_ID` | `HELPDESK_URL` |
| Gestão Hospitalar | `HOSPITAL_SYSTEM_ID` | `HOSPITAL_URL` |

As variáveis estão documentadas em `.env.example` e repassadas pelo Compose. Reinicie o backend após alterar configurações. O link público aceita apenas HTTP/HTTPS, sem credenciais, query ou fragmento; somente a origem é publicada. Não coloque tokens nessas variáveis. URLs internas e health endpoints ficam protegidos nos DTOs da área.

## Status e métricas

- **Online**: check recente bem-sucedido, latência dentro do limite e sem estado operacional degradado.
- **Atenção**: resposta inesperada recuperável (por exemplo HTTP 4xx), latência elevada ou estado degradado calculado pelo monitoramento existente.
- **Offline**: falha de transporte, timeout, HTTP 5xx ou check malsucedido com estado DOWN.
- **Desconhecido**: nenhum check, check futuro/desatualizado ou monitoramento pausado. Ausência de configuração tem identificação própria.

`INTEGRATIONS_STALE_AFTER=PT5M` define a validade da evidência. A saúde é a porcentagem de checks bem-sucedidos nas últimas 24 horas, com no mínimo cinco amostras; abaixo disso, mostra `—`. Não representa um SLA contratado. “Com incidentes” conta entradas em Atenção ou Offline, e não a quantidade de tickets de incidente.

“Sistemas conectados” conta vínculos cadastrados, incluindo os temporariamente indisponíveis. “Eventos processados hoje” soma checks registrados e relatórios recebidos desde a meia-noite em `INTEGRATIONS_REPORTING_ZONE` (padrão `America/Sao_Paulo`), sem duplicar IDs compartilhados. Não conta webhooks nem artefatos não modelados.

A sincronização corresponde a `TestReport.createdAt`, o recebimento do relatório. A data de geração é `generatedAt`, apresentada separadamente. Um health check não altera a sincronização de dados.

## AI Web Auditor

A integração reutiliza seu `MonitoredSystem`, histórico de `HealthCheck` e relatórios de `TestReport` recebidos pelo contrato existente `POST /api/quality/reports`. O envio deve informar o ID do sistema e autenticar com perfil autorizado, conforme o contrato de qualidade já documentado na API.

O código atual não fornece contagens de screenshots, artefatos ou estado de webhook. A página não inventa essas métricas. Para conectar a instância externa efetiva, configure seu destino de monitoramento, vínculo e URL pública, e mantenha o produtor enviando relatórios pelo contrato existente. Nenhum endereço de produção foi presumido.

## API e autorização

| Método e endpoint | Perfil | Resultado |
| --- | --- | --- |
| `GET /api/integrations` | ADMIN, DEVELOPER, VIEWER | Catálogo, resumo, modo de leitura e horário da consulta |
| `GET /api/integrations/events` | ADMIN, DEVELOPER, VIEWER | Até 12 eventos nos últimos 30 dias |
| `GET /api/integrations/{id}` | ADMIN, DEVELOPER, VIEWER | Detalhes e métricas de uma entrada |
| `GET /api/integrations/{id}/events` | ADMIN, DEVELOPER, VIEWER | Atividade da entrada |
| `POST /api/integrations/{id}/health-check` | ADMIN, DEVELOPER | Resultado normalizado e persistido |

`id` é o slug fixo do catálogo: `ai-web-auditor`, `arena-predict`, `playspace`, `logitrack`, `helpdesk` ou `hospital`. Entrada desconhecida retorna 404; entrada sem configuração ou pausada retorna 422 no teste. O modo demonstrativo bloqueia escritas também no backend. Não há endpoint de cadastro nesta área; a administração permanece no fluxo existente de Sistemas.

GETs consultam o banco sem fazer chamadas externas. O frontend atualiza a cada 60 segundos somente enquanto a aba está visível, sem sobrepor consultas, e cancela requisições ao desmontar. O teste manual usa exclusivamente a configuração persistida e reaproveita checks recentes durante `INTEGRATIONS_CHECK_COOLDOWN=PT1M` (mínimo 30 segundos). A resposta inclui `cached` e `nextCheckAt`. O bloqueio em memória serializa chamadas por entrada dentro da instância; o cooldown persistido também funciona após reinício, mas não substitui um lock distribuído em deployments com várias réplicas.

## Segurança e operação

O teste mantém o pipeline de monitoramento, registro de resultado e incidentes automáticos existentes. O timeout inclui validação/resolução DNS e chamada HTTP. Endereços resolvidos e validados são fixados no cliente de rede, evitando uma segunda resolução sujeita a DNS rebinding. Redirecionamentos não são seguidos. Erros de DNS, recusa, timeout e respostas HTTP inesperadas são normalizados, sem expor mensagens internas nos DTOs da nova área.

A política existente de URLs bloqueia destinos privados por padrão. `MONITORING_ALLOW_PRIVATE_NETWORKS` deve seguir a política de implantação da organização; sua habilitação permite destinos privados e não deve ser usada como atalho para aceitar URLs de usuários não confiáveis. Nenhum endpoint da nova área aceita uma URL arbitrária enviada pelo frontend.

A migration `V3` adiciona apenas um índice de recebimento de relatórios. Reutilizam-se `MonitoredSystem`, `HealthCheck`, `TestReport` e `OperationalEventResponse`. Não há entidade Integration/IntegrationEvent nem uma nova tabela de logs.

O Compose preserva o modo demonstrativo existente por padrão: `DEMO_READ_ONLY=true`, `VITE_DEMO_MODE=true`, `MONITORING_ENABLED=false`. O banner identifica dados demonstrativos já gerados pelo seeder do projeto. Em produção, use a configuração de segurança e perfil apropriados do projeto; os novos sistemas não recebem métricas fictícias.

## Validação

Execute `mvn clean verify` no backend com Java 21 e Docker disponível para Testcontainers. No frontend: `npm run test:run`, `npm run lint`, `npm run build`. O build inclui TypeScript. Depois execute `docker compose config --quiet`, `docker compose build`, `docker compose up -d`, `docker compose ps` e consulte logs e `/actuator/health`.

Os resultados concretos desta entrega estão em [integrations-delivery.md](integrations-delivery.md).
