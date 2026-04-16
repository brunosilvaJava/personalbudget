---
inclusion: always
---

# Arquitetura em Camadas

```
HTTP Request
     │
     ▼
┌─────────────────────────────┐
│        Controller           │  REST, validação de entrada, mapeamento request→model
└────────────┬────────────────┘
             │ domain model (record)
             ▼
┌─────────────────────────────┐
│         Service             │  Regras de negócio, orquestração, @Transactional
└────────────┬────────────────┘
             │ entity
             ▼
┌─────────────────────────────┐
│        Repository           │  Spring Data JPA, queries, paginação
└────────────┬────────────────┘
             │
             ▼
          MySQL
```

O **Mapper** (MapStruct) atua transversalmente, convertendo entre:
- `Request/Response` — DTOs da API
- `Domain Model` — records/classes usados na lógica de negócio
- `Entity` — persistência JPA

O mapper também converte entre domínios: `FixedBill` → `FinancialMovement` e `InstallmentBill` → `FinancialMovement` (usado pelo job de recorrência).

## Regras de Camada

- Controllers **nunca** acessam repositórios diretamente
- Services **nunca** retornam entidades — sempre domain models
- Domain models **nunca** são expostos na API — sempre convertidos para Response
- Toda conversão entre camadas passa pelo Mapper
- `FixedBillService` e `InstallmentBillService` possuem repositórios de domínio próprios (interfaces em seus subpacotes) que encapsulam o JpaRepository
