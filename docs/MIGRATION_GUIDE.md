# Guia de Migração: AfterBlockAnimations → AfterCore Inventory Framework

Este guia orienta a migração do sistema legado **AfterBlockAnimations (ABA)** para o novo **AfterCore Inventory Framework**.

## Índice

1. [Visão Geral](#visão-geral)
2. [Comparação Lado-a-Lado](#comparação-lado-a-lado)
3. [Passo-a-Passo da Migração](#passo-a-passo-da-migração)
4. [Breaking Changes](#breaking-changes)
5. [Novos Recursos](#novos-recursos)
6. [Exemplos Before/After](#exemplos-beforeafter)
7. [FAQ](#faq)

---

## Visão Geral

### Por Que Migrar?

O AfterCore Inventory Framework oferece:

- **Performance Superior**: Cache inteligente com 80-90% hit rate
- **Menos Duplicação**: Infraestrutura compartilhada entre plugins
- **Mais Recursos**: Paginação híbrida, tabs, drag-and-drop, shared inventories
- **Manutenibilidade**: Codebase centralizada, atualizações unificadas
- **Melhor DX**: API consistente, melhor error handling

### O Que Muda?

| Aspecto | AfterBlockAnimations | AfterCore Inventory |
|---------|---------------------|---------------------|
| **Configuração** | Formato proprietário | YAML padrão com schema |
| **Cache** | Por configuração | Global com Caffeine |
| **Animações** | Baseado em frames | Frame engine unificado |
| **Actions** | Hardcoded | Registry + ActionService |
| **Persistence** | Sem suporte | Database built-in |
| **Paginação** | Layout-only | NATIVE_ONLY, LAYOUT_ONLY, HYBRID |
| **Shared Inventories** | Não suportado | Suporte nativo |

---

## Comparação Lado-a-Lado

### Configuração YAML

#### ANTES (AfterBlockAnimations)

```yaml
inventories:
  main_menu:
    title: "&aMenu Principal"
    size: 27
    items:
      - slot: 13
        material: DIAMOND_SWORD
        name: "&cLoja"
        lore:
          - "&7Clique para abrir"
        actions:
          - "console: give %player% diamond 1"
```

#### DEPOIS (AfterCore)

```yaml
inventories:
  main_menu:
    title: "&aMenu Principal"
    size: 27
    items:
      - slot: 13
        material: DIAMOND_SWORD
        display_name: "&cLoja"
        lore:
          - "&7Clique para abrir"
        click_actions:
          - "console: give %player% diamond 1"
```

**Mudanças**:
- `name` → `display_name` (mais descritivo)
- `actions` → `click_actions` (explícito sobre quando executar)

---

### API de Código

#### ANTES (AfterBlockAnimations)

```java
// Abrir inventário
BlockAnimationManager manager = plugin.getManager();
manager.openInventory(player, "main_menu");

// Adicionar item dinamicamente
// Não suportado - requer recarregar config
```

#### DEPOIS (AfterCore)

```java
// Abrir inventário
InventoryService inv = AfterCore.get().inventory();
InventoryContext ctx = InventoryContext.builder(player)
    .withPlaceholder("player_level", "10")
    .build();
inv.openInventory(player, "main_menu", ctx);

// Adicionar item dinamicamente
inv.getState(player.getUniqueId()).ifPresent(state -> {
    state.setItem(13, new ItemStack(Material.DIAMOND));
    inv.refreshInventory(player);
});
```

**Melhorias**:
- ✅ Context system para placeholders
- ✅ State management para modificações dinâmicas
- ✅ Refresh sem reabrir inventário

---

## Passo-a-Passo da Migração

### 1. Adicionar Dependência

**pom.xml**:
```xml
<dependency>
    <groupId>com.afterlands</groupId>
    <artifactId>AfterCore</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

**plugin.yml**:
```yaml
depend: [AfterCore]
```

### 2. Converter Configurações

Execute o script de conversão automática (futuro) ou converta manualmente:

```bash
# Futuro: script de migração automática
java -jar AfterCore-MigrationTool.jar convert \
  --input plugins/AfterBlockAnimations/config.yml \
  --output plugins/AfterCore/inventories/migrated.yml
```

**Conversão Manual**:

1. Renomear campos:
   - `name` → `display_name`
   - `actions` → `click_actions`

2. Converter animações:
   - Old: `animations: [GLOW, SPIN]` → New: veja seção de animações

3. Adicionar metadata de versão:
   ```yaml
   config-version: 1
   ```

### 3. Atualizar Código Java

#### Remover Dependências Antigas

```java
// REMOVER
import com.afterlands.blockanimations.*;

// ADICIONAR
import com.afterlands.core.AfterCore;
import com.afterlands.core.inventory.*;
```

#### Atualizar Chamadas de API

**Abertura de Inventário**:

```java
// ANTES
blockAnimationManager.openInventory(player, "shop");

// DEPOIS
InventoryService inv = AfterCore.get().inventory();
InventoryContext ctx = InventoryContext.builder(player).build();
inv.openInventory(player, "shop", ctx);
```

**Modificação Dinâmica**:

```java
// ANTES
// Não suportado - precisava recarregar config

// DEPOIS
inv.getState(player.getUniqueId()).ifPresent(state -> {
    state.setData("page", 1);
    state.setItem(10, customItem);
    inv.refreshInventory(player);
});
```

**Persistência**:

```java
// ANTES
// Não suportado

// DEPOIS
// Salvar estado
UUID playerId = player.getUniqueId();
InventoryState state = inv.getState(playerId).orElse(new InventoryState());
state.setData("coins", 1000);
inv.saveState(playerId, "shop", state);

// Carregar estado
inv.loadState(playerId, "shop").thenAccept(loaded -> {
    if (loaded != null) {
        int coins = (int) loaded.getData("coins", 0);
    }
});
```

### 4. Converter Animações

#### ANTES

```yaml
items:
  - slot: 13
    material: DIAMOND_SWORD
    animation: GLOW
```

#### DEPOIS

```yaml
items:
  - slot: 13
    material: DIAMOND_SWORD
    # Sem animação inline - usar animation blocks

animations:
  - slot: 13
    type: ITEM_CYCLE
    interval: 10
    repeat: true
    frames:
      - material: DIAMOND_SWORD
        glow: true
      - material: DIAMOND_SWORD
        glow: false
```

### 5. Testar Migração

Checklist:
- [ ] Inventários abrem corretamente
- [ ] Placeholders são substituídos
- [ ] Actions funcionam (clique, hover)
- [ ] Animações rodam suavemente
- [ ] Paginação funciona (se aplicável)
- [ ] Persistência salva/carrega corretamente
- [ ] Performance mantém 20 TPS

---

## Breaking Changes

### 1. Nome de Campos em YAML

| Antigo | Novo | Motivo |
|--------|------|--------|
| `name` | `display_name` | Clareza (evita confusão com ID) |
| `actions` | `click_actions` | Explícito sobre tipo de ação |
| `lore` | `lore` | Sem mudança |

### 2. API de Abertura de Inventário

**ANTES**:
```java
manager.openInventory(player, "menu");
```

**DEPOIS**:
```java
InventoryContext ctx = InventoryContext.builder(player).build();
service.openInventory(player, "menu", ctx);
```

**Migração**: Wrap todas as chamadas com context builder.

### 3. Sistema de Actions

**ANTES**: Actions hardcoded no plugin.

**DEPOIS**: Actions registradas via `ActionService`.

**Impacto**: Actions customizadas precisam ser re-registradas:

```java
// ANTES
blockAnimations.registerAction("custom_action", handler);

// DEPOIS
ActionService actions = AfterCore.get().actions();
actions.registerHandler("custom_action", (player, args) -> {
    // Handler logic
});
```

### 4. Paginação

**ANTES**: Apenas layout-based (slots fixos).

**DEPOIS**: 3 modos (NATIVE_ONLY, LAYOUT_ONLY, HYBRID).

**Migração**:
- Se usava paginação → configurar `pagination.mode: LAYOUT_ONLY` para comportamento legado
- Ou migrar para HYBRID para melhor performance

---

## Novos Recursos

### 1. Inventory Tabs

Organize inventários em múltiplas tabs navegáveis:

```yaml
tabs:
  enabled: true
  items:
    - id: weapons
      slot: 0
      display_name: "&cArmas"
      icon: DIAMOND_SWORD
    - id: armor
      slot: 1
      display_name: "&9Armaduras"
      icon: DIAMOND_CHESTPLATE
```

### 2. Drag-and-Drop

Permita arrastar itens entre slots:

```yaml
drag_settings:
  enabled: true
  allowed_slots: [10-43]
  anti_dupe: true
```

### 3. Shared Inventories

Múltiplos players visualizando/interagindo com mesmo inventário:

```java
String sessionId = "trade_" + UUID.randomUUID();
inv.openSharedInventory(player1, "trade", sessionId, ctx1);
inv.openSharedInventory(player2, "trade", sessionId, ctx2);

// Mudanças são sincronizadas em tempo real
```

### 4. Database Persistence

Estado do inventário salvo automaticamente:

```yaml
persistence:
  enabled: true
  save_on_close: true
  auto_save_interval: 300  # 5 minutos
```

### 5. Paginação Híbrida

Melhor performance para inventários grandes:

```yaml
pagination:
  enabled: true
  mode: HYBRID  # Nativo para lista, layout para decoração
  items_per_page: 28
  item_slots: [10-16, 19-25, 28-34, 37-43]
  controls:
    previous_page:
      slot: 48
      material: ARROW
      display_name: "&ePágina Anterior"
    next_page:
      slot: 50
      material: ARROW
      display_name: "&ePróxima Página"
```

---

## Exemplos Before/After

### Exemplo 1: Menu Simples

#### ANTES
```yaml
inventories:
  main_menu:
    title: "Menu"
    size: 27
    items:
      - slot: 13
        material: COMPASS
        name: "&aNavegar"
        actions:
          - "console: warp spawn %player%"
```

```java
manager.openInventory(player, "main_menu");
```

#### DEPOIS
```yaml
inventories:
  main_menu:
    title: "Menu"
    size: 27
    items:
      - slot: 13
        material: COMPASS
        display_name: "&aNavegar"
        click_actions:
          - "console: warp spawn %player%"
```

```java
InventoryService inv = AfterCore.get().inventory();
InventoryContext ctx = InventoryContext.builder(player).build();
inv.openInventory(player, "main_menu", ctx);
```

---

### Exemplo 2: Shop com Paginação

#### ANTES
```yaml
# Paginação limitada, itens hardcoded
inventories:
  shop:
    title: "Loja"
    size: 54
    items:
      # 45 itens hardcoded...
```

#### DEPOIS
```yaml
inventories:
  shop:
    title: "Loja - Página %current_page%/%total_pages%"
    size: 54

    pagination:
      enabled: true
      mode: HYBRID
      items_per_page: 28
      item_slots: [10-16, 19-25, 28-34, 37-43]

      controls:
        previous_page:
          slot: 48
          material: ARROW
        next_page:
          slot: 50
          material: ARROW

    # Itens podem ser adicionados dinamicamente via API
```

```java
// Adicionar 100 itens dinamicamente
InventoryContext ctx = InventoryContext.builder(player).build();
inv.openInventory(player, "shop", ctx);

inv.getState(player.getUniqueId()).ifPresent(state -> {
    List<ItemStack> shopItems = loadShopItems(); // 100+ itens
    for (int i = 0; i < shopItems.size(); i++) {
        state.addPaginatedItem(shopItems.get(i));
    }
    inv.refreshInventory(player);
});
```

---

### Exemplo 3: Inventário com Animação

#### ANTES
```yaml
items:
  - slot: 13
    material: DIAMOND
    name: "&bLoading..."
    animation: PULSE
```

#### DEPOIS
```yaml
items:
  - slot: 13
    material: GRAY_STAINED_GLASS_PANE
    display_name: "&7Carregando..."

animations:
  - slot: 13
    type: ITEM_CYCLE
    interval: 5
    repeat: true
    frames:
      - material: GRAY_STAINED_GLASS_PANE
        display_name: "&7Carregando."
      - material: LIGHT_GRAY_STAINED_GLASS_PANE
        display_name: "&7Carregando.."
      - material: WHITE_STAINED_GLASS_PANE
        display_name: "&7Carregando..."
```

---

## FAQ

### P: Preciso reescrever todo o código?

**R**: Não. A migração é incremental:
1. Adicione dependência do AfterCore
2. Converta configurações YAML
3. Atualize chamadas de API (buscar/substituir)
4. Teste feature por feature

### P: O AfterCore tem impacto em TPS?

**R**: Não, na verdade **melhora** TPS:
- Cache inteligente reduz compilação de itens
- Thread pool otimizado para I/O
- Budget de 27ms/50ms (54% uso) @ 500 CCU

### P: Posso usar ambos simultaneamente?

**R**: Sim, mas **não recomendado**:
- Duplicação de cache (desperdício de memória)
- Conflitos de event priority
- Migre completamente assim que possível

### P: Como migrar inventários com muitos itens?

**R**: Use paginação HYBRID:
```yaml
pagination:
  mode: HYBRID  # Melhor performance
  items_per_page: 28
```

E adicione itens dinamicamente via API ao invés de YAML.

### P: E se eu tiver actions customizadas?

**R**: Re-registre via `ActionService`:
```java
@Override
public void onEnable() {
    ActionService actions = AfterCore.get().actions();

    actions.registerHandler("my_custom_action", (player, args) -> {
        // Handler logic
    });
}
```

### P: Perderei dados de players?

**R**: Não, se usar persistence:
```yaml
persistence:
  enabled: true
  save_on_close: true
```

Dados são salvos no database do AfterCore (MySQL ou SQLite).

### P: Como debugar problemas na migração?

**R**: Use diagnostics:
```
/acore memory    # Verifica memory leaks
/acore metrics   # Performance metrics
/acore all       # Relatório completo
```

E ative debug mode:
```yaml
# config.yml do AfterCore
debug: true
```

---

## Suporte

**Issues**: https://github.com/AfterLands/AfterCore/issues
**Discord**: https://discord.gg/afterlands
**Wiki**: https://wiki.afterlands.com/core

---

**Última Atualização**: 2026-01-08
**Versão do Guia**: 1.0.0
