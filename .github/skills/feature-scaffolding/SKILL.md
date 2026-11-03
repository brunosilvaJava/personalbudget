---
name: feature-scaffolding
description: 'Gera o esqueleto completo de uma nova feature de domínio no Personal Budget (Entity, Repository, Mapper, DTOs, Service, Controller+ApiDocs, migration Flyway e testes). Use quando o usuário pedir para "criar uma feature", "gerar um CRUD", "scaffold" ou "adicionar um novo domínio" ao projeto.'
metadata:
  argument-hint: <nome-da-feature> [campos e tipos] [regras de negócio]
---

# Feature Scaffolding — Personal Budget

Esta skill gera todos os arquivos de uma nova feature de domínio, seguindo rigorosamente as convenções de:
- `.github/copilot-instructions.md` (regras gerais, contrato e estado atual);
- `.github/instructions/feature-scaffolding.instructions.md` (pacotes, nomes, migrations e testes);
- `.github/instructions/spring-boot-patterns.instructions.md` (exemplos de código por camada).

Gere sempre código completo e funcional — nunca placeholders, comentários "implemente aqui" ou métodos vazios.

## 1. Reunir requisitos

Se não fornecido pelo usuário, pergunte:
- **Nome da feature** (singular, PascalCase, ex.: `Category`)
- **Campos** e tipos (ex.: `name: String`, `amount: BigDecimal`, `dueDate: LocalDate`, relações com outras entidades)
- **Regras de negócio** (status possíveis, validações obrigatórias, cálculos)
- **Endpoints necessários** (CRUD completo? apenas leitura? listagem paginada?)

Não prossiga para geração de código sem essas informações mínimas.

## 2. Inspecionar o projeto

Antes de escrever qualquer arquivo:
1. Leia a seção "Estado atual" de `.github/copilot-instructions.md` para saber o que está em andamento e o que está fora do escopo.
2. Liste `src/main/resources/db/migration/` e identifique o maior `V{n}__` existente — a nova migration usa `n+1`.
3. Abra uma feature existente semelhante em complexidade. Para um cadastro simples, use `Category` (`controller/category/`, `core/domain/model/Category.java`, `CategoryEntity`, `CategoryMapper`). Para uma feature com regra própria, use `core/domain/service/fixedbill/` ou `core/domain/service/income/`. Replique exatamente o estilo de código, anotações e imports usados.
4. Confirme o pacote raiz `com.bts.personalbudget` e a estrutura de pastas real do repositório (não assuma — verifique com `find`/`glob`).

## 3. Gerar os arquivos

Para uma feature `{Feature}` (ex.: `Category`), gerar, nesta ordem, **somente os artefatos necessários ao conjunto de endpoints solicitado pelo usuário**:

### 3.1 Migration Flyway
`src/main/resources/db/migration/V{n}__create_table_{feature_snake_case}.sql`
- Tabela com PK `id BIGINT AUTO_INCREMENT`, `code BINARY(16)` (UUID) único, colunas para cada campo, `flag_active TINYINT(1) DEFAULT 1`, `created_date` e `last_modified_date`, seguindo o formato das migrations existentes.
- FKs para relações informadas pelo usuário.
- Nunca edite uma migration já aplicada.

### 3.2 Entity JPA
`core/domain/entity/{Feature}Entity.java`
- `@Entity`, `@Table(name = "...")`, Lombok (`@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`), `extends AuditingEntity implements Serializable`.
- Campo `code` do tipo `UUID`, gerado em `@PrePersist`.

### 3.3 Domain Model
Record com apenas os campos relevantes ao negócio, nunca a entidade JPA.
- Feature simples: `core/domain/model/{Feature}.java`.
- Feature com regra, cálculo ou agregação próprios: no subpacote do service, ao lado dele (`core/domain/service/{feature}/`).

### 3.4 Repository
`repository/{Feature}Repository.java` — `extends JpaRepository<{Feature}Entity, Long>`, com métodos derivados (`findByCodeAndFlagActiveTrue`, paginação, etc.) conforme necessário.

