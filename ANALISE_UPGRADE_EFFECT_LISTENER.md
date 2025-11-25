# Análise: UpgradeEffectListener - Problemas e Correções

## ✅ O QUE ESTÁ CORRETO

1. **Arquitetura Grug Brain**: Listener separado, simples, sem over-engineering
2. **Registro de eventos**: Correto no `PrimeFactions.java`
3. **Dependências**: Usa apenas `ClaimManager`, `UpgradeManager`, `ClansPlugin` (soft deps)
4. **Cache de upgrades**: `UpgradeManager` usa cache (evita queries frequentes)

---

## ❌ PROBLEMAS IDENTIFICADOS

### **1. BlockGrowEvent - `newState.update(true)` pode falhar**

**Problema:**
- Em Paper 1.8.8, `BlockState.update(true)` pode não funcionar corretamente durante o evento
- O `newState` pode estar desatualizado ou inválido

**Solução:**
- Usar `BukkitRunnable` para atualizar no próximo tick
- Verificar se o bloco ainda é válido antes de atualizar

### **2. EntityDeathEvent - Conflito de prioridade com FarmListener**

**Problema:**
- `FarmListener` usa `MONITOR` (processa DEPOIS de tudo)
- Meu listener usa `HIGH` (pode processar ANTES)
- Ordem: Meu listener modifica EXP → FarmListener processa
- Mas se houver outro listener modificando EXP, pode haver race condition

**Solução:**
- Usar `MONITOR` para garantir que modifico EXP após todos os processamentos
- Ou usar `HIGHEST` para garantir que minha modificação é final

### **3. CreatureSpawnEvent - Spawn durante evento pode causar loop**

**Problema:**
- Spawnar entidade durante `CreatureSpawnEvent` pode causar:
  - Loop infinito (spawn gera novo evento)
  - Thread safety issues
  - Performance problems

**Solução:**
- Marcar entidade com metadata para evitar processar novamente
- Usar `MONITOR` priority + delay de vários ticks
- Ou cancelar evento original e criar novo spawn com delay

### **4. UpgradeManager.getUpgrades() - Query síncrona em eventos frequentes**

**Problema:**
- `BlockGrowEvent` e `CreatureSpawnEvent` são MUITO frequentes
- `getUpgrades()` faz query síncrona se cache não existe
- Pode causar lag em eventos de crescimento de crops

**Solução:**
- Cache já existe, mas pode expirar
- Adicionar verificação assíncrona para cache miss (não bloquear evento)
- Retornar dados padrão (0,0,0,0) se cache não existe e query async

### **5. Thread Safety - Cache pode ter race condition**

**Problema:**
- `ConcurrentHashMap` é thread-safe para acesso
- Mas `getUpgrades()` pode ter múltiplas threads fazendo query para mesmo `clanId`
- Duas queries simultâneas = desperdício + possível inconsistência

**Solução:**
- Adicionar `synchronized` no método `getUpgrades()` apenas para cache miss
- Ou usar `ConcurrentHashMap.computeIfAbsent()` (já thread-safe)

---

## 🔧 CORREÇÕES PROPOSTAS

### Correção 1: BlockGrowEvent - Atualizar no próximo tick

```java
@EventHandler(priority = EventPriority.HIGH)
public void onCropGrow(BlockGrowEvent e) {
    // ... verificações ...

    if (Math.random() < (bonus / 100.0)) {
        // Atualizar no próximo tick (thread-safe)
        final Block block = e.getBlock();
        final BlockState newState = e.getNewState();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Verificar se bloco ainda é válido
            if (block.getType() == newState.getType() ||
                isCropType(block.getType())) {
                newState.update(true);
            }
        });
    }
}
```

### Correção 2: EntityDeathEvent - Prioridade MONITOR

```java
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void onEntityDeath(EntityDeathEvent e) {
    // MONITOR garante que processa DEPOIS de tudo
    // Modificar EXP no final garante que bônus é aplicado corretamente
}
```

### Correção 3: CreatureSpawnEvent - Metadata + delay

```java
@EventHandler(priority = EventPriority.MONITOR)
public void onCreatureSpawn(CreatureSpawnEvent e) {
    // ... verificações ...

    // Verificar se já foi processado (evitar loop)
    if (e.getEntity().hasMetadata("faction_spawn_bonus")) {
        return;
    }

    if (Math.random() < (bonus / 100.0)) {
        // Marcar entidade atual
        e.getEntity().setMetadata("faction_spawn_bonus",
            new FixedMetadataValue(plugin, true));

        // Spawn extra com delay (evitar conflitos)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (e.getEntity().isValid() && !e.getEntity().isDead()) {
                try {
                    e.getEntity().getWorld().spawnEntity(
                        e.getEntity().getLocation(),
                        e.getEntity().getType()
                    );
                } catch (Exception ignored) {}
            }
        }, 5L); // 5 ticks = 0.25s delay
    }
}
```

### Correção 4: UpgradeManager - Thread-safe cache miss

```java
public UpgradeData getUpgrades(int clanId) {
    // Verificar cache (thread-safe)
    UpgradeData cached = upgradeCache.get(clanId);
    if (cached != null) {
        return cached;
    }

    // Cache miss - usar computeIfAbsent (thread-safe)
    return upgradeCache.computeIfAbsent(clanId, k -> {
        // Carregar do banco
        // Query síncrona OK aqui (computeIfAbsent já sincroniza)
        return loadUpgradesFromDB(clanId);
    });
}
```

---

## ✅ CHECKLIST DE COMPATIBILIDADE

- [x] **Grug Brain**: Simples, direto, sem abstrações
- [x] **Paper 1.8.8**: Eventos compatíveis
- [x] **Thread Safety**: Correções propostas
- [x] **Arquitetura**: Segue padrão do projeto
- [x] **Performance**: Cache evita queries frequentes
- [x] **Conflitos**: Prioridades ajustadas

---

## 🎯 PRÓXIMOS PASSOS

1. Aplicar correções propostas
2. Testar em servidor de desenvolvimento
3. Verificar performance com muitos chunks claimados
4. Validar que upgrades realmente funcionam

