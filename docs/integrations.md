# Integrações operacionais

O Pulse Ops observa e mantém evidências. O AI Web Auditor executa auditorias web; o Nexus Flow executa automações. A página `/integracoes` apresenta essas duas integrações disponíveis para configuração. Sistemas de negócio não fazem parte do catálogo de integrações.

## Configuração pela interface

1. Abra **Integrações → Configurar AI Web Auditor** ou **Configurar Nexus Flow**.
2. Informe a URL base do conector, endpoint de conexão, timeout e caminho da API/webhook. Não é necessário cadastrar o serviço remoto em Sistemas.
3. Informe o token Bearer quando exigido. A credencial é enviada pelo backend ao endpoint de conexão e às ações; nunca é devolvida ao navegador.
4. Opcionalmente configure a origem pública da interface para abrir relatórios.
5. Salve e use **Testar conexão**. Uma resposta HTTP 2xx do endpoint configurado comprova aquela comunicação; não garante que todas as ações ou contratos do serviço estejam autorizados.
6. Abra o sistema alvo para solicitar auditoria ou workflow e consultar as execuções persistidas.

Sem configuração, o estado é **Não configurada**; antes de um teste válido, **Aguardando teste**. Falhas apresentam a mensagem real sanitizada. Evidências com mais de cinco minutos deixam de comprovar uma conexão atual (`INTEGRATIONS_STALE_AFTER`). O cooldown de um minuto reutiliza o registro recente e informa que nenhuma nova requisição ocorreu (`INTEGRATIONS_CHECK_COOLDOWN`). Alterar a configuração invalida o cache anterior.

`integration_probes` contém os testes próprios dos conectores. Eles não contam como `health_checks` de uma aplicação, não alteram seu estado nem abrem incidentes nesse sistema. A comunicação bem-sucedida deriva de probe ou execução HTTP 2xx persistida; conclusão e relatório derivam de `integration_runs`. Relatórios de CI não são usados como resultados do AI Web Auditor. As antigas variáveis de associação por system-id foram retiradas da configuração normal; conexões persistidas são migradas copiando seu destino, sem manter essa dependência.

Configurações, provas e execuções pertencem à conta. Credenciais usam AES-256-GCM. Um campo de token vazio preserva a credencial existente; sua remoção precisa ser explícita.

## AI Web Auditor

Contrato implementado a partir da API existente do serviço:

```http
POST /api/audits
Authorization: Bearer <token do AI Web Auditor>
Idempotency-Key: <UUID da execução no Pulse Ops>
Content-Type: application/json
```

```json
{
  "url": "https://aplicacao-autorizada.example.org",
  "projectName": "Nome do sistema",
  "authorizationConfirmed": true,
  "allowDestructiveActions": false,
  "testEnvironment": false
}
```

O operador confirma que possui autorização para auditar o alvo. O Pulse Ops exige `id` UUID e `status` reconhecido na resposta. Registra PENDING, RUNNING, COMPLETED, FAILED ou CANCELLED; não considera uma solicitação aceita como auditoria concluída.

**Consultar resultado** chama `GET /api/audits/{id}` com o mesmo Bearer. Somente um `overallScore` inteiro entre 0 e 100 efetivamente devolvido por uma auditoria COMPLETED é exibido. Não é convertido em cobertura de linhas/ramificações. Quando concluída, a origem pública configurada permite abrir `/audits/{id}`.

Credencial recusada, timeout, HTTP inesperado e contrato inválido apresentam erro humano. A chamada possui limite de dez segundos. O Pulse Ops não instala Lighthouse, Playwright ou outro executor de auditoria para realizar esse trabalho internamente.

Para conectar seu serviço real: publique uma API HTTPS alcançável pelo backend, escolha um endpoint de saúde adequado e forneça um token real do AI Web Auditor. Uma instalação apenas em `localhost`/rede privada é rejeitada pelo ambiente normal. Use um gateway público autenticado ou uma implantação com conectividade e política de saída específica; não habilite acesso amplo a redes internas em produção.

## Nexus Flow

Informe o caminho do webhook publicado pelo workflow, por exemplo `/webhook/pulseops`. O payload enviado é:

