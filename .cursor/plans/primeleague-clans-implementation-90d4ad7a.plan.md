<!-- 90d4ad7a-ff70-4f3d-ab52-ccee38e29337 ddc81c88-9c8f-4659-a67d-887e253be2ca -->
# Plano de Implementação: Primeleague Clans

## Fase 1: MVP - Estrutura Base (Semana 1-2)

### 1.1 Estrutura do Projeto

- Criar `plugins/primeleague-clans/` seguindo padrão dos outros plugins
- `pom.xml`: Dependência apenas do Core (scope provided)
- `plugin.yml`: `depend: [PrimeleagueCore]`, `softdepend: [PlaceholderAPI, PrimeleagueDiscord, PrimeleagueEconomy]`, comandos em PT-BR
- Estrutura de pacotes: `com.primeleague.clans.{commands,listeners,models,utils}`

### 1.2 Tabelas PostgreSQL

Criar no `onEnable()` via `CoreAPI.getDatabase().getConnection()`:

**Tabela `clans`:**

```sql
CREATE TABLE IF NOT EXISTS clans (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,  -- Até 50 chars, pode ter espaços
    tag VARCHAR(20) NOT NULL,   -- Armazenar com códigos de cor, max 20 para segurança
    tag_clean VARCHAR(3) NOT NULL,  -- Tag sem cores para validação/unique
    leader_uuid UUID NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    description VARCHAR(255),
    discord_channel_id BIGINT,
    discord_role_id BIGINT,
    FOREIGN KEY (leader_uuid) REFERENCES users(uuid)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_clans_tag_clean ON clans(UPPER(tag_clean));  -- Índice único case-insensitive
CREATE INDEX IF NOT EXISTS idx_clans_name ON clans(name);
```

**Tabela `clan_members`:**

```sql
CREATE TABLE IF NOT EXISTS clan_members (
    clan_id INTEGER NOT NULL,
    player_uuid UUID NOT NULL,
    role VARCHAR(20) DEFAULT 'MEMBER',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (clan_id, player_uuid),
    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE,
    FOREIGN KEY (player_uuid) REFERENCES users(uuid)
);
CREATE INDEX IF NOT EXISTS idx_clan_members_clan ON clan_members(clan_id);
CREATE INDEX IF NOT EXISTS idx_clan_members_uuid ON clan_members(player_uuid);
```

**Tabela `clan_invites`:**

```sql
CREATE TABLE IF NOT EXISTS clan_invites (
    id SERIAL PRIMARY KEY,
    clan_id INTEGER NOT NULL,
    invited_uuid UUID NOT NULL,
    inviter_uuid UUID NOT NULL,
    expires_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP + INTERVAL '5 minutes',
    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE,
    FOREIGN KEY (invited_uuid) REFERENCES users(uuid)
);
CREATE INDEX IF NOT EXISTS idx_clan_invites_invited ON clan_invites(invited_uuid);
CREATE INDEX IF NOT EXISTS idx_clan_invites_expires ON clan_invites(expires_at);
```

### 1.3 Modelos de Dados

- `ClanData.java`: POJO simples (id, name, tag, leaderUuid, createdAt, etc.)
- `ClanMember.java`: POJO (clanId, playerUuid, role, joinedAt)
- Seguir padrão `PlayerData.java` do Core

### 1.4 ClansManager

- Classe principal de lógica de negócio
- Métodos: `createClan()`, `getClan()`, `getClanByMember()`, `invitePlayer()`, `acceptInvite()`, `kickMember()`, etc.
- Todas queries via `CoreAPI.getDatabase().getConnection()` com try-with-resources
- Validações inline (sem abstrações)

### 1.5 Validações Específicas

**Nome do clan:**

- Máximo 50 caracteres
- Pode conter espaços
- Regex: `^.{1,50}$` (aceita qualquer char, 1-50)
- Trim antes de validar

**Tag do clan:**

- Sempre exatamente 3 caracteres (sem contar códigos de cor)
- Pode ter cores do jogo (códigos §)
- Tag completa (com cores) máximo 20 caracteres (VARCHAR(20))
- Validação: `ChatColor.stripColor(tag).length() == 3` E `tag.length() <= 20`
- `tag_clean` para unique constraint (sem cores, case-insensitive via UPPER)
- Armazenar `tag_clean` em uppercase para comparação case-insensitive
- Regex para validar: aceita `§[0-9a-fk-or]` seguido de 3 chars

### 1.6 Comandos Básicos (PT-BR)

**`/clan criar <nome> <tag>`**

- Valida nome (1-50 chars, pode ter espaços, trim antes)
- Valida tag: `ChatColor.stripColor(tag).length() == 3` E `tag.length() <= 20`
- Armazenar `tag_clean` em uppercase para comparação case-insensitive
- Verifica se player já está em clan
- Verifica se tag já existe (case-insensitive no tag_clean via `UPPER(tag_clean)`)
- Cria clan e adiciona leader como membro
- Mensagem: `§aClan §e{name} §acriado com sucesso! Tag: {tag}`

**`/clan sair`**

