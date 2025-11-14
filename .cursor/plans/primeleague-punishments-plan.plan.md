# Plano: Primeleague Punishments - Sistema de Punições

## Análise do Relatório

**O que está bom:**

- Pesquisa sólida de plugins existentes (LiteBans, Judicator, etc)
- Features essenciais identificadas (ban/mute/warn/kick, temp/permanent, UUID/IP)
- Integração com ecossistema Primeleague bem pensada

**O que precisa ajustar (Grug Brain):**

- Relatório muito extenso (636 linhas) - vamos simplificar
- Tabela SQL pode ser mais enxuta (menos campos = menos complexidade)
- Roadmap de 9h é otimista demais - vamos ser realistas
- Features "Nice-to-Have" podem esperar - MVP primeiro

## Objetivo

Sistema de punições simples e eficiente para Paper 1.8.8, integrado com:

- CoreAPI (PostgreSQL)
- ChatPlugin (mute check)
- ClansPlugin (alertas automáticos)
- DiscordPlugin (notificações)

**Filosofia:** Simples > Complexo. MVP funcional > Features extras.

---

## Fase 1: MVP Core (4-5h)

### 1.1 Estrutura PostgreSQL

**Tabela única `punishments` (simples, direto):**

```sql
CREATE TABLE IF NOT EXISTS punishments (
    id SERIAL PRIMARY KEY,
    player_uuid UUID NOT NULL,
    ip VARCHAR(45),  -- IPv4/IPv6 (opcional, para IP bans)
    type VARCHAR(20) NOT NULL,  -- 'ban', 'mute', 'warn', 'kick'
    reason TEXT,
    staff_uuid UUID,  -- Quem aplicou (NULL = automático)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,  -- NULL = permanente
    active BOOLEAN DEFAULT TRUE,  -- Para unban/unmute (soft delete)
    appealed BOOLEAN DEFAULT FALSE
    -- Sem FK para users(uuid) - UUID genérico, mais simples (Grug Brain)
);

CREATE INDEX IF NOT EXISTS idx_punishments_player ON punishments(player_uuid);
CREATE INDEX IF NOT EXISTS idx_punishments_type_active ON punishments(type, active, expires_at);
CREATE INDEX IF NOT EXISTS idx_punishments_ip ON punishments(ip) WHERE ip IS NOT NULL;
```

**Grug Brain:** Uma tabela só. Sem normalização excessiva. `active` para soft delete (mais rápido que DELETE). Sem FK (UUID genérico = mais simples).

### 1.2 Estrutura do Plugin

```
primeleague-punishments/
├── pom.xml (depende: CoreAPI, softdepend: ChatPlugin, ClansPlugin, DiscordPlugin)
├── src/main/java/com/primeleague/punishments/
│   ├── PunishPlugin.java (onEnable: createTables, register listeners/commands)
│   ├── managers/
│   │   └── PunishManager.java (isBanned, isMuted, applyPunish, getHistory)
│   ├── listeners/
│   │   └── PunishListener.java (PlayerLoginEvent, AsyncPlayerChatEvent)
│   ├── commands/
│   │   ├── BanCommand.java
│   │   ├── MuteCommand.java
│   │   ├── WarnCommand.java
│   │   ├── KickCommand.java
│   │   ├── UnbanCommand.java
│   │   ├── UnmuteCommand.java
│   │   └── HistoryCommand.java
│   └── integrations/
│       └── DiscordIntegration.java (notifyDiscord - similar ao ClansPlugin)
└── src/main/resources/
    ├── plugin.yml
    └── config.yml
```

**Grug Brain:** Estrutura simples, sem camadas desnecessárias. Integrações separadas.

### 1.3 PunishManager (Core Logic)

**Métodos essenciais:**

