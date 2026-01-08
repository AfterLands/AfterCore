# AfterMotion → AfterCore Migration Guide

Este documento orienta a migração do AfterMotion para usar a infraestrutura centralizada de actions do AfterCore.

## Visão Geral

O AfterCore agora fornece:
- Sistema completo de parsing de actions (suporta ambos dialetos: SimpleKV e MotionDSL)
- Handlers padrão reutilizáveis (message, actionbar, sound, title, teleport, potion, etc.)
- `ActionExecutor` com suporte a scopes (VIEWER, NEARBY, ALL)
- Integração com `ConditionService` para avaliar condições

## O que foi movido para o AfterCore

### Infraestrutura (já existia no core)
- `ActionService` - Interface principal
- `ActionSpec` - Modelo comum
- `ActionHandler` - Interface funcional
- `ActionScope` - Enum (VIEWER, NEARBY, ALL)
- `ActionTrigger` - Enum (@tick, @event)
- `ActionDialect` - Interface para parsers
- `SimpleKvActionDialect` - Parser simples
- `MotionActionDialect` - Parser avançado

### Novo no AfterCore (migrado do AfterMotion)
- `ActionExecutor` - Executor com suporte a scopes
- **Handlers padrão** (pacote `com.afterlands.core.actions.handlers`):
  - `MessageHandler` - Mensagens no chat
  - `ActionBarHandler` - Action bar (barra acima do hotbar)
  - `SoundHandler` - Sons padrão do Minecraft
  - `ResourcePackSoundHandler` - Sons customizados de resource pack
  - `TitleHandler` - Títulos (title + subtitle)
  - `TeleportHandler` - Teleporte (coordenadas absolutas/relativas)
  - `PotionHandler` - Efeitos de poção
  - `ConsoleCommandHandler` - Comandos como console
  - `PlayerCommandHandler` - Comandos como player

## Migração Passo a Passo

### 1. Remover Classes Duplicadas do AfterMotion

**Deletar completamente:**
- `ActionScope` (usar `com.afterlands.core.actions.ActionScope`)
- `ActionTrigger` (usar `com.afterlands.core.actions.ActionTrigger`)
- `ActionParser` (usar `ActionService.parse()`)
- `ParsedAction` (usar `ActionSpec`)
- `ConditionEvaluator` (usar `AfterCore.get().conditions()`)
- `ConditionOperator` (já existe no `ConditionService`)

### 2. Migrar de ActionParser para ActionService

**ANTES (AfterMotion):**
```java
// AfterMotion tinha seu próprio parser
ActionParser parser = new ActionParser();
ParsedAction action = parser.parse("@tick:20 [%player_level% > 5] message: Olá!");
```

**DEPOIS (AfterCore):**
```java
// Usar ActionService do core
ActionService actions = AfterCore.get().actions();
ActionSpec spec = actions.parse("@tick:20 [%player_level% > 5] message: Olá!");
```

### 3. Migrar Action Types para Handlers

**ANTES (AfterMotion):**
```java
// AfterMotion tinha classes concretas: MessageAction, SoundAction, etc.
public class MessageAction implements Action {
    @Override
    public void execute(Player target, String args) {
        target.sendMessage(args);
    }
}
```

**DEPOIS (AfterCore):**
```java
// Handlers já estão registrados no core
// Apenas use parse + executeAction
ActionSpec spec = AfterCore.get().actions().parse("message: &aOlá!");
AfterCore.get().executeAction(spec, player);
```

### 4. Migrar ActionExecutor

**ANTES (AfterMotion):**
```java
// AfterMotion tinha seu próprio executor
ActionExecutor executor = new ActionExecutor();
executor.execute(action, viewer, origin);
```

**DEPOIS (AfterCore):**
```java
// Usar método convenience da API
AfterCore.get().executeAction(spec, viewer);

// Ou com origem customizada:
AfterCore.get().executeAction(spec, viewer, customOrigin);
```

### 5. Migrar ConditionEvaluator

**ANTES (AfterMotion):**
```java
ConditionEvaluator evaluator = new ConditionEvaluator();
boolean result = evaluator.evaluate(player, "%player_level% > 5");
```

**DEPOIS (AfterCore):**
```java
ConditionService conditions = AfterCore.get().conditions();
ConditionContext ctx = ConditionContext.empty();

// Síncrono (se na main thread e sem async ops)
boolean result = conditions.evaluateSync(player, "%player_level% > 5", ctx);

// Ou assíncrono (recomendado)
conditions.evaluate(player, "%player_level% > 5", ctx)
    .thenAccept(result -> {
        if (result) {
            // Ação...
        }
    });
```

### 6. Atualizar Scene/ScenePlayback

**ANTES (AfterMotion):**
```java
public class Scene {
    private List<ParsedAction> actions;
    private ActionParser parser;

    public void load(List<String> lines) {
        this.actions = lines.stream()
            .map(parser::parse)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
}
```

**DEPOIS (AfterCore):**
```java
public class Scene {
    private List<ActionSpec> actions;

    public void load(List<String> lines) {
        ActionService actionService = AfterCore.get().actions();

        this.actions = lines.stream()
            .map(actionService::parse)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public void play(Player viewer) {
        for (ActionSpec spec : actions) {
            AfterCore.get().executeAction(spec, viewer);
        }
    }
}
```