### 3.5 Mapper (MapStruct)
`mapper/{Feature}Mapper.java` — `@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)`, métodos:
- `{Feature} toModel({Feature}Request request)` (somente se houver `create`)
- `{Feature}Entity toEntity({Feature} model)`
- `{Feature} toModel({Feature}Entity entity)`
- `{Feature}Response toResponse({Feature} model)`
- Versões em lista (`toModelList`, `toResponseList`) quando houver listagem.
- Para atualização parcial, use um objeto de domínio de atualização (como `FixedBillUpdate`) em vez de dar significado novo a `null` no model completo.
- `Paged{Feature}Response` é montado explicitamente a partir de `page.getContent()` + metadados de paginação (não mapear `Page` diretamente no MapStruct).

### 3.6 DTOs
No mesmo pacote do controller, `controller/{feature}/` (não existe subpacote `dto`):
- `{Feature}Request` — **somente se houver `create`**; campos de entrada com Bean Validation, `@JsonProperty` em snake_case e `@Schema`.
- `{Feature}UpdateRequest` — **somente se houver `update`**; campos opcionais para atualização parcial.
- `{Feature}Response` — campos de saída (inclui `code`, nunca `id`), `@JsonProperty` em snake_case.
- `Paged{Feature}Response` — se houver listagem paginada (`total_elements`, `total_pages` etc.).

### 3.7 Service
`core/domain/service/{feature}/{Feature}Service.java`
- `@Slf4j`, `@Service`, `@RequiredArgsConstructor`, `@Transactional` nos métodos de escrita.
- Log `log.info("m={método}, param={valor}")` no início de cada método público.
- Métodos: incluir apenas os necessários para os endpoints solicitados (`create`, `update`, `delete`, `findByCode`, `find`).
- Soft delete: aplicar `flagActive=false` em `delete`; `update` mantém o registro ativo.
- Código não encontrado: `EntityNotFoundException` (o handler responde 404). Conflito de estado: `ConflictException` (409).
- Datas de "hoje": `LocalDate.now(clock)` com o `Clock` injetado.

### 3.8 Controller + ApiDocs
- `controller/{feature}/config/{Feature}ControllerApiDocs.java` — interface com todas as anotações Swagger/OpenAPI (`@Operation`, `@ApiResponse`, etc.).
- `controller/{feature}/{Feature}Controller.java` — `@RestController`, `@Tag`, `@RequestMapping("/{feature_snake_case}")`, `@RequiredArgsConstructor`, `implements {Feature}ControllerApiDocs`; cria apenas os endpoints solicitados, responde com `ResponseEntity` (POST 201 com corpo, GET/PATCH 200, DELETE 204), delega tudo ao Service e usa o Mapper só para request→model e model→response.

### 3.9 Testes
- `src/test/java/.../core/domain/factory/{Feature}Factory.java` — builder de dados de teste válidos.
- Testes unitários do Service (`{Feature}ServiceTest`), do Controller e do Mapper, se relevante, com nomenclatura `should{Ação}{Condição}` (ex.: `shouldThrowExceptionWhenCategoryCodeNotFound`).
- Se a feature tem constraints, colunas geradas ou concorrência, crie um teste de integração em `src/test_integration` com TestContainers.
- Cobrir somente os casos correspondentes aos endpoints solicitados (ex.: criação com sucesso, validação de erro, atualização, soft delete, busca por code inexistente).

## 4. Validar

Depois de gerar os arquivos, rode (ou sugira rodar, se não for possível executar):
```
./gradlew test
./gradlew integrationTest   # quando houver persistência nova (precisa de Docker)
```
e resolva quaisquer erros de compilação antes de considerar a tarefa concluída. Se não conseguir rodar, diga isso na resposta.

## 5. Resumir

Ao final, liste todos os arquivos criados/alterados, agrupados por camada, e aponte próximos passos manuais que o usuário precise fazer (ex.: atualizar o contrato do front, ajustar documentação externa e a seção "Estado atual" de `.github/copilot-instructions.md`).