```java
public class PunishManager {
    // Cache simples (TTL 60s)
    private final Map<UUID, PunishmentCache> cache = new ConcurrentHashMap<>();
    private final PunishPlugin plugin;

    public PunishManager(PunishPlugin plugin) {
        this.plugin = plugin;
        // Task periódica para limpar cache expirado (60s TTL)
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            cleanExpiredCache();
        }, 1200L, 1200L); // A cada 60s (1200 ticks)
    }

    // Verifica se player está banido (UUID ou IP)
    public boolean isBanned(UUID uuid, String ip) {
        // 1. Check cache (TTL 60s)
        PunishmentCache cached = cache.get(uuid);
        if (cached != null && !cached.isExpired()) {
            return cached.isBanned();
        }

        // 2. Query DB: SELECT * FROM punishments WHERE
        //    (player_uuid = ? OR ip = ?) AND type = 'ban' AND active = TRUE
        //    AND (expires_at IS NULL OR expires_at > NOW())
        // 3. Update cache
        // 4. Return true se encontrou
    }

    // Verifica se player está mutado
    public boolean isMuted(UUID uuid) {
        // Similar ao isBanned, mas type = 'mute'
    }

    // Aplica punição (ban/mute/warn/kick)
    public boolean applyPunish(UUID playerUuid, String ip, String type, String reason,
                               UUID staffUuid, Long durationSeconds) {
        // 1. Insert DB
        // 2. Se type = 'ban', kick player (async)
        // 3. Se type = 'mute', invalidar cache
        invalidateCache(playerUuid);
        // 4. Se type = 'warn', notificar player
        // 5. Se type = 'kick', kick player
        // 6. Integrar Clans (addAlert se player tem clan)
        // 7. Integrar Discord (notifyDiscord)
        // 8. Return true/false
    }

    // Remove punição (unban/unmute)
    public boolean removePunish(UUID playerUuid, String type, UUID staffUuid) {
        // UPDATE punishments SET active = FALSE WHERE player_uuid = ? AND type = ? AND active = TRUE
        // Invalidar cache
        invalidateCache(playerUuid);
    }

    // Histórico de punições
    public List<PunishmentData> getHistory(UUID playerUuid) {
        // SELECT * FROM punishments WHERE player_uuid = ? ORDER BY created_at DESC LIMIT 50
    }

    // Parse duração (1h, 7d, 30d, etc) - Java 8 compatível
    public long parseDuration(String input) {
        if (input == null || input.isEmpty()) {
            return 0; // Permanente
        }

        // Regex: (\d+)([smhd])?
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)([smhd])?");
        java.util.regex.Matcher matcher = pattern.matcher(input.toLowerCase());

        if (matcher.find()) {
            long num = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);

            if (unit == null || unit.isEmpty()) {
                return num; // Segundos default
            }

            // Java 8: if/else ao invés de switch expression
            if ("s".equals(unit)) {
                return num;
            } else if ("m".equals(unit)) {
                return num * 60;
            } else if ("h".equals(unit)) {
                return num * 3600;
            } else if ("d".equals(unit)) {
                return num * 86400;
            }
        }

        return 0; // Inválido = permanente
    }

    // Invalidar cache (chamado em applyPunish/removePunish)
    private void invalidateCache(UUID uuid) {
        cache.remove(uuid);
    }

    // Limpar cache expirado (task periódica)
    private void cleanExpiredCache() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }
}
```

**Grug Brain:** Cache simples com TTL. Queries diretas. Sem DAO/Repository. Parse de tempo com regex (Java 8 compatível). Cache invalidação via BukkitScheduler (mais simples que ScheduledExecutorService).

### 1.4 Listeners

**PunishListener.java:**

```java
@EventHandler(priority = EventPriority.HIGHEST)
public void onPlayerLogin(PlayerLoginEvent event) {
    Player player = event.getPlayer();
    UUID uuid = player.getUniqueId();
    String ip = event.getAddress().getHostAddress();

    // Verificar ban (UUID ou IP)
    if (punishManager.isBanned(uuid, ip)) {
        // Buscar motivo do ban (query DB ou cache)
        String reason = punishManager.getBanReason(uuid, ip);
        event.disallow(Result.KICK_BANNED,
            "§cVocê está banido!\n§7Motivo: §f" + reason);
    }
}

@EventHandler(priority = EventPriority.LOWEST)
public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
    Player player = event.getPlayer();

    // Verificar mute
    if (punishManager.isMuted(player.getUniqueId())) {
        event.setCancelled(true);
        String reason = punishManager.getMuteReason(player.getUniqueId());
        // Scheduler para thread principal (Bukkit API)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.sendMessage("§cVocê está mutado!\n§7Motivo: §f" + reason);
        });
    }
}
```

**Grug Brain:** Listeners simples, prioridades corretas. Scheduler para thread principal quando necessário.

### 1.5 Comandos

**Estrutura básica (todos seguem padrão similar):**

