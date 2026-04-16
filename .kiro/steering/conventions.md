---
inclusion: always
---

# Convenções Gerais — Personal Budget

Este projeto segue as convenções detalhadas em `spring-boot-conventions.md`.

## Resumo Rápido

- Linguagem: **Java 21**, use records e features modernas
- Injeção de dependência: sempre via `@RequiredArgsConstructor` com campos `final`
- Boilerplate: Lombok (`@Getter`, `@Setter`, `@Builder`, `@Slf4j`, etc.)
- JSON: campos em `snake_case` via `@JsonProperty`
- Identificadores públicos: `code` (UUID); `id` (Long) é interno ao banco
- Soft delete: setar `flagActive = false`, nunca DELETE físico
- Logs: `log.info("m={método}, param={valor}")` no início de cada método público
- Testes: nomenclatura `should{Ação}{Condição}`, factories em `core/domain/factory/`
