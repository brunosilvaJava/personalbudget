---
inclusion: always
---

# Convenções Spring Boot — Personal Budget

## Estrutura de Pacotes

Pacote raiz: `com.bts.personalbudget`

| Pacote | Responsabilidade |
|---|---|
| `config/` | Classes de configuração Spring |
| `controller/{feature}/` | Controllers REST por feature |
| `controller/{feature}/config/` | Interface com anotações OpenAPI |
| `core/domain/entity/` | Entidades JPA |
| `core/domain/enumerator/` | Enums de domínio |
| `core/domain/exception/` | Exceções de domínio |
| `core/domain/model/` | Domain models (records) — apenas `FinancialMovement` |
| `core/domain/service/` | Serviços com regras de negócio |
| `core/domain/service/{feature}/` | Subpacotes por feature (`balance/`, `fixedbill/`, `installmentbill/`, `recurrencebill/`, `dashboard/`) |
| `exception/` | Handler global de exceções (`ControllerExceptionHandler`, `InvalidFieldsException`) |
| `mapper/` | Interfaces MapStruct |
| `repository/` | Repositórios Spring Data JPA |
| `job/` | Jobs agendados |
| `util/` | Classes utilitárias |

> **Nota:** `FixedBill` e `InstallmentBill` são modelos de domínio localizados em seus respectivos subpacotes de serviço (`core/domain/service/fixedbill/` e `core/domain/service/installmentbill/`), não em `core/domain/model/`.

## Nomenclatura

- Controllers: `{Feature}Controller`
- Services: `{Feature}Service`
- Entidades JPA: `{Feature}Entity`
- Repositórios: `{Feature}Repository`
- Mappers: `{Feature}Mapper`
- DTOs de entrada: `{Feature}Request`, `{Feature}UpdateRequest`
- DTOs de saída: `{Feature}Response`, `Paged{Feature}Response`
- Interface OpenAPI: `{Feature}ControllerApiDocs`
- Enums: singular descritivo (`OperationType`, `FinancialMovementStatus`)

---

## Camada Controller

Controllers implementam `{Feature}ControllerApiDocs` (concentra todas as anotações OpenAPI).

```java
@Slf4j
@RestController
@RequestMapping("/financial_movement")
@RequiredArgsConstructor
public class FinancialMovementController implements FinancialMovementControllerApiDocs {

    private final FinancialMovementService service;
    private final FinancialMovementMapper mapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FinancialMovementResponse create(@RequestBody @Valid FinancialMovementRequest request) {
        log.info("m=create, request={}", request);
        return mapper.toResponse(service.create(mapper.toModel(request)));
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID code) {
        log.info("m=delete, code={}", code);
        service.delete(code);
    }
}
```

### DTOs são Java records com snake_case via @JsonProperty

```java
public record FinancialMovementRequest(
    @JsonProperty("operation_type") @NotNull OperationType operationType,
    @JsonProperty("description") @NotBlank String description,
    @JsonProperty("amount") @NotNull BigDecimal amount,
    @JsonProperty("movement_date") @NotNull LocalDate movementDate
) {}
```

### HTTP Status

| Operação | Método | Status |
|---|---|---|
| Criar | POST | 201 Created |
| Buscar | GET | 200 OK |
| Atualizar | PATCH | 200 OK |
| Deletar | DELETE | 204 No Content |

---