```java
// /ban <player> [tempo] [reason]
// Exemplos:
// /ban Player123 hacking
// /ban Player123 7d uso de hacks
// /ban Player123 1h spam

public class BanCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // 1. Validar permissão: punish.ban
        // 2. Validar args (mínimo: player)
        // 3. Buscar player (online ou offline via CoreAPI)
        // 4. Parse tempo (se fornecido): 1h, 7d, 30d, etc
        // 5. Parse reason (resto dos args)
        // 6. Chamar punishManager.applyPunish()
        // 7. Mensagem de confirmação
    }
}
```

**Comandos MVP:**

- `/ban <player> [tempo] [reason]` - Ban permanente ou temporário
- `/mute <player> [tempo] [reason]` - Mute permanente ou temporário
- `/warn <player> [reason]` - Aviso (sempre permanente, sem tempo)
- `/kick <player> [reason]` - Kick (sem DB, só kick)
- `/unban <player>` - Remove ban
- `/unmute <player>` - Remove mute
- `/history <player>` - Histórico de punições

**Grug Brain:** Comandos diretos, sem subcomandos complexos. Parse de tempo via `parseDuration()` (1h, 7d, 30d).

### 1.6 Integração ChatPlugin

**Modificar `ChatListener.java` (primeleague-chat):**

```java
@EventHandler(priority = EventPriority.LOWEST)
public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
    // ... código existente ...

    // Verificar mute (ANTES de processar spam/filtros)
    Plugin punishPlugin = plugin.getServer().getPluginManager().getPlugin("PrimeleaguePunishments");
    if (punishPlugin != null && punishPlugin.isEnabled()) {
        // Usar API do PunishPlugin (método estático ou getInstance)
        if (com.primeleague.punishments.PunishPlugin.getInstance().getPunishManager().isMuted(player.getUniqueId())) {
            event.setCancelled(true);
            // Mensagem já enviada pelo PunishListener
            return;
        }
    }

    // ... resto do código (anti-spam, filtros, etc) ...
}
```

**Grug Brain:** Verificação simples via softdepend. Não quebra se PunishPlugin não estiver habilitado.

### 1.7 Integração ClansPlugin

**No `PunishManager.applyPunish()`:**

```java
// Se player tem clan, adicionar alerta
Plugin clansPlugin = plugin.getServer().getPluginManager().getPlugin("PrimeleagueClans");
if (clansPlugin != null && clansPlugin.isEnabled()) {
    com.primeleague.clans.ClansPlugin cp = (com.primeleague.clans.ClansPlugin) clansPlugin;
    com.primeleague.clans.models.ClanData clan = cp.getClansManager().getClanByMember(playerUuid);
    if (clan != null) {
        String alertType = type.equals("ban") ? "BAN" :
                          type.equals("mute") ? "PUNISHMENT" : "WARNING";
        String alertMsg = "Punição aplicada: " + type.toUpperCase() + " - " + reason;
        cp.getClansManager().addAlert(clan.getId(), playerUuid, alertType, alertMsg, staffUuid, null);
    }
}
```

**Grug Brain:** Integração direta via casting (softdepend). Similar ao DiscordIntegration do ClansPlugin.

### 1.8 Integração DiscordPlugin

**DiscordIntegration.java (similar ao ClansPlugin):**

```java
public void notifyDiscord(UUID playerUuid, String type, String reason, UUID staffUuid, Long durationSeconds) {
    // Verificar se Discord está habilitado
    if (!isDiscordEnabled()) return;

    // Rate limiting (60s TTL)
    String rateLimitKey = playerUuid.toString() + "_" + type;
    // ... rate limit check ...

    // Obter JDA (similar ao ClansPlugin)
    JDA jda = getJDA();
    if (jda == null) return;

    // Obter canal de moderação (config do DiscordPlugin)
    long channelId = getModChannelId(); // Config: discord.mod-channel-id
    TextChannel channel = jda.getTextChannelById(channelId);
    if (channel == null) return;

    // Criar embed
    EmbedBuilder embed = new EmbedBuilder();
    embed.setTitle("🔨 Nova Punição");
    embed.addField("Player", getPlayerName(playerUuid), true);
    embed.addField("Tipo", type.toUpperCase(), true);
    embed.addField("Motivo", reason, false);
    if (staffUuid != null) {
        embed.addField("Staff", getPlayerName(staffUuid), true);
    }
    if (durationSeconds != null) {
        embed.addField("Duração", formatDuration(durationSeconds), true);
    }
    embed.setColor(type.equals("ban") ? 0xFF0000 : type.equals("mute") ? 0xFFAA00 : 0xFFFF00);
    embed.setFooter(new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()));

    // Enviar async
    channel.sendMessage(embed.build()).queue();
}
```

