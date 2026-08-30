---
description: 'Convenções obrigatórias para criar uma nova feature de domínio no Personal Budget (camadas Spring Boot, DTOs, mapper, migrations Flyway e testes).'
applyTo: 'src/main/java/**/*.java, src/main/resources/db/migration/**/*.sql, src/test/java/**/*.java'
---

# Convenções para novas features — Personal Budget

Estas regras se aplicam sempre que arquivos Java em `src/main/java`,
migrations Flyway ou testes em `src/test/java` forem criados ou editados,
independente de estar usando o agente `feature-scaffolder` ou não.

## Estrutura de pacotes (raiz `com.bts.personalbudget`)

| Pacote | Responsabilidade |
|---|---|
| `controller/{feature}/` | Controllers REST por feature |
| `controller/{feature}/config/` | Interface com anotações OpenAPI (`{Feature}ControllerApiDocs`) |
| `core/domain/entity/` | Entidades JPA (`{Feature}Entity`) |
| `core/domain/enumerator/` | Enums de domínio |
| `core/domain/exception/` | Exceções de domínio |
| `core/domain/model/` | Domain models (records) |
| `core/domain/service/{feature}/` | Serviços com regras de negócio da feature |
| `core/domain/factory/` (em `src/test`) | Factories de teste |
| `mapper/` | Interfaces MapStruct (`{Feature}Mapper`) |
| `repository/` | Repositórios Spring Data JPA (`{Feature}Repository`) |

> Nota: features mais complexas (ex. `FixedBill`, `InstallmentBill`) mantêm
> seu domain model dentro do próprio subpacote de serviço, não em
> `core/domain/model/`. Para features novas e simples, prefira
> `core/domain/model/` como padrão, a menos que haja um motivo claro para
> localizar o model junto do serviço.

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
- Soft delete: setar `flagActive = false` no update; nunca `DELETE` físico.
- Campos de auditoria padrão: `createdDate`, `lastModifiedDate` (via `@CreatedDate`/`@LastModifiedDate` ou equivalente já usado no projeto).

## JSON e validação

- Campos JSON em `snake_case` via `@JsonProperty` nos DTOs (nomes de campo Java continuam `camelCase`).
- Validação de entrada com Bean Validation (`@Valid`, `@NotNull`, `@NotBlank`, etc.) nos `Request`/`UpdateRequest`.

## Logs

- Todo método público de Controller/Service inicia com:
  `log.info("m={nomeDoMétodo}, param={valorRelevante}")`.

## Migrations Flyway

- Local: `src/main/resources/db/migration/`.
- Nomenclatura: `V{n}__{descricao_snake_case}.sql`, onde `{n}` é o próximo
  inteiro sequencial após a última migration existente (verifique sempre os
  arquivos existentes antes de numerar; nunca reutilize ou pule números).
- Uma migration por feature/tabela nova; alterações de tabelas existentes vão
  em uma nova migration, nunca editando uma migration já aplicada.
- Tipos e defaults devem ser coerentes com o padrão MySQL 8.0 já usado nas
  migrations existentes (ex.: `BIGINT` para PK/FK, `VARCHAR` com tamanho
  explícito, `DATETIME` para timestamps, `flag_active TINYINT(1) DEFAULT 1`).

## Testes

- Nomenclatura de métodos: `should{Ação}{Condição}`.
- Dados de teste construídos via factories em `core/domain/factory/`
  (criar uma factory nova por feature quando não existir uma reutilizável).
- Testes unitários usam JUnit 5 + Mockito; testes de integração (quando
  aplicável) usam TestContainers com MySQL, seguindo os exemplos já
  presentes em `src/test_integration`.

## Documentação OpenAPI

- Toda anotação Swagger/OpenAPI fica concentrada na interface
  `{Feature}ControllerApiDocs`, implementada pelo Controller — o Controller
  em si permanece livre de anotações de documentação.