### 7. Registrar Handlers Customizados (se necessário)

Se o AfterMotion tiver handlers **específicos** (não os genéricos já migrados):

```java
public class AfterMotionPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        ActionService actions = AfterCore.get().actions();

        // Registrar handlers customizados do AfterMotion
        actions.registerHandler("motion_specific", new MotionSpecificHandler());

        getLogger().info("AfterMotion handlers registrados!");
    }
}
```

## Breaking Changes

### API Changes
- `ParsedAction` → `ActionSpec`
- `ActionParser.parse()` → `ActionService.parse()`
- `ActionExecutor.execute()` → `AfterCore.get().executeAction()`

### Removidos
- `ConditionEvaluator` - Substituído por `ConditionService`
- `ActionScope` (duplicado) - Usar do core
- `ActionTrigger` (duplicado) - Usar do core

### Comportamento
- **PlaceholderAPI**: Agora é opcional gracefully (sem PAPI = valores literais)
- **Thread Safety**: Handlers garantem main thread quando necessário
- **Condições**: Sempre avaliadas antes de executar ações

## Exemplos Completos

### Exemplo 1: Parsing e Execução Simples

**ANTES:**
```java
ActionParser parser = new ActionParser();
ParsedAction action = parser.parse("message: &aOlá!");
executor.execute(action, player, player.getLocation());
```

**DEPOIS:**
```java
ActionSpec spec = AfterCore.get().actions().parse("message: &aOlá!");
AfterCore.get().executeAction(spec, player);
```

### Exemplo 2: Action com Scope

**ANTES:**
```java
// Código manual para resolver scope
ParsedAction action = parser.parse("@tick:20 message[nearby:10]: Explosão!");
if (action.getScope() == ActionScope.NEARBY) {
    for (Player nearby : getNearbyPlayers(origin, 10)) {
        action.execute(nearby);
    }
}
```

**DEPOIS:**
```java
// ActionExecutor resolve automaticamente
ActionSpec spec = AfterCore.get().actions().parse("@tick:20 message[nearby:10]: Explosão!");
AfterCore.get().executeAction(spec, viewer, explosionLocation);
```

### Exemplo 3: Action com Condição

**ANTES:**
```java
ParsedAction action = parser.parse("[%player_level% > 5] title: Parabéns!");
if (conditionEvaluator.evaluate(player, action.getCondition())) {
    action.execute(player);
}
```

**DEPOIS:**
```java
// Condições avaliadas automaticamente pelo ActionExecutor
ActionSpec spec = AfterCore.get().actions().parse("[%player_level% > 5] title: Parabéns!");
AfterCore.get().executeAction(spec, player);
```

### Exemplo 4: Scene Playback Completo

```java
public class Scene {
    private final String name;
    private final List<ActionSpec> actions;

    public static Scene load(String name, List<String> lines) {
        ActionService actionService = AfterCore.get().actions();

        List<ActionSpec> specs = lines.stream()
            .map(actionService::parse)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        return new Scene(name, specs);
    }

    public void play(Player viewer, Location origin) {
        for (ActionSpec spec : actions) {
            // Verificar trigger (se necessário)
            if (spec.trigger() == ActionTrigger.TICK && spec.timeTicks() != null) {
                // Agendar para depois
                scheduleAction(spec, viewer, origin);
            } else {
                // Executar imediatamente
                AfterCore.get().executeAction(spec, viewer, origin);
            }
        }
    }

    private void scheduleAction(ActionSpec spec, Player viewer, Location origin) {
        long delay = spec.timeTicks();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (viewer.isOnline()) {
                AfterCore.get().executeAction(spec, viewer, origin);
            }
        }, delay);
    }
}
```

## Checklist de Migração

- [ ] Deletar classes duplicadas (ActionScope, ActionTrigger, ActionParser, ParsedAction)
- [ ] Substituir `ActionParser.parse()` por `ActionService.parse()`
- [ ] Substituir `ParsedAction` por `ActionSpec`
- [ ] Migrar de `ConditionEvaluator` para `ConditionService`
- [ ] Atualizar Scene/ScenePlayback para usar AfterCore API
- [ ] Remover action handlers genéricos (já estão no core)
- [ ] Registrar apenas handlers customizados (se houver)
- [ ] Testar todos os cenários de actions
- [ ] Verificar integração com PlaceholderAPI
- [ ] Testar scopes (VIEWER, NEARBY, ALL)
- [ ] Testar condições

## Vantagens da Migração

1. **Menos código duplicado** - Infraestrutura compartilhada
2. **Manutenção centralizada** - Bugs corrigidos no core beneficiam todos
3. **Performance** - ActionExecutor otimizado com spatial queries
4. **Thread safety** - Garantida pelo core
5. **Extensibilidade** - Outros plugins podem reusar handlers
6. **Graceful degradation** - PlaceholderAPI opcional

## Suporte

Para dúvidas ou problemas na migração:
1. Consulte a documentação do AfterCore
2. Veja exemplos em `AfterCore/src/test/examples/`
3. Abra issue no repositório AfterCore

---

**Nota**: Esta migração não quebra funcionalidade, apenas centraliza código. Todos os recursos continuam funcionando.