**Grug Brain:** Reutiliza padrão do ClansPlugin. Rate limiting simples. Embed direto.

---

## Fase 2: Features Extras (2-3h) - Opcional

### 2.1 Auto-Punish Spam

**Integrar com ChatPlugin:**

```java
// No ChatListener (primeleague-chat), após detectar spam:
if (plugin.getConfig().getBoolean("auto-punish.spam.enabled", false)) {
    int spamCount = getSpamCount(player.getUniqueId());
    if (spamCount >= plugin.getConfig().getInt("auto-punish.spam.threshold", 5)) {
        // Auto-mute 1h
        Plugin punishPlugin = plugin.getServer().getPluginManager().getPlugin("PrimeleaguePunishments");
        if (punishPlugin != null) {
            com.primeleague.punishments.PunishPlugin.getInstance()
                .getPunishManager()
                .applyPunish(player.getUniqueId(), null, "mute", "Spam excessivo", null, 3600L);
        }
    }
}
```

**Grug Brain:** Feature simples, opcional. Config-driven.

### 2.2 Ragequit Mute (PvP)

**Listener para PlayerQuitEvent:**

```java
@EventHandler
public void onPlayerQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();

    // Verificar se saiu durante combate (PvP)
    // Integrar com plugin de PvP (se existir) ou usar heurística simples:
    // - Se player morreu nos últimos 10s, considerar ragequit
    if (isRagequit(player)) {
        // Auto-mute 5min
        punishManager.applyPunish(player.getUniqueId(), null, "mute",
            "Ragequit detectado", null, 300L);
    }
}
```

**Grug Brain:** Feature específica PvP. Heurística simples (pode melhorar depois).

### 2.3 Templates/Reasons

**config.yml:**

```yaml
templates:
  hacking: "Uso de hacks detectado"
  spam: "Spam excessivo"
  toxicity: "Comportamento tóxico"
  # ... mais templates ...

# Uso: /ban Player123 hacking
# Expande para: /ban Player123 "Uso de hacks detectado"
```

**Grug Brain:** Templates simples via config. Sem sistema complexo de categorias.

---

## Fase 3: Polish (1h) - Opcional

### 3.1 Appeals System

**Tabela simples:**

```sql
CREATE TABLE IF NOT EXISTS punishment_appeals (
    id SERIAL PRIMARY KEY,
    punishment_id INTEGER NOT NULL,
    player_uuid UUID NOT NULL,
    message TEXT,
    status VARCHAR(20) DEFAULT 'pending',  -- pending, approved, denied
    reviewed_by UUID,
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (punishment_id) REFERENCES punishments(id)
);
```

**Comando:** `/appeal <punishment_id> <message>`

**Grug Brain:** Sistema básico. Pode expandir depois se necessário.

### 3.2 GUI (Opcional)

**Comando `/punish <player>` abre GUI simples (Bukkit Inventory API):**

- Botões: Ban, Mute, Warn, Kick
- Ao clicar, pede reason via chat input (ou comando)

**Grug Brain:** GUI opcional. Comandos são suficientes para MVP.

---

## Config.yml

```yaml
# Templates de punições
templates:
  hacking: "Uso de hacks detectado"
  spam: "Spam excessivo"
  toxicity: "Comportamento tóxico"

# Auto-punish
auto-punish:
  spam:
    enabled: false
    threshold: 5  # Quantas vezes spam antes de auto-mute
  ragequit:
    enabled: false
    mute-duration: 300  # 5min em segundos

# Integrações
integrations:
  discord-notify: true
  clans-alert: true

# Cache
cache-ttl: 60  # Segundos
```

---

## Roadmap Realista

**Fase 1 (MVP Core):** 2h
- Tabela PostgreSQL (sem FK, simples)
- PunishManager core (isBanned, isMuted, applyPunish, parseDuration)
- Cache com TTL e invalidação (BukkitScheduler)
- Listeners (login, chat)

**Fase 2 (Comandos):** 1.5h
- Comandos básicos (ban, mute, warn, kick, unban, unmute, history)
- Parse de tempo integrado

