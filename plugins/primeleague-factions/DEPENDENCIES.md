# Dependências - Primeleague Factions

## 📋 Visão Geral

Este documento descreve as dependências do plugin `primeleague-factions` e como elas são gerenciadas.

---

## 🔗 Dependências Obrigatórias

### 1. PrimeleagueCore

**Tipo:** Hard Dependency (`depend` no plugin.yml)

**Versão:** 1.0.0+

**Uso:**
- Acesso ao banco de dados PostgreSQL via `CoreAPI.getDatabase()`
- Pool de conexões HikariCP compartilhado
- Modelos de dados base (`PlayerData`)

**Conforme:** ARCHITECTURE.md

---

### 2. PrimeleagueClans

**Tipo:** Hard Dependency (`depend` no plugin.yml)

**Versão:** 1.0.0+

**Uso:**
- Sistema de Clans (grupos de jogadores)
- Modelos: `ClanData`
- API: `ClansPlugin.getInstance().getClansManager()`

**Razão da Dependência:**
- Factions funciona **sobre** o sistema de Clans
- Claims são feitos **por Clan**, não por player individual
- Power é gerenciado por player, mas territórios pertencem a Clans

**Observação:** 
- Esta é uma dependência arquitetural especial documentada aqui
- Factions e Clans são módulos interdependentes do sistema Primeleague
- Conforme decidido na arquitetura, Clans + Factions trabalham juntos

---

## 📦 Dependências Opcionais

### 1. PrimeleagueDiscord

**Tipo:** Soft Dependency (`softdepend` no plugin.yml)

**Versão:** 1.0.0+

**Uso:**
- Notificações Discord para eventos importantes
- Integração via `DiscordPlugin.getInstance().getDiscordBot()`

**Funcionalidades:**
- ✅ Notificação de claims de território
- ✅ Notificação de unclaims (abandono)
- ✅ Notificação de power crítico (< 0)
- ✅ Notificação de perda de power (morte)

**Configuração:**
```yaml
discord:
  enabled: false  # Habilitar/desabilitar notificações
  channel-id: ""  # ID do canal Discord
  color: 3447003  # Cor do embed (azul padrão)
```

**Comportamento:**
- Plugin funciona normalmente sem Discord
- Notificações são ignoradas silenciosamente se Discord não estiver habilitado
- Rate limiting automático para evitar spam

---

## 🏗️ Estrutura de Dependências

```
PrimeleagueCore (Infraestrutura)
    ▲
    │
    ├── PrimeleagueClans (Sistema de Clans)
    │       ▲
    │       │
    │       └── PrimeleagueFactions (Sistema de Factions)
    │                   │
    │                   └── PrimeleagueDiscord (Opcional)
    │
    └── [Outros Plugins]
```

---

## 📝 Notas de Arquitetura

### Por Que Factions Depende de Clans?

**Decisão Arquitetural:**
- Factions não é um sistema standalone
- É um **módulo de extensão** do sistema de Clans
- Claims são feitos por Clans, não por players individuais
- Power é gerenciado individualmente, mas territórios são coletivos

**Alternativa Rejeitada:**
- Sistema de Factions independente (muito complexo, duplicação de funcionalidades)
- Cada player ter seus próprios claims (não faz sentido para HCF)

**Conclusão:**
- ✅ Dependência de Clans é **arquiteturalmente correta**
- ✅ Documentada e justificada aqui
- ✅ Mantém simplicidade (Grug Brain)

---

## ✅ Checklist de Dependências

### Inicialização
- [x] Verifica Core no `onEnable()`
- [x] Verifica Clans no `onEnable()`
- [x] Discord é opcional (softdepend)

### Uso
- [x] Usa `CoreAPI` para banco de dados
- [x] Usa `ClansPlugin` para dados de clans
- [x] Verifica Discord antes de notificar

### Configuração
- [x] Config.yml com seção Discord opcional
- [x] Plugin funciona sem Discord

---

**Última atualização:** 2025-01-XX  
**Versão:** 1.0.0