- Verifica se é leader (não pode sair, precisa transferir)
- Remove do clan
- Mensagem: `§cVocê saiu do clan §e{name}§c.`

**`/clan info [clan]`**

- Mostra info do clan (nome, tag, membros, leader, criado em)
- Se sem argumento, mostra clan do player
- Formato similar a `/elo` (ChatColor.GOLD para título)

**`/clan membros [clan]`**

- Lista membros com roles (Leader, Officer, Member)
- Paginação se muitos membros

**`/clan convidar <player>`**

- Apenas Leader/Officer
- Cria invite com expiração 5min
- Mensagem para convidado: `§aVocê foi convidado para o clan §e{name}§a! Use /clan aceitar`

**`/clan aceitar [clan]`**

- Lista invites pendentes se sem argumento
- Aceita invite específico
- Adiciona como MEMBER
- Mensagem: `§aVocê entrou no clan §e{name}§a!`

### 1.7 Plugin Principal

- `ClansPlugin.java`: Estende JavaPlugin
- `onEnable()`: Verifica Core, cria tabelas, registra comandos/listeners
- Padrão igual `EconomyPlugin.java` e `EloPlugin.java`

## Fase 2: Integrações Básicas (Semana 3-4)

### 2.1 Integração com Stats/ELO

**Listener PvP:**

- `ClanStatsListener.java`: Escuta `PlayerDeathEvent`
- Quando membro faz kill/death, atualiza stats agregadas do clan
- Query async para não bloquear

**Tabela `clan_stats` (opcional - pode calcular on-the-fly):**

```sql
CREATE TABLE IF NOT EXISTS clan_stats (
    clan_id INTEGER PRIMARY KEY,
    total_kills INT DEFAULT 0,
    total_deaths INT DEFAULT 0,
    avg_elo DECIMAL(10,2) DEFAULT 0,
    member_count INT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_clan_stats_kills ON clan_stats(total_kills DESC);
```

**Comando `/clan top [elo|kills] [página]`:**

- Cache similar a `EloCommand` (ConcurrentHashMap com TTL 300s)
- Query agregada: `SELECT c.name, AVG(u.elo) as avg_elo FROM clans c JOIN clan_members cm ON c.id = cm.clan_id JOIN users u ON cm.player_uuid = u.uuid GROUP BY c.id ORDER BY avg_elo DESC`
- Formato similar a `/elo top`

### 2.2 Integração com Economy

**Tabela `clan_bank`:**

```sql
CREATE TABLE IF NOT EXISTS clan_bank (
    clan_id INTEGER PRIMARY KEY,
    balance_cents BIGINT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
);
```

**Comandos:**

- `/clan banco`: Mostra saldo do clan
- `/clan depositar <valor>`: Transfere do player para clan bank
- `/clan sacar <valor>`: Transfere do clan bank para player (apenas Leader/Officer)

**Integração:**

- Depositar: `EconomyAPI.removeMoney(playerUuid, amount, "CLAN_DEPOSIT")` + atualizar `clan_bank.balance_cents` via query
- Sacar: Verificar `clan_bank.balance_cents >= amount` + `EconomyAPI.addMoney(playerUuid, amount, "CLAN_WITHDRAW")` + atualizar `clan_bank.balance_cents` via query
- Logar em `economy_transactions` com type "CLAN_DEPOSIT", "CLAN_WITHDRAW" via `EconomyAPI.logTransactionPublic()`
- Verificar se Economy está habilitado: `EconomyAPI.isEnabled()` (softdepend)

### 2.3 PlaceholderAPI

**Expansão `ClansPlaceholderExpansion.java`:**

- Identifier: `"clans"` (plural, seguindo padrão "economy")
- `%clans_name%`: Nome do clan do player
- `%clans_tag%`: Tag colorida do clan
- `%clans_elo%`: ELO médio do clan (cache 30s, query sync se necessário - PlaceholderAPI não suporta async)
- `%clans_kills%`: Kills totais do clan (query sync se necessário)
- `%clans_members_online%`: Membros online (contagem em tempo real)
- `%clans_balance%`: Saldo do clan bank (se Economy habilitado)
- Validação: retorna "" se player não tem clan ou PlaceholderAPI não suporta async
- Verificar se PlaceholderAPI está habilitado antes de registrar: `getServer().getPluginManager().getPlugin("PlaceholderAPI") != null`

## Fase 3: Features Avançadas (Semana 5-7)

### 3.1 Clan Chat

**Listener `ClanChatListener.java`:**

- Escuta `AsyncPlayerChatEvent` com prioridade LOWEST
- Se player tem clan e mensagem começa com `!` ou configurado, envia apenas para membros do clan
- Formato: `§7[§e{tag}§7] §b{player}: §7{message}`
- Cancelar evento original e enviar apenas para membros online

### 3.2 Gestão de Membros

**Comandos:**

- `/clan expulsar <player>`: Apenas Leader/Officer
- `/clan promover <player>`: Apenas Leader (promove para Officer)
- `/clan rebaixar <player>`: Apenas Leader (rebaixa Officer para Member)
- `/clan transferir <player>`: Apenas Leader (transfere liderança)

### 3.3 Clan Home (Opcional)

**Tabela:**

