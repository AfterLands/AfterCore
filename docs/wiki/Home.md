## AfterCore Wiki

**Última atualização:** 08/01/2026 - AfterCore v1.0.1

AfterCore é um **plugin-biblioteca** (dependency plugin) para o ecossistema AfterLands em **Spigot/Paper 1.8.8** com **Java 21**. Ele fornece infraestrutura compartilhada (DB async, actions, conditions, inventories, métricas, diagnósticos, etc.) para reduzir duplicação entre plugins e manter **20 TPS** em alta concorrência.

### Regras de ouro

- **Main thread sagrada**: zero I/O bloqueante (DB, filesystem, rede) na thread principal.
- **Graceful degradation**: dependências opcionais (ProtocolLib, PlaceholderAPI) **não podem crashar** o servidor.
- **API pública não expõe deps “shaded/relocated”**: tipos internos sombreados não devem aparecer nas assinaturas públicas.

### Índice (Wiki)

- **Comece aqui**
  - [Getting Started](Getting-Started)
  - [Visão geral da API pública](Public-API-Index)
  - [Lista completa de tipos `public`](Public-API-Complete-List)
  - [Threading & boas práticas](Threading-and-Performance)

- **API principal**
  - [`AfterCore` + `AfterCoreAPI`](AfterCoreAPI)

- **Serviços**
  - [`SchedulerService`](SchedulerService)
  - [`SqlService`](SqlService)
  - [`ConditionService`](ConditionService)
  - [`ActionService`](ActionService)
  - [`InventoryService` (Inventory Framework)](InventoryService)
  - [`ProtocolService`](ProtocolService)
  - [`DiagnosticsService`](DiagnosticsService)
  - [`MetricsService`](MetricsService)
  - [`ConfigService` + `MessageService`](Config-and-Messages)
  - [`CommandService`](CommandService)

- **Utilitários**
  - [`CoreResult<T>` e erros](CoreResult)
  - [Retry/Backoff](Retry)
  - [Rate limiting / cooldowns](Rate-Limiting)
  - [Strings e utilitários espaciais](Utilities)

### Links úteis

- **Inventory Framework**:
    - [Exemplos Práticos](Inventory-Framework-Examples)
    - [Referência da API](Inventory-Framework-API)
- **Migração**: `docs/MIGRATION_GUIDE.md`
- **Config default**: `src/main/resources/config.yml` e `src/main/resources/messages.yml`
