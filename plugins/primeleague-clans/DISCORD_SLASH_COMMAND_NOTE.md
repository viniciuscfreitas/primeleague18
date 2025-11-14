# Nota: Slash Command Discord `/clan info`

## Status: ⚠️ Implementação Pendente no DiscordPlugin

O Slash Command `/clan info <player>` deve ser implementado no **ApprovalHandler** do plugin **PrimeleagueDiscord**, não no ClansPlugin.

### Motivo

- O `ApprovalHandler` é onde todos os Slash Commands são tratados
- O ClansPlugin não deve modificar código do DiscordPlugin
- Segue o padrão de separação de responsabilidades

### Como Implementar

**Localização:** `plugins/primeleague-discord/src/main/java/com/primeleague/discord/handlers/ApprovalHandler.java`

**Adicionar no método `onSlashCommand()`:**

```java
if (event.getName().equals("clan")) {
    event.deferReply().queue();

    // Obter subcomando (info)
    String subcommand = event.getSubcommandName();
    if (subcommand == null || !subcommand.equals("info")) {
        event.getHook().sendMessage("❌ Uso: `/clan info <player>`").queue();
        return;
    }

    // Obter player
    String playerName = event.getOption("player") != null ?
        event.getOption("player").getAsString() : null;
    if (playerName == null) {
        event.getHook().sendMessage("❌ Especifique um player").queue();
        return;
    }

    // Buscar dados do clan (async)
    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
        try {
            // Buscar player
            com.primeleague.core.models.PlayerData playerData =
                com.primeleague.core.CoreAPI.getPlayerByName(playerName);
            if (playerData == null) {
                event.getHook().sendMessage("❌ Player não encontrado: " + playerName).queue();
                return;
            }

            // Buscar clan
            com.primeleague.clans.ClansPlugin clansPlugin =
                (com.primeleague.clans.ClansPlugin) plugin.getServer()
                    .getPluginManager().getPlugin("PrimeleagueClans");
            if (clansPlugin == null) {
                event.getHook().sendMessage("❌ Plugin de Clans não encontrado").queue();
                return;
            }

            com.primeleague.clans.models.ClanData clan =
                clansPlugin.getClansManager().getClanByMember(playerData.getUuid());

            if (clan == null) {
                event.getHook().sendMessage("❌ " + playerName + " não está em um clan").queue();
                return;
            }

            // Criar embed
            net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder();
            embed.setTitle("🏰 Clan: " + clan.getName());
            embed.setDescription("Tag: " + clan.getTag());
            embed.setColor(0x00FF00);

            // Adicionar informações
            embed.addField("Membros", String.valueOf(clansPlugin.getClansManager().getMembers(clan.getId()).size()), true);
            // ... mais campos

            event.getHook().sendMessageEmbeds(embed.build()).queue();
        } catch (Exception e) {
            plugin.getLogger().severe("Erro ao processar /clan info: " + e.getMessage());
            event.getHook().sendMessage("❌ Erro ao buscar informações do clan").queue();
        }
    });
}
```

**Registrar comando no `DiscordBot.initialize()`:**

```java
commands.addCommands(
    new CommandData("clan", "Informações sobre clans")
        .addSubcommands(
            new SubcommandData("info", "Mostra informações do clan de um player")
                .addOptions(
                    new OptionData(OptionType.STRING, "player", "Nome do player", true)
                )
        )
);
```

### Status Atual

- ✅ Integração Discord criada (criação de canais, notificações)
- ⚠️ Slash Command pendente (deve ser implementado no DiscordPlugin)

