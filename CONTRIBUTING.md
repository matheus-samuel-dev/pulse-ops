# Contribuindo com o PulseOps

O PulseOps prioriza comportamento correto, segurança e legibilidade. Mudanças devem preservar essas três propriedades antes de otimizar cobertura ou reduzir código.

## Preparação

Pré-requisitos:

- JDK 21;
- Docker Desktop / Docker Compose v2;
- Node.js 22 e npm;
- Git.

Suba a stack completa para uma verificação inicial:

```powershell
docker compose up -d --build
docker compose ps
```

## Fluxo sugerido

1. mantenha cada mudança pequena e focada em uma responsabilidade;
2. escreva ou ajuste testes que expressem o comportamento desejado;
3. execute os gates do módulo alterado;
4. valide integrações com Docker ativo quando tocar persistência ou migrations;
5. atualize README, OpenAPI e exemplos quando o contrato público mudar;
6. não inclua `.env`, tokens, dumps, artefatos de build ou credenciais.

## Gates obrigatórios

Backend:

```powershell
cd backend
.\mvnw.cmd clean verify
```

Frontend:

```powershell
cd frontend
npm ci
npm run lint
npm run test:run
npm run build
```

Stack:

```powershell
docker compose build
docker compose up -d
docker compose ps
```

Nenhum container deve permanecer em `unhealthy`, `restarting` ou `exited` durante a validação.

## Convenções de backend

- mantenha controllers sem regra de negócio;
- prefira services pequenos e coesos;
- use DTOs na fronteira HTTP e entidades apenas na camada interna;
- injete `Clock` em regras dependentes do tempo;
- represente transições inválidas com exceções de domínio explícitas;
- use Flyway para qualquer mudança de schema;
- mantenha `ddl-auto=validate`;
- faça mock de dependências, não de DTOs ou entidades simples;
- cubra sucesso, erro, limite, conflito, autorização e efeitos colaterais;
- valide interações com Mockito apenas quando elas forem parte do comportamento.

## Convenções de frontend

- mantenha contratos da API centralizados em `src/types` e `src/services`;
- preserve estados de loading, vazio e erro em toda tela assíncrona;
- não use `alert()`; use feedback visual integrado;
- mantenha labels, foco, semântica e navegação por teclado;
- valide desktop, tablet e mobile;
- não contorne erros TypeScript ou ESLint com tipos genéricos desnecessários.

## Migrations

Migrations aplicadas são imutáveis. Crie sempre um novo arquivo versionado em `backend/src/main/resources/db/migration/`. Teste a sequência completa contra PostgreSQL real por meio do Testcontainers antes de enviar a mudança.

## Commits e revisão

Use mensagens objetivas, no imperativo, que expliquem a intenção da alteração. Na descrição da revisão, informe:

- problema resolvido;
- decisão técnica relevante;
- testes executados e resultado;
- impacto em API, banco, segurança ou interface;
- screenshots quando houver alteração visual.