```sql
ALTER TABLE clans ADD COLUMN IF NOT EXISTS home_world VARCHAR(50);
ALTER TABLE clans ADD COLUMN IF NOT EXISTS home_x DOUBLE PRECISION;
ALTER TABLE clans ADD COLUMN IF NOT EXISTS home_y DOUBLE PRECISION;
ALTER TABLE clans ADD COLUMN IF NOT EXISTS home_z DOUBLE PRECISION;
```

**Comandos:**

- `/clan home definir`: Leader define home
- `/clan home`: Teleporta para home (cooldown configurável)

## Fase 4: Integração Discord (Semana 8-9)

### 4.1 Criação Automática de Canais

**Método em `ClansPlugin.java`:**

- Verificar se `PrimeleagueDiscord` está habilitado: `getServer().getPluginManager().getPlugin("PrimeleagueDiscord") != null`
- Obter JDA via `DiscordPlugin.getInstance().getDiscordBot().getJDA()`
- Obter guild via `jda.getGuildById(guildId)` do config do DiscordPlugin (ler `discord.guild-id` do config.yml)
- Ao criar clan, criar canal de texto e role no Discord
- Usar JDA 4.4.0: `guild.createTextChannel("clan-" + clanName).queue()` e `guild.createRole().setName("Clan " + clanName).queue()`
- Salvar `discord_channel_id` e `discord_role_id` no banco após criação bem-sucedida (via callback do queue)
- Async via `queue()` para não bloquear thread principal

### 4.2 Notificações Discord

**Método `notifyDiscord()`:**

- Enviar embeds quando: player entra/sai, clan criado, ranking atualizado
- Usar `TextChannel.sendMessageEmbeds()` com `EmbedBuilder`
- Rate limiting: ConcurrentHashMap com TTL 60s
- Mensagens em PT-BR

### 4.3 Slash Commands Discord

**Estender `ApprovalHandler` do PrimeleagueDiscord:**

- Adicionar handler para `/clan info <player>`
- Buscar dados via CoreAPI
- Retornar embed formatado

## Fase 5: Features "Tchan" (Semana 10-12)

### 5.1 Clan ELO Dinâmico

- Calcular ELO médio do clan em tempo real
- Atualizar quando membros fazem PvP
- Cache TTL 30s
- Mostrar em `/clan info` e rankings

### 5.2 Clan Seasons (Opcional)

- Sistema de temporadas com reset de rankings
- Tabela `clan_seasons` para histórico
- Comando `/clan season` para ver temporada atual

## Padrões e Boas Práticas

### Thread Safety

- Todas queries via HikariCP (thread-safe por padrão)
- Try-with-resources sempre
- Cache usando ConcurrentHashMap
- Async para queries pesadas (rankings)

### Mensagens PT-BR

- Todas mensagens em português brasileiro
- Usar códigos de cor § para formatação
- Padrão: `§a` sucesso, `§c` erro, `§e` destaque, `§7` info

### Validações

- Nome: 1-50 chars, pode ter espaços, trim antes de validar
- Tag: 3 chars (sem contar cores), tag completa máximo 20 chars (com cores), pode ter cores
- Tag unique: case-insensitive via `UPPER(tag_clean)` no banco
- Verificar se player já está em clan antes de criar/aceitar
- Verificar permissões (Leader/Officer) antes de ações
- Verificar se plugins opcionais estão habilitados antes de usar (Economy, Discord, PlaceholderAPI)

### Integrações

- Todas integrações são softdepend (graceful fallback)
- Verificar se plugin está habilitado antes de usar
- Seguir padrão dos outros plugins (Economy, ELO, Stats)

### Compatibilidade Paper 1.8.8

- Usar apenas APIs básicas do Bukkit
- Sem ItemStack builder moderno
- GUIs usando Inventory API básica
- Sem componentes de chat modernos (usar String com códigos §)

### To-dos

- [ ] Criar estrutura do projeto (pom.xml, plugin.yml, estrutura de pacotes)
- [ ] Criar tabelas PostgreSQL (clans, clan_members, clan_invites) no onEnable()
- [ ] Criar modelos de dados (ClanData, ClanMember) seguindo padrão PlayerData
- [ ] Implementar ClansManager com lógica de negócio (create, get, invite, accept)
- [ ] Implementar validações (nome 1-50 chars com espaços, tag 3 chars com cores)
- [ ] Implementar comandos básicos PT-BR (/clan criar, sair, info, membros, convidar, aceitar)
- [ ] Implementar ClanStatsListener para atualizar stats agregadas em PvP
- [ ] Implementar comando /clan top [elo|kills] com cache similar a EloCommand
- [ ] Implementar integração Economy (clan bank, depositar, sacar)
- [ ] Implementar PlaceholderAPI expansion com placeholders de clan
- [ ] Implementar clan chat (listener AsyncPlayerChatEvent com prefixo)
- [ ] Implementar comandos de gestão (/clan expulsar, promover, rebaixar, transferir)
- [ ] Implementar integração Discord (criação de canais, notificações, slash commands)
- [ ] Implementar ELO dinâmico do clan (cálculo em tempo real, cache 30s)