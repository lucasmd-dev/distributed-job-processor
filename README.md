# Distributed Job Processor

Aplicação full stack criada para demonstrar uma arquitetura de processamento assíncrono com foco em confiabilidade operacional. O projeto combina API REST, fila, workers stateless, retry com backoff, DLQ, idempotência, agendamento e um dashboard para acompanhamento das execuções.

## Visão Geral

Este projeto foi pensado como uma peça de portfólio técnico para mostrar domínio em:

- arquitetura distribuída orientada a filas
- separação entre plano de controle e execução
- resiliência com retry, dead-letter queue e idempotência
- rastreabilidade com histórico de execuções e auditoria
- organização de código em backend, frontend, testes e infraestrutura

## O Que O Projeto Entrega

- API para criar, listar, detalhar, cancelar e reprocessar jobs
- workers escaláveis horizontalmente
- retry exponencial com envio para DLQ quando o limite é atingido
- idempotência na criação e na execução do job
- scheduler para jobs futuros
- dashboard web para monitoramento e troubleshooting
- pipeline de CI para validar backend e frontend

## Arquitetura

```text
Cliente -> API (Spring Boot) -> RabbitMQ -> Workers -> PostgreSQL
                               |               |
                               |               -> histórico de execução
                               |
                               -> Redis (idempotência)
                               -> fila de retry com atraso
                               -> DLQ para falhas esgotadas
```

### Papéis da aplicação

- `api`: expõe a API REST, recebe os jobs e roda o scheduler
- `worker`: consome filas, executa handlers e registra o resultado

### Fluxo principal

1. O job é criado pela API.
2. A entrada é validada e a chave de idempotência é resolvida.
3. O job é publicado na fila principal ou marcado como agendado.
4. Um worker consome a mensagem e executa o handler correspondente.
5. Em caso de falha, o job entra em retry com backoff exponencial.
6. Após esgotar as tentativas, a mensagem segue para a DLQ.

## Stack

| Camada | Tecnologias |
|---|---|
| Backend | Java 21, Spring Boot 3.3.5, Spring Data JPA, RabbitMQ, Redis, PostgreSQL |
| Frontend | React 18, TypeScript, Vite, Tailwind CSS, React Query |
| Infra | Docker Compose |
| Testes | JUnit 5, Mockito, Testcontainers |

## Pontos Fortes Para Avaliação Técnica

- Modelagem clara do ciclo de vida dos jobs, com estados explícitos.
- Preocupação com cenários reais de operação: duplicidade, retry, DLQ, histórico e reprocessamento.
- Separação entre criação do job, publicação, consumo, execução e auditoria.
- Setup simples para rodar localmente com Docker Compose.
- Interface web que ajuda a validar o fluxo sem depender apenas de chamadas manuais na API.

## Como Executar Com Docker

```bash
cp .env.example .env
docker compose up --build
```

Se quiser subir múltiplos workers:

```bash
docker compose up --build --scale worker=3
```

### Serviços disponíveis

| Serviço | Endereço |
|---|---|
| API | [http://localhost:8080](http://localhost:8080) |
| Dashboard | [http://localhost:3000](http://localhost:3000) |
| Swagger UI | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) |
| RabbitMQ Management | [http://localhost:15672](http://localhost:15672) |

Credenciais padrão do RabbitMQ Management: `guest/guest`

### Ajuste de portas

Se alguma porta local já estiver ocupada, altere os valores no arquivo `.env` antes de subir a stack:

```env
DB_PORT=5433
RABBITMQ_PORT=5673
RABBITMQ_MANAGEMENT_PORT=15673
REDIS_PORT=6380
API_PORT=8081
FRONTEND_PORT=3001
```

## Desenvolvimento Local

### Pré-requisitos

- Java 21
- Node.js 20+
- Docker e Docker Compose para infraestrutura local e testes de integração

O repositório inclui um arquivo `.nvmrc` para facilitar o uso da versão correta do Node.

### Backend

```bash
cd backend
./mvnw test
./mvnw spring-boot:run
```

### Frontend

```bash
cd frontend
npm ci
npm run build
npm run dev
```

O frontend usa proxy do Vite apontando para `http://localhost:8080` durante o desenvolvimento local e injeta `X-API-Key` no proxy, sem expor a chave no bundle.

## Testes E Validação

- `backend`: `./mvnw test`
- `frontend`: `npm run build`
- os testes de integração usam Testcontainers
- quando Docker não está disponível, apenas os testes que dependem de containers são pulados
- o workflow em `.github/workflows/ci.yml` valida backend e frontend em `push` e `pull_request`

## API

Header obrigatório em todas as requisições:

```text
X-API-Key: dev-secret-key
```

### Endpoints principais

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/v1/jobs` | Cria um job |
| `GET` | `/api/v1/jobs` | Lista jobs com filtros opcionais |
| `GET` | `/api/v1/jobs/stats` | Retorna contagem por status |
| `GET` | `/api/v1/jobs/{id}` | Retorna detalhes do job e execuções |
| `GET` | `/api/v1/jobs/{id}/executions` | Retorna histórico de tentativas |
| `POST` | `/api/v1/jobs/{id}/cancel` | Cancela um job elegível |
| `POST` | `/api/v1/jobs/{id}/retry` | Reprocessa um job |

### Exemplo de criação

```bash
curl -X POST http://localhost:8080/api/v1/jobs \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev-secret-key" \
  -d '{
    "type": "EMAIL_SEND",
    "payload": "{\"to\":\"user@example.com\",\"subject\":\"Hello\"}"
  }'
```

### Tipos suportados

- `EMAIL_SEND`
- `REPORT_GENERATE`
- `WEBHOOK_DISPATCH`

### Exemplo de agendamento

```json
{
  "type": "REPORT_GENERATE",
  "payload": "{}",
  "scheduledAt": "2025-12-01T10:00:00Z"
}
```

### Exemplo de idempotência

```json
{
  "type": "EMAIL_SEND",
  "payload": "{\"to\":\"user@example.com\"}",
  "idempotencyKey": "email-welcome-user-42"
}
```

Se `idempotencyKey` não for enviada, a aplicação gera a chave automaticamente com base em `type + payload canonicalizado`.

## Estrutura Do Repositório

```text
backend/   API, domínio, mensageria, segurança e testes
frontend/  dashboard React
docker-compose.yml
README.md
```

## Licença

MIT