**Fase 3 (Integrações):** 1h
- Integração ChatPlugin (mute check)
- Integração ClansPlugin (alertas)
- Integração DiscordPlugin (notificações)

**Fase 4 (Polish):** 0.5h
- Ajustes finais (cache invalidação, parse tempo)
- Testes básicos (opcional: MockBukkit para testes unitários)

**Fase 5 (Extras - Opcional):** 1-2h
- Auto-punish spam
- Ragequit mute
- Templates/reasons

**Fase 6 (V2 - Opcional):** 1h
- Appeals system básico
- GUI (se necessário)

**Total MVP: 4.5h** (realista com ajustes finais)

**Total Completo: 6-7h** (com extras)

---

## Decisões de Design (Grug Brain)

1. **Uma tabela só:** `punishments` com todos os tipos. Simples > Normalizado.
2. **Soft delete:** Campo `active` ao invés de DELETE. Mais rápido, histórico preservado.
3. **Sem FK:** UUID genérico, sem foreign key para `users(uuid)`. Mais simples, flexível.
4. **Cache simples:** ConcurrentHashMap com TTL 60s. Invalidação via BukkitScheduler (task periódica).
5. **Parse tempo:** Regex simples `(\d+)([smhd])?` com if/else (Java 8 compatível, sem switch expressions).
6. **Integrações via softdepend:** Não quebra se plugin não estiver habilitado.
7. **Comandos diretos:** Sem subcomandos complexos. Parse de tempo integrado no PunishManager.
8. **MVP primeiro:** Features extras podem esperar. Foco no essencial.

---

## Próximos Passos

1. Criar estrutura do plugin (pom.xml, plugin.yml, estrutura de pastas)
2. Implementar createTables() no PunishPlugin
3. Implementar PunishManager (core logic)
4. Implementar Listeners (login, chat)
5. Implementar Comandos (ban, mute, warn, kick, etc)
6. Integrar ChatPlugin (mute check)
7. Integrar ClansPlugin (alertas)
8. Integrar DiscordPlugin (notificações)
9. Testar MVP
10. Adicionar features extras (se necessário)

**Começar pelo MVP. Features extras depois.**

---

## Ajustes Finais (Baseado em Análise)

### Validações Técnicas

✅ **Java 8 Compatível:**
- Parse de tempo usa if/else (não switch expressions Java 14+)
- BukkitScheduler ao invés de ScheduledExecutorService (mais simples, já no projeto)
- Regex Pattern/Matcher funciona perfeitamente em Java 8

✅ **PostgreSQL:**
- Tabela `users` existe (confirmado no CoreAPI)
- UUID suportado nativamente
- Índices otimizados para queries frequentes (isBanned, isMuted)

✅ **Discord API (JDA 4.4.0):**
- EmbedBuilder e sendMessage() compatíveis (confirmado no ClansPlugin)
- Rate limiting simples (ConcurrentHashMap, TTL 60s)
- Async via queue() (thread-safe)

✅ **Paper 1.8.8:**
- PlayerLoginEvent e AsyncPlayerChatEvent funcionam
- Prioridades corretas (HIGHEST para login, LOWEST para chat)
- Scheduler para thread principal quando necessário

### Correções Aplicadas

1. **Parse de Tempo:** Método `parseDuration()` com regex e if/else (Java 8)
2. **Cache Invalidação:** Task periódica via BukkitScheduler (60s TTL)
3. **FK Removida:** UUID genérico, sem foreign key (mais simples)
4. **Estrutura Cache:** Classe `PunishmentCache` com timestamp para TTL

### Exemplo de Implementação (PunishmentCache)

```java
private static class PunishmentCache {
    private final boolean banned;
    private final boolean muted;
    private final long timestamp;
    private static final long TTL = 60000; // 60s

    public PunishmentCache(boolean banned, boolean muted) {
        this.banned = banned;
        this.muted = muted;
        this.timestamp = System.currentTimeMillis();
    }

    public boolean isBanned() {
        return banned;
    }

    public boolean isMuted() {
        return muted;
    }

    public boolean isExpired() {
        return isExpired(System.currentTimeMillis());
    }

    public boolean isExpired(long now) {
        return (now - timestamp) > TTL;
    }
}
```

**Grug Brain:** Classe interna estática, simples. TTL configurável via config.yml.