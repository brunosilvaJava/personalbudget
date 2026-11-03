---
description: 'Padrões de código Spring Boot do PersonalBudget: controller, service, entity, mapper, repository, exceções, paginação e datas, com exemplos do código atual.'
applyTo: 'src/main/java/**/*.java'
---

# Padrões de código — PersonalBudget

Complementa `.github/copilot-instructions.md` (regras gerais) e `feature-scaffolding.instructions.md` (nomes, pacotes, migrations e testes). Os exemplos abaixo seguem o código atual da `master`.

## Controller

O controller implementa `{Feature}ControllerApiDocs` (anotações `@Operation` e `@ApiResponse` ficam na interface) e só leva `@Tag`. Responde com `ResponseEntity`, converte com o Mapper e nunca fala com repositório.

```java
@Slf4j
@Tag(name = "Income schedule", description = "API for managing the main income and its occurrences")
@RequiredArgsConstructor
@RequestMapping("/income_schedule")
@RestController
public class IncomeScheduleController implements IncomeScheduleControllerApiDocs {

    private final IncomeScheduleService incomeScheduleService;
    private final IncomeScheduleMapper incomeScheduleMapper;

    @Override
    @PostMapping
    public ResponseEntity<IncomeScheduleResponse> create(@Valid @RequestBody IncomeScheduleRequest request) {
        log.info("m=create, request={}", request);
        final IncomeSchedule schedule = incomeScheduleService.create(incomeScheduleMapper.toModel(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(incomeScheduleMapper.toResponse(schedule));
    }

    @Override
    @GetMapping("/{code}")
    public ResponseEntity<IncomeScheduleResponse> findByCode(@PathVariable UUID code) {
        log.info("m=findByCode, code={}", code);
        return ResponseEntity.ok(incomeScheduleMapper.toResponse(incomeScheduleService.findByCode(code)));
    }
}
```

## DTOs

Records no mesmo pacote do controller (não existe subpacote `dto`), com `@JsonProperty` em snake_case, Bean Validation e `@Schema` com descrição e exemplo.

```java
public record CategoryRequest(
        @NotBlank @Size(min = 3, max = 50) String name,
        @NotNull @JsonProperty("operation_type") OperationType operationType,
        @NotNull @JsonProperty("group_code") UUID groupCode) {
}
```

## Service

`@Slf4j`, `@Service`, `@RequiredArgsConstructor`. Recebe e devolve domain models. Escrita com `@Transactional`. Código inexistente lança `EntityNotFoundException` (o handler responde 404 com mensagem genérica).

**Relações obrigatórias:** o Mapper não preenche a relação (ele a ignora). O service busca a entidade relacionada, rejeita com `InvalidFieldsException` (400) se não existir e só então a atribui antes do `save`. O exemplo abaixo é o fluxo do `CategoryService.create` (que também normaliza o nome e rejeita nome duplicado):

```java
private final CategoryRepository categoryRepository;
private final CategoryGroupRepository categoryGroupRepository;
private final CategoryMapper categoryMapper;

@Transactional
public Category create(final Category category) {
    log.info("m=create, category={}", category);
    final CategoryGroupEntity group = categoryGroupRepository.findByCode(category.groupCode())
            .orElseThrow(() -> new InvalidFieldsException("Campos inválidos", CategoryService.class,
                    Map.of("groupCode", "grupo não encontrado")));

    final CategoryEntity entity = categoryMapper.toEntity(category);
    entity.setCategoryGroup(group);
    return categoryMapper.toModel(categoryRepository.save(entity));
}

public Category findByCode(final UUID code) {
    log.info("m=findByCode, code={}", code);
    return categoryMapper.toModel(categoryRepository.findByCode(code)
            .orElseThrow(() -> new EntityNotFoundException("CategoryEntity not found for code " + code)));
}
```

- Operações que alteram estado de ciclos ou configuração conferem a versão esperada e lançam `ConflictException` (409).
- Soft delete: `setFlagActive(false)` e salvar; as leituras usam `findByCodeAndFlagActiveTrue`.

## Entity