```json
{
  "source": "pulseops",
  "type": "SYSTEM_DOWN",
  "eventId": "UUID do evento",
  "runId": "UUID da execução",
  "systemId": "UUID do sistema",
  "systemName": "Nome do sistema",
  "url": "https://sistema.example.org"
}
```

Chamadas manuais usam `MANUAL_REQUEST`. O token configurado vai em Authorization Bearer e o UUID da execução vai em Idempotency-Key.

A opção **Enviar evento automaticamente quando um sistema ficar indisponível** cria uma fila persistente junto da transação do evento. O scheduler entrega após o commit. O identificador único evita gerar duas execuções do mesmo evento; o receptor deve usar Idempotency-Key para deduplicar requisições após um reinício ou falha de transporte.

HTTP 2xx significa **Recebida pelo Nexus Flow**, não sucesso do workflow. Para informar progresso/conclusão, o workflow deve chamar:

```http
PATCH /api/connections/runs/{runId}
Authorization: Bearer <JWT do Pulse Ops da conta proprietária>
Content-Type: application/json
```

```json
{"state":"COMPLETED","externalId":"identificador-da-execucao-no-nexus"}
```

Estados aceitos: RUNNING, COMPLETED, FAILED e CANCELLED. Atualizações de outras contas e alterações após estado terminal são rejeitadas. O JWT do Pulse Ops expira; o serviço deve obter uma sessão válida e mantê-la fora do frontend. Não há token de serviço permanente nem assinatura HMAC de callback nesta arquitetura.

Não foi encontrado contrato/repositório real do Nexus Flow neste workspace. Portanto este é o contrato HTTP do adaptador do Pulse Ops: configure um workflow para consumi-lo e faça o callback. O fluxo foi exercitado contra servidor controlado nos testes, sem alegar que um workflow externo já está em operação. Entregas que falham ficam registradas como FAILED; não há política automática de múltiplas tentativas ou garantia de entrega exatamente uma vez.

## Endpoints do Pulse Ops

| Método e caminho | Função |
| --- | --- |
| GET `/api/integrations` | Catálogo e métricas persistidas |
| GET `/api/integrations/{slug}` | Estado e detalhes |
| POST `/api/integrations/{slug}/health-check` | Verificação HTTP real, com cooldown informado |
| GET/PUT/DELETE `/api/connections/{slug}` | Consultar/configurar/desconectar vínculo da conta |
| POST `/api/connections/{slug}/actions` | Solicitar ação com systemId e authorizationConfirmed |
| GET `/api/connections/runs?systemId=...&page=0&size=20` | Histórico paginado da conta |
| POST `/api/connections/runs/{id}/refresh` | Consultar resultado no AI Web Auditor |
| PATCH `/api/connections/runs/{id}` | Receber atualização autenticada do Nexus Flow |
| POST `/api/quality/reports` | Receber resultados reais de testes/CI; contrato distinto de auditorias web |
| GET `/api/events` | Timeline real de eventos operacionais |

Escritas exigem ADMIN ou DEVELOPER, além de propriedade dos recursos. Todas as rotas de dados exigem autenticação. Os endpoints Swagger/OpenAPI documentam os campos de cada contrato.

## Segurança

Todas as chamadas externas reutilizam a política SSRF: HTTP/HTTPS, validação do destino e DNS, endereços fixados no transporte, ausência de redirects e bloqueio de redes privadas/reservadas/metadados por padrão. Respostas têm limite de 2 MB; erros não ecoam corpo remoto, token ou stack trace.

Configuração de origem pública não altera o destino da API nem comprova disponibilidade. Uma página com URL pública e sem registros continua sem métricas. A permissão para usar um sistema remoto deve ser obtida pelo proprietário da integração.

## Testar sem dependências externas

O Compose de teste separado inicia um servidor HTTP com saúde, endpoints 503/lentos, API de auditoria e webhook. Execute os comandos de E2E do README. Os dados controlados ficam exclusivamente no volume/rede do ambiente de testes. Para validar serviços reais depois, substitua o cadastro remoto e token, teste a conexão, solicite uma ação e confira HTTP, estado e resultado persistidos.

