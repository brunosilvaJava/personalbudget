---
name: feature-scaffolding
description: 'Gera o esqueleto completo de uma nova feature de domínio no Personal Budget (Entity, Repository, Mapper, DTOs, Service, Controller+ApiDocs, migration Flyway e testes). Use quando o usuário pedir para "criar uma feature", "gerar um CRUD", "scaffold" ou "adicionar um novo domínio" ao projeto.'
metadata:
  argument-hint: <nome-da-feature> [campos e tipos] [regras de negócio]
---

# Feature Scaffolding — Personal Budget

Esta skill gera todos os arquivos de uma nova feature de domínio, seguindo
rigorosamente as convenções descritas em
`.github/instructions/feature-scaffolding.instructions.md` e nos documentos
`.kiro/steering/*.md`. Gere sempre código completo e funcional — nunca
placeholders, comentários "implemente aqui" ou métodos vazios.

## 1. Reunir requisitos

Se não fornecido pelo usuário, pergunte:
- **Nome da feature** (singular, PascalCase, ex.: `Category`)
- **Campos** e tipos (ex.: `name: String`, `amount: BigDecimal`, `dueDate: LocalDate`, relações com outras entidades)
- **Regras de negócio** (status possíveis, validações obrigatórias, cálculos)
- **Endpoints necessários** (CRUD completo? apenas leitura? listagem paginada?)

Não prossiga para geração de código sem essas informações mínimas.

## 2. Inspecionar o projeto

Antes de escrever qualquer arquivo:
1. Liste `src/main/resources/db/migration/` e identifique o maior `V{n}__`
   existente — a nova migration usa `n+1`.
2. Abra uma feature existente semelhante em complexidade (ex.:
   `core/domain/service/fixedbill/` ou o pacote de `FinancialMovement`) para
   replicar exatamente o estilo de código, anotações e imports usados.
3. Confirme o pacote raiz `com.bts.personalbudget` e a estrutura de pastas
   real do repositório (não assuma — verifique com `find`/`glob`).

## 3. Gerar os arquivos

Para uma feature `{Feature}` (ex.: `Category`), gerar, nesta ordem, **somente os
artefatos necessários ao conjunto de endpoints solicitado pelo usuário**:

### 3.1 Migration Flyway
`src/main/resources/db/migration/V{n}__create_table_{feature_snake_case}.sql`
- Tabela com PK `id BIGINT AUTO_INCREMENT`, `code BINARY(16)` (UUID) único,
  colunas para cada campo, `flag_active TINYINT(1) DEFAULT 1`,
  `created_date DATETIME`, `last_modified_date DATETIME`.
- FKs para relações informadas pelo usuário.

### 3.2 Entity JPA
`core/domain/entity/{Feature}Entity.java`
- `@Entity`, `@Table(name = "...")`, Lombok (`@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`).
- Campo `code` do tipo `UUID`, gerado em `@PrePersist` ou no builder do Service.

### 3.3 Domain Model
`core/domain/model/{Feature}.java` (record) — apenas os campos relevantes ao negócio, nunca a entidade JPA.

### 3.4 Repository
`repository/{Feature}Repository.java` — `extends JpaRepository<{Feature}Entity, Long>`, com métodos derivados (`findByCodeAndFlagActiveTrue`, paginação, etc.) conforme necessário.

### 3.5 Mapper (MapStruct)
`mapper/{Feature}Mapper.java` — `@Mapper(componentModel = "spring")`, métodos:
- `{Feature} toDomain({Feature}Request request)` (somente se houver `create`)
- `{Feature} toDomain({Feature}UpdateRequest request)` (somente se houver
  `update`)
- `{Feature}Entity toEntity({Feature} domain)`
- `{Feature} toDomain({Feature}Entity entity)`
- `{Feature}Response toResponse({Feature} domain)`
- `List<{Feature}Response> toResponseList(List<{Feature}> domains)` (se houver
  listagem)
- `Paged{Feature}Response` deve ser montado explicitamente no
  Controller/Service a partir de `page.getContent()` + metadados de paginação
  (não mapear `Page` diretamente no MapStruct).

### 3.6 DTOs
`controller/{feature}/dto/` (ou pacote de DTOs já usado no projeto):
- `{Feature}Request` — **somente se houver `create`**; campos de entrada com
  Bean Validation, `@JsonProperty` em snake_case.
- `{Feature}UpdateRequest` — **somente se houver `update`**; campos opcionais para
  atualização parcial/total.
- `{Feature}Response` — campos de saída (inclui `code`, nunca `id`), `@JsonProperty` em snake_case.
- `Paged{Feature}Response` — se houver listagem paginada.

### 3.7 Service
`core/domain/service/{feature}/{Feature}Service.java`
- `@Slf4j`, `@RequiredArgsConstructor`, `@Transactional` nos métodos de escrita.
- Log `log.info("m={método}, param={valor}")` no início de cada método público.
- Métodos: incluir apenas os necessários para os endpoints solicitados
  (`create`, `update`, `delete`, `findByCode`, `findAll`/`findAllPaged`).
- Soft delete: aplicar `flagActive=false` em método `delete`; `update` mantém o
  registro ativo.
- Lança exceções de domínio existentes (ex.: `EntityNotFoundException` já usada no projeto) quando `code` não é encontrado.

### 3.8 Controller + ApiDocs
- `controller/{feature}/config/{Feature}ControllerApiDocs.java` — interface com todas as anotações Swagger/OpenAPI (`@Operation`, `@ApiResponse`, etc.).
- `controller/{feature}/{Feature}Controller.java` — `@RestController`,
  `@RequestMapping("/{feature_snake_case}")`, `@RequiredArgsConstructor`,
  `implements {Feature}ControllerApiDocs`; cria apenas os endpoints solicitados,
  delega tudo ao Service e usa o Mapper só para request→domain e
  domain→response.

### 3.9 Testes
- `src/test/java/.../core/domain/factory/{Feature}Factory.java` — builder de dados de teste válidos.
- Testes unitários do Service (`{Feature}ServiceTest`) e do Mapper, se relevante, com nomenclatura `should{Ação}{Condição}` (ex.: `shouldThrowExceptionWhenCategoryCodeNotFound`).
- Cobrir somente os casos correspondentes aos endpoints solicitados
  (ex.: criação com sucesso, validação de erro, atualização, soft delete,
  busca por code inexistente).

## 4. Validar

Depois de gerar os arquivos, rode (ou sugira rodar, se não for possível
executar):
```
./gradlew test
```
e resolva quaisquer erros de compilação antes de considerar a tarefa concluída.

## 5. Resumir

Ao final, liste todos os arquivos criados/alterados, agrupados por camada, e
aponte próximos passos manuais que o usuário precise fazer (ex.: registrar
rotas em algum gateway, ajustar documentação externa).