Estende `AuditingEntity` (`createdDate` e `lastModifiedDate` automáticos), `id` interno (`IDENTITY`), `code` UUID gerado em `@PrePersist`, enums com `@Enumerated(EnumType.STRING)`.

```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "category")
public class CategoryEntity extends AuditingEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar")
    private CategoryStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_group_id", nullable = false)
    private CategoryGroupEntity categoryGroup;

    @PrePersist
    public void prePersist() {
        if (code == null) {
            code = UUID.randomUUID();
        }
        if (status == null) {
            status = CategoryStatus.ACTIVE;
        }
    }
}
```

Tabelas só de inserção (histórico) usam entidade `@Immutable`, sem setters. Entidades com concorrência otimista levam `@Version`.

## Mapper (MapStruct)

```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CategoryMapper {

    CategoryMapper INSTANCE = Mappers.getMapper(CategoryMapper.class);

    Category toModel(CategoryRequest request);

    @Mapping(target = "groupCode", source = "categoryGroup.code")
    Category toModel(CategoryEntity entity);

    CategoryResponse toResponse(Category category);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "categoryGroup", ignore = true)
    CategoryEntity toEntity(Category category);
}
```

Quando uma relação depende de busca no banco, o Mapper a ignora e o service é dono dessa busca (ver "Service").

## Repository

- `JpaRepository<{Feature}Entity, Long>`. Prefira consultas por nome de método; use `@Query` para as complexas.
- Filtre por `flagActive = true` nas leituras usuais onde há soft delete. **Exceção:** as consultas de idempotência da recorrência (por exemplo `existsByRecurrenceBillCodeAndOccurrenceDate`) não filtram, porque um lançamento excluído conta como já gerado e não pode ser recriado.
- Leituras que serializam escritas concorrentes usam `@Lock(LockModeType.PESSIMISTIC_WRITE)` numa consulta própria (`findForUpdate...`).

## Tratamento de exceções

O `ControllerExceptionHandler` (em `com.bts.personalbudget.exception`) centraliza as respostas. Não crie handlers por controller.

| Exceção | Resposta |
|---|---|
| Bean Validation, enum inválido, parâmetro ausente ou com tipo errado, `InvalidFieldsException` | 400 `{message, validations}` (chaves em snake_case) |
| `EntityNotFoundException` | 404 `{message: "Registro não encontrado"}` |
| `ConflictException`, `OptimisticLockingFailureException`, `OptimisticLockException` | 409 `{message}` |
| Qualquer outra | 500 `{message: "Erro interno no servidor"}` |

`ConflictException`, `InvalidFieldsException` e `ErrorResponse` ficam em `com.bts.personalbudget.exception`: reuse-os. Exceções específicas do domínio (como `InstallmentBillAlreadyDeletedException`) ficam em `core/domain/exception/` e estendem `RuntimeException`. A mensagem de `ConflictException` é escrita para o usuário.

## Paginação e ordenação

- Parâmetros: `page` (a partir de 0), `size` (padrão 10), `sort_by` e `sort_direction`.
- Os campos ordenáveis são uma lista fechada por recurso, convertida pelo `SortFieldResolver`; campo desconhecido é 400, nunca 500.
- A resposta paginada monta o `Paged{Feature}Response` com `content, page, size, total_elements, total_pages, first, last`.

## Datas e horas

| Tipo | Uso |
|---|---|
| `LocalDate` | Apenas data |
| `LocalDateTime` | Data com hora, sem fuso |
| `YearMonth` | Mês nominal (`yyyy-MM`) |
| `Instant` | Timestamps de auditoria e históricos |

- Use o `Clock` injetado (`LocalDate.now(clock)`) em services e no job; o fuso é `America/Sao_Paulo`.
- Intervalos novos são `[início, fim)`.
- Conversões comuns: `date.atStartOfDay()`, `date.atTime(23, 59, 59)`.

## Logging

- `@Slf4j` nas classes que logam. Nível INFO para operações de negócio, com `m=` no início: `log.info("m=create, request={}", request)`.
- Não registre dados sensíveis.
