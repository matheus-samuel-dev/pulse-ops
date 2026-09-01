# Segurança do PulseOps

O PulseOps inclui autenticação, autorização, proteção de chamadas outbound e hardening de containers, mas uma implantação segura ainda depende da configuração correta do ambiente.

## Reporte responsável

Não publique credenciais, tokens, dados reais ou detalhes exploráveis em uma issue pública. Envie o relato de forma privada ao responsável pelo repositório, incluindo:

- componente e versão/revisão afetados;
- impacto observado;
- passos mínimos para reprodução;
- evidências sem dados pessoais ou segredos;
- mitigação sugerida, se conhecida.

Evite acessar dados de terceiros, executar ações destrutivas ou ampliar o teste além do necessário para demonstrar o problema.

## Baseline para produção

- use `SPRING_PROFILES_ACTIVE=prod`;
- gere `JWT_SECRET` aleatório, exclusivo e com pelo menos 32 caracteres;
- use senhas distintas para PostgreSQL e nunca versione o `.env`;
- restrinja `CORS_ALLOWED_ORIGINS` aos domínios HTTPS reais;
- mantenha `MONITORING_ALLOW_PRIVATE_NETWORKS=false`;
- exponha publicamente somente o terminador TLS/reverse proxy;
- bloqueie as portas do backend e PostgreSQL no Security Group;
- configure certificado válido, HSTS no ponto de terminação TLS e renovação automática;
- armazene secrets no GitHub Environments ou em um secret manager;
- proteja o environment `production` com revisores e regras de branch;
- mantenha Docker, imagens-base e dependências atualizados;
- monitore `/actuator/health`, logs e consumo de volume;
- mantenha backups testados do volume PostgreSQL.

## Fronteira outbound

O cadastro de URLs monitoradas é uma fronteira sensível. A política existente valida URL, origem, DNS e endereços antes da persistência e antes de cada acesso. Não remova essa segunda validação: ela é necessária para reduzir risco de DNS rebinding.

Quando o monitoramento de um serviço privado for indispensável, prefira um agente isolado em rede própria. O opt-in de redes privadas deve permanecer limitado a um ambiente de desenvolvimento controlado.

## Credenciais demonstrativas

As contas `@pulseops.dev` e os defaults do Compose existem exclusivamente para demonstração local. Não os reutilize em ambientes acessíveis por outras pessoas. O perfil `prod` não executa o seed.

## Dependências e imagens

Antes de publicar uma release, execute:

```powershell
cd backend
.\mvnw.cmd clean verify

cd ..\frontend
npm ci
npm run lint
npm run test:run
npm run build
```

Além dos gates do projeto, habilite atualização automatizada de dependências e análise de vulnerabilidades no repositório ou registry usados pela implantação.