## Camada Service

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialMovementService {

    private final FinancialMovementRepository repository;
    private final FinancialMovementMapper mapper;

    @Transactional
    public FinancialMovement create(FinancialMovement model) {
        log.info("m=create, model={}", model);
        return mapper.toModel(repository.save(mapper.toEntity(model)));
    }

    public FinancialMovement find(UUID code) {
        log.info("m=find, code={}", code);
        return mapper.toModel(repository.findByCodeAndFlagActiveTrue(code)
            .orElseThrow(() -> new EntityNotFoundException("Financial movement not found")));
    }

    @Transactional
    public void delete(UUID code) {
        log.info("m=delete, code={}", code);
        FinancialMovementEntity entity = repository.findByCodeAndFlagActiveTrue(code)
            .orElseThrow(() -> new EntityNotFoundException("Financial movement not found"));
        entity.setFlagActive(false);
        repository.save(entity);
    }
}
```

- Recebem e retornam **domain models (records)**, nunca entidades diretamente
- Operações de escrita com `@Transactional`
- Métodos padrão: `create`, `find`, `update`, `delete`
- Batch: `create(List<Model>)` delega para `create(Model)`

---

## Entidades JPA

```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "financial_movement")
public class FinancialMovementEntity extends AuditingEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID code;

    @Enumerated(EnumType.STRING)
    private OperationType operationType;

    private String description;
    private BigDecimal amount;
    private Boolean flagActive;

    @PrePersist
    private void prePersist() {
        if (code == null) code = UUID.randomUUID();
        if (flagActive == null) flagActive = true;
    }

    @Override
    public boolean equals(Object o) { ... } // baseado em id e code

    @Override
    public int hashCode() { ... }
}
```

- Estendem `AuditingEntity` (`createdDate`, `lastModifiedDate` automáticos)
- `id` (Long) = chave interna; `code` (UUID) = identificador de negócio
- Soft delete: `flagActive = false` no lugar de DELETE físico

---

## Domain Models

```java
public record FinancialMovement(
    UUID code,
    OperationType operationType,
    String description,
    BigDecimal amount,
    LocalDate movementDate,
    FinancialMovementStatus status
) {}
```

- Java records imutáveis
- Trafegam entre service e mapper
- Nunca expostos diretamente na API

---

## Mappers (MapStruct)

```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FinancialMovementMapper {

    FinancialMovementMapper INSTANCE = Mappers.getMapper(FinancialMovementMapper.class);

    FinancialMovement toModel(FinancialMovementEntity entity);
    FinancialMovement toModel(FinancialMovementRequest request);
    FinancialMovementEntity toEntity(FinancialMovement model);
    FinancialMovementResponse toResponse(FinancialMovement model);
}
```

---

## Repositórios

```java
public interface FinancialMovementRepository extends JpaRepository<FinancialMovementEntity, Long> {

    Optional<FinancialMovementEntity> findByCodeAndFlagActiveTrue(UUID code);

    @Query("SELECT f FROM FinancialMovementEntity f WHERE f.flagActive = true AND f.movementDate BETWEEN :start AND :end")
    Page<FinancialMovementEntity> findAllActive(LocalDate start, LocalDate end, Pageable pageable);
}
```

- Sempre filtrar por `flagActive = true`
- Queries por nome de método quando possível; `@Query` para consultas complexas

---

## Tratamento de Exceções

```java
@Slf4j
@RestControllerAdvice
public class ControllerExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ValidationResponse handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                e -> toSnakeCase(e.getField()),
                FieldError::getDefaultMessage
            ));
        return new ValidationResponse("Campos inválidos", errors);
    }
}
```

- Exceções de domínio estendem `RuntimeException`
- Nomes de campo nos erros em `snake_case`

---

## Logging

```java
// Sempre no início de cada método público
log.info("m=create, request={}", request);
log.info("m=find, code={}, startDate={}", code, startDate);
```

- `@Slf4j` em todas as classes que logam
- Nível INFO para operações de negócio

---

## Paginação

- Parâmetros padrão: `page=0`, `size=10`, `sortBy=movementDate`, `sortDirection=DESC`
- Resposta inclui: `number`, `size`, `totalElements`, `totalPages`, `first`, `last`

---

## Datas e Horas

| Tipo | Uso |
|---|---|
| `LocalDate` | Apenas data |
| `LocalDateTime` | Data com hora |
| `Instant` | Timestamps de auditoria |

```java
// Conversões comuns
date.atStartOfDay()
date.atTime(23, 59, 59)
```

---

## Testes Unitários

```java
@ExtendWith(MockitoExtension.class)
class FinancialMovementServiceTest {

    @InjectMocks
    private FinancialMovementService service;

    @Mock
    private FinancialMovementRepository repository;

    @Spy
    private FinancialMovementMapper mapper = FinancialMovementMapper.INSTANCE;

    @Test
    void shouldCreateFinancialMovement() {
        // given / when / then
    }
}
```

- Factories em `core/domain/factory/` para geração de dados de teste
- Nome dos métodos: `should{Ação}{Condição}`
