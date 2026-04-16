---
inclusion: always
---

# Personal Budget — Stack Tecnológica

## Linguagem e Framework

- **Java 21** — use records, sealed classes e outras features modernas quando apropriado
- **Spring Boot 3.2.4** — framework principal

## Dependências Principais

| Biblioteca | Versão | Uso |
|---|---|---|
| Spring Data JPA + Hibernate | — | Persistência |
| MySQL | 8.0 | Banco de dados |
| Flyway | — | Migrations de banco |
| MapStruct | 1.5.5.Final | Mapeamento entre camadas |
| Lombok | 1.18.32 | Redução de boilerplate |
| SpringDoc OpenAPI | 2.6.0 | Documentação Swagger UI |
| Docker + Docker Compose | — | Containerização |
| Gradle | 8.6 | Build |
| JUnit 5 + Mockito | — | Testes unitários |
| TestContainers | 1.19.7 | Testes de integração com MySQL |

## Banco de Dados

- MySQL 8.0 em todos os ambientes
- Migrations versionadas com Flyway em `src/main/resources/db/migration/`
- Nomenclatura de migrations: `V{n}__{descricao}.sql`

## Modelo de Dados

```
financial_movement
├── id (PK, BIGINT), code (UUID)
├── operation_type (CREDIT | DEBIT)
├── description, amount, amount_paid
├── movement_date, due_date, pay_date
├── status (PENDING | LATE | PAID_OUT)
├── flag_active, recurrence_bill_code
└── created_date, last_modified_date

installment_bill
├── id, code, description, amount, operation_type
├── purchase_date, installment_total, installment_count
├── next_installment_date, last_installment_date
└── status, flag_active, created_date, last_modified_date

fixed_bill
├── id, code, description, amount, operation_type
├── recurrence_type (WEEKLY | MONTHLY | YEARLY)
├── start_date, end_date, next_due_date, reference_year
└── status, flag_active, created_date, last_modified_date

calendar_fixed_bill   ← dias de lançamento para fixed_bill MONTHLY
├── id, day_launch
└── id_fixed_bill (FK → fixed_bill)
```

## Infraestrutura

### Profiles Spring

| Profile | Uso |
|---|---|
| `local` | Desenvolvimento local com MySQL via Docker |
| `qa` | Ambiente de qualidade |
| _(default)_ | Produção |

### Docker Compose

```bash
# Apenas banco de dados
docker compose --profile dependents up -d

# Aplicação completa
docker compose --profile app up -d
```

## API REST

- Documentação interativa: `/swagger-ui/index.html`

| Recurso | Path base |
|---|---|
| Financial Movement | `/financial_movement` |
| Fixed Bill | `/fixed_bill` |
| Installment Bill | `/installment_bill` |
| Balance | `/balance` |
| Dashboard | `/dashboard` |

## Testes

| Tipo | Localização | Ferramenta |
|---|---|---|
| Unitários | `src/test/` | JUnit 5 + Mockito |
| Integração | `src/test_integration/` | TestContainers (MySQL) |
