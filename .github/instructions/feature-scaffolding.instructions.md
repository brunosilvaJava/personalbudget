---
description: 'Convenções obrigatórias para criar uma nova feature de domínio no Personal Budget (pacotes, nomes, camadas, migrations Flyway e testes).'
applyTo: 'src/main/java/**/*.java, src/main/resources/db/migration/**/*.sql, src/test/java/**/*.java, src/test_integration/**/*.java'
---

# Convenções para novas features — Personal Budget

Estas regras se aplicam sempre que arquivos Java em `src/main/java`, migrations Flyway ou testes (`src/test` e `src/test_integration`) forem criados ou editados, independente de estar usando o agente `feature-scaffolder` ou não.

Regras gerais do repositório: `.github/copilot-instructions.md`. Exemplos de código por camada: `spring-boot-patterns.instructions.md`.

## Estrutura de pacotes (raiz `com.bts.personalbudget`)

| Pacote | Responsabilidade |
|---|---|
| `controller/{feature}/` | Controller e DTOs (`Request`, `UpdateRequest`, `Response`, `Paged...Response`). Não existe subpacote `dto` |
| `controller/{feature}/config/` | Interface com anotações OpenAPI (`{Feature}ControllerApiDocs`) |
| `core/domain/entity/` | Entidades JPA (`{Feature}Entity`) |
| `core/domain/enumerator/` | Enums de domínio |
| `core/domain/exception/` | Exceções específicas do domínio (ex.: `InstallmentBillAlreadyDeletedException`) |
| `exception/` | Exceções e respostas de erro da API: `ConflictException` (409), `InvalidFieldsException` (400), `ErrorResponse` e o `ControllerExceptionHandler`. Reuse-as; não crie equivalentes |
| `core/domain/model/` | Domain models (records) de features simples |
| `core/domain/service/{feature}/` | Services com as regras de negócio da feature e, quando a feature tem regra própria, o domain model |
| `core/domain/factory/` (em `src/test`) | Factories de teste |
| `mapper/` | Interfaces MapStruct (`{Feature}Mapper`) |
| `repository/` | Repositórios Spring Data JPA (`{Feature}Repository`) |

**Onde fica o domain model:**
- Feature simples, parecida com um cadastro (ex.: `Category`) → `core/domain/model/`.
- Feature com regra, cálculo ou agregação próprios (ex.: `FixedBill`, `InstallmentBill`, `IncomeSchedule`, `PayCycle`) → no subpacote do service, ao lado dele.

## Nomenclatura

- Controllers: `{Feature}Controller`
- Services: `{Feature}Service`
- Entidades JPA: `{Feature}Entity`
- Repositórios: `{Feature}Repository`
- Mappers: `{Feature}Mapper`
- DTOs de entrada: `{Feature}Request`, `{Feature}UpdateRequest`
- DTOs de saída: `{Feature}Response`, `Paged{Feature}Response`
- Interface OpenAPI: `{Feature}ControllerApiDocs`
- Enums: singular descritivo (ex.: `OperationType`, `FinancialMovementStatus`)
- Testes: `should{Ação}{Condição}` (ex.: `shouldCreateCategoryWhenNameIsValid`)

## Camadas — regras rígidas

- Controllers **nunca** acessam repositórios diretamente; sempre passam pelo Service.
- Services **nunca** retornam entidades — sempre domain models (records).
- Domain models **nunca** são expostos na API — sempre convertidos para `{Feature}Response` via Mapper.
- Toda conversão entre camadas (Request/Response ↔ Domain Model ↔ Entity) passa pelo Mapper (MapStruct).
- Injeção de dependência sempre via `@RequiredArgsConstructor` com campos `final` (nunca `@Autowired` em campo).
- Boilerplate via Lombok (`@Getter`, `@Setter`, `@Builder`, `@Slf4j`, etc.), nunca getters/setters manuais.

## Identificadores e persistência

- Identificador público sempre `code` (UUID gerado na criação); `id` (Long, `@GeneratedValue`) é interno ao banco e nunca exposto na API.
- Soft delete: setar `flagActive = false`; nunca `DELETE` físico. As leituras usuais filtram por `flagActive = true`; a exceção são as consultas de idempotência da recorrência, que incluem registros excluídos.
- Auditoria: a entidade estende `AuditingEntity` (`createdDate` e `lastModifiedDate`). Tabelas só de inserção (histórico) usam entidade `@Immutable`, sem `AuditingEntity`.

## JSON, validação e erros

- Campos JSON em `snake_case` via `@JsonProperty` nos DTOs (nomes de campo Java continuam `camelCase`).
- Validação de entrada com Bean Validation (`@Valid`, `@NotNull`, `@NotBlank`, etc.) nos `Request`/`UpdateRequest`.
- Status: POST 201 (com corpo), GET 200, PATCH 200, DELETE 204. Código inexistente → 404; conflito de estado ou versão → 409 (`ConflictException`); validação → 400.
- Listas aceitam `page`, `size`, `sort_by` e `sort_direction`; os campos ordenáveis passam pelo `SortFieldResolver`.
- Datas "de hoje" usam o `Clock` injetado (`LocalDate.now(clock)`).

## Logs

- Todo método público de Controller/Service inicia com: `log.info("m={nomeDoMétodo}, param={valorRelevante}")`.

## Migrations Flyway

- Local: `src/main/resources/db/migration/`.
- Nomenclatura: `V{n}__{descricao_snake_case}.sql`, onde `{n}` é o próximo inteiro sequencial após a última migration existente (verifique sempre os arquivos existentes antes de numerar; nunca reutilize ou pule números).
- Uma migration por feature/tabela nova; alterações de tabelas existentes vão em uma nova migration, nunca editando uma migration já aplicada.
- Mudanças em tabelas existentes são expansivas: coluna anulável → backfill → conferência → `NOT NULL` em migration posterior.
- Para "no máximo um registro ativo", use coluna gerada (nula fora do estado ativo) com `UNIQUE`; invariantes entram como `CHECK`.
- Tipos e defaults devem ser coerentes com o padrão MySQL 8.0 já usado nas migrations existentes (ex.: `BIGINT` para PK/FK, `VARCHAR` com tamanho explícito, `DATETIME` para timestamps, `flag_active TINYINT(1) DEFAULT 1`).

## Testes

- Nomenclatura de métodos: `should{Ação}{Condição}`.
- Dados de teste construídos via factories em `core/domain/factory/` (criar uma factory nova por feature quando não existir uma reutilizável).
- Testes unitários (`src/test`) usam JUnit 5 + Mockito. Testes de integração (`src/test_integration`) usam TestContainers com MySQL e cobrem persistência: constraints, colunas geradas, `@Version` e bloqueios.
- Rode `./gradlew test` e, quando houver persistência, `./gradlew integrationTest`. Se não conseguir rodar, diga isso no PR.

## Documentação OpenAPI

- As anotações detalhadas (`@Operation`, `@ApiResponse`, `@Parameter`) ficam concentradas na interface `{Feature}ControllerApiDocs`, implementada pelo Controller. O Controller leva só `@Tag`; os DTOs levam `@Schema` com descrição e exemplo.
