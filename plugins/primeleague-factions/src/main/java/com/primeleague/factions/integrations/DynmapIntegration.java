package com.primeleague.factions.integrations;

import com.primeleague.clans.models.ClanData;
import com.primeleague.factions.PrimeFactions;
import com.primeleague.factions.util.ChunkKey;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integração Dynmap para mostrar territórios de clãs no mapa
 * Grug Brain: Simples, direto, soft dependency (funciona sem Dynmap)
 */
public class DynmapIntegration {

    private final PrimeFactions plugin;
    private Object dynmapAPI; // Usar Object para reflection (soft dependency)
    private Object markerAPI;
    private Object markerSet;
    private final Map<ChunkKey, Object> claimMarkers; // Cache de markers (AreaMarker via reflection)
    private final Map<Integer, String> clanColors; // Cache de cores por clã

    public DynmapIntegration(PrimeFactions plugin) {
        this.plugin = plugin;
        this.claimMarkers = new ConcurrentHashMap<>(); // Thread-safe (async updates)
        this.clanColors = new ConcurrentHashMap<>(); // Thread-safe (async updates)
    }

    /**
     * Inicializa integração com Dynmap
     * Grug Brain: Usa reflection para soft dependency (funciona sem Dynmap no classpath)
     */
    public void setup() {
        org.bukkit.plugin.Plugin dynmapPlugin = plugin.getServer().getPluginManager().getPlugin("dynmap");
        if (dynmapPlugin == null || !dynmapPlugin.isEnabled()) {
            plugin.getLogger().info("Dynmap não encontrado - integração de mapa desabilitada");
            return;
        }

        try {
            // Usar reflection para acessar DynmapAPI (soft dependency)
            dynmapAPI = dynmapPlugin;

            // Obter MarkerAPI via reflection
            java.lang.reflect.Method getMarkerAPIMethod = dynmapAPI.getClass().getMethod("getMarkerAPI");
            try {
                markerAPI = getMarkerAPIMethod.invoke(dynmapAPI);
            } catch (Exception e) {
                // Se falhar, tentar com setAccessible (problema de classloader)
                getMarkerAPIMethod.setAccessible(true);
                markerAPI = getMarkerAPIMethod.invoke(dynmapAPI);
            }

            if (markerAPI == null) {
                plugin.getLogger().warning("Dynmap MarkerAPI não disponível");
                return;
            }

            // Criar MarkerSet para claims via reflection
            java.lang.reflect.Method getMarkerSetMethod = markerAPI.getClass().getMethod("getMarkerSet", String.class);
            try {
                markerSet = getMarkerSetMethod.invoke(markerAPI, "primeleague.claims");
            } catch (Exception e) {
                getMarkerSetMethod.setAccessible(true);
                markerSet = getMarkerSetMethod.invoke(markerAPI, "primeleague.claims");
            }

            if (markerSet == null) {
                // Criar novo MarkerSet
                java.lang.reflect.Method createMarkerSetMethod = markerAPI.getClass().getMethod(
                    "createMarkerSet", String.class, String.class, java.util.Set.class, boolean.class);
                try {
                    markerSet = createMarkerSetMethod.invoke(markerAPI, "primeleague.claims", "Territórios", null, false);
                } catch (Exception e) {
                    createMarkerSetMethod.setAccessible(true);
                    markerSet = createMarkerSetMethod.invoke(markerAPI, "primeleague.claims", "Territórios", null, false);
                }
            } else {
                // Atualizar label
                java.lang.reflect.Method setLabelMethod = markerSet.getClass().getMethod("setMarkerSetLabel", String.class);
                try {
                    setLabelMethod.invoke(markerSet, "Territórios");
                } catch (Exception e) {
                    setLabelMethod.setAccessible(true);
                    setLabelMethod.invoke(markerSet, "Territórios");
                }
            }

            if (markerSet == null) {
                plugin.getLogger().severe("Falha ao criar MarkerSet do Dynmap");
                return;
            }

            plugin.getLogger().info("Dynmap integration habilitada - Mapa interativo ativo!");

            // Carregar claims existentes (será chamado após loadClaims() no ClaimManager)
            // Não carregar aqui para evitar race condition

        } catch (Exception e) {
            plugin.getLogger().warning("Erro ao configurar Dynmap: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Carrega todos os claims existentes no mapa
     * Grug Brain: Chama após setup, atualiza mapa completo
     * Nota: Chamado pelo ClaimManager após loadClaims() completar
     */
    public void loadAllClaims() {
        if (markerSet == null) return;
        // Este método não é mais necessário - carregamento é feito no ClaimManager
    }

    /**
     * Adiciona/atualiza claim no mapa
     * Grug Brain: Cria ou atualiza AreaMarker para o chunk (via reflection)
     */
    public void updateClaim(ChunkKey chunk, int clanId) {
        if (markerSet == null) return;

        // Capturar dados necessários antes de ir para async
        final String worldName = chunk.getWorld();
        final int chunkX = chunk.getX();
        final int chunkZ = chunk.getZ();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Remover marker antigo se existir (já é thread-safe com ConcurrentHashMap)
                removeClaim(chunk);

                // Buscar dados do clã (query async - OK)
                ClanData clan = plugin.getClansPlugin().getClansManager().getClan(clanId);
                if (clan == null) return;

                // Obter cor do clã (cache - thread-safe)
                String color = getClanColor(clanId);
                int colorInt = Integer.parseInt(color, 16);

                // Obter mundo (precisa voltar para main thread - Bukkit API)
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    try {
                        World world = Bukkit.getWorld(worldName);
                        if (world == null) return;

                        // Calcular coordenadas do chunk (16x16 blocos)
                        double x = chunkX * 16.0;
                        double z = chunkZ * 16.0;
                        double[] xCoords = {x, x + 16.0, x + 16.0, x};
                        double[] zCoords = {z, z, z + 16.0, z + 16.0};

                        // Criar AreaMarker via reflection
                        String markerId = "claim_" + worldName + "_" + chunkX + "_" + chunkZ;
                        java.lang.reflect.Method createAreaMarkerMethod = markerSet.getClass().getMethod(
                            "createAreaMarker",
                            String.class, String.class, boolean.class, String.class,
                            double[].class, double[].class, boolean.class
                        );
                        Object marker;
                        try {
                            marker = createAreaMarkerMethod.invoke(
                                markerSet, markerId, clan.getName(), false, world.getName(), xCoords, zCoords, false);
                        } catch (Exception e) {
                            // Se falhar, tentar com setAccessible (problema de classloader)
                            createAreaMarkerMethod.setAccessible(true);
                            marker = createAreaMarkerMethod.invoke(
                                markerSet, markerId, clan.getName(), false, world.getName(), xCoords, zCoords, false);
                        }

                        if (marker != null) {
                            // Configurar cor e opacidade via reflection
                            java.lang.reflect.Method setLineStyleMethod = marker.getClass().getMethod(
                                "setLineStyle", int.class, double.class, int.class);
                            try {
                                setLineStyleMethod.invoke(marker, 2, 1.0, colorInt); // Borda
                            } catch (Exception e) {
                                setLineStyleMethod.setAccessible(true);
                                setLineStyleMethod.invoke(marker, 2, 1.0, colorInt);
                            }

                            java.lang.reflect.Method setFillStyleMethod = marker.getClass().getMethod(
                                "setFillStyle", double.class, int.class);
                            try {
                                setFillStyleMethod.invoke(marker, 0.3, colorInt); // Preenchimento
                            } catch (Exception e) {
                                setFillStyleMethod.setAccessible(true);
                                setFillStyleMethod.invoke(marker, 0.3, colorInt);
                            }

                            java.lang.reflect.Method setDescriptionMethod = marker.getClass().getMethod(
                                "setDescription", String.class);
                            try {
                                setDescriptionMethod.invoke(marker, "§6Clã: §e" + clan.getName() + "§7 (" + clan.getTag() + ")");
                            } catch (Exception e) {
                                setDescriptionMethod.setAccessible(true);
                                setDescriptionMethod.invoke(marker, "§6Clã: §e" + clan.getName() + "§7 (" + clan.getTag() + ")");
                            }

                            // Cache do marker (thread-safe)
                            claimMarkers.put(chunk, marker);
                        }

                    } catch (Exception e) {
                        plugin.getLogger().warning("Erro ao atualizar claim no Dynmap: " + e.getMessage());
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().warning("Erro ao processar claim para Dynmap: " + e.getMessage());
            }
        });
    }

    /**
     * Remove claim do mapa
     * Grug Brain: Remove AreaMarker quando chunk é unclaimado (via reflection)
     * Thread-safe: Usa main thread para Dynmap API
     */
    public void removeClaim(ChunkKey chunk) {
        if (markerSet == null) return;

        Object marker = claimMarkers.remove(chunk);
        if (marker != null) {
            // Dynmap API precisa de main thread - usar scheduler
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    java.lang.reflect.Method deleteMarkerMethod = marker.getClass().getMethod("deleteMarker");
                    try {
                        deleteMarkerMethod.invoke(marker);
                    } catch (Exception e) {
                        deleteMarkerMethod.setAccessible(true);
                        deleteMarkerMethod.invoke(marker);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Erro ao remover marker do Dynmap: " + e.getMessage());
                }
            });
        }
    }

    /**
     * Remove todos os claims de um clã
     * Grug Brain: Limpa markers quando clã é deletado (via reflection)
     * Thread-safe: Usa main thread para Dynmap API
     */
    public void removeClanClaims(int clanId) {
        if (markerSet == null) return;

        // Coletar markers a remover (thread-safe)
        final java.util.List<Object> markersToDelete = new java.util.ArrayList<>();
        claimMarkers.entrySet().removeIf(entry -> {
            // Verificar se chunk pertence ao clã
            int chunkClanId = plugin.getClaimManager().getClanAt(
                entry.getKey().getWorld(),
                entry.getKey().getX(),
                entry.getKey().getZ()
            );
            if (chunkClanId == clanId) {
                markersToDelete.add(entry.getValue());
                return true;
            }
            return false;
        });

        // Deletar markers na main thread (Dynmap API requirement)
        if (!markersToDelete.isEmpty()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                for (Object marker : markersToDelete) {
                    try {
                        java.lang.reflect.Method deleteMarkerMethod = marker.getClass().getMethod("deleteMarker");
                        try {
                            deleteMarkerMethod.invoke(marker);
                        } catch (Exception e) {
                            deleteMarkerMethod.setAccessible(true);
                            deleteMarkerMethod.invoke(marker);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Erro ao remover marker do clã: " + e.getMessage());
                    }
                }
            });
        }
    }

    /**
     * Obtém cor única para cada clã
     * Grug Brain: Hash do clanId gera cor consistente
     */
    private String getClanColor(int clanId) {
        // Verificar cache
        if (clanColors.containsKey(clanId)) {
            return clanColors.get(clanId);
        }

        // Gerar cor baseada em hash do clanId
        // Cores vibrantes (evitar preto/branco)
        int hash = Math.abs(clanId * 31);
        int r = (hash % 200) + 55; // 55-255 (evita muito escuro)
        int g = ((hash / 200) % 200) + 55;
        int b = ((hash / 40000) % 200) + 55;

        // Converter para hex (RRGGBB)
        String color = String.format("%02X%02X%02X", r, g, b);
        clanColors.put(clanId, color);
        return color;
    }

    /**
     * Verifica se Dynmap está habilitado
     */
    public boolean isEnabled() {
        return markerSet != null && markerAPI != null;
    }

    /**
     * Limpa todos os markers (cleanup)
     * Grug Brain: Via reflection para soft dependency
     */
    public void cleanup() {
        if (markerSet != null) {
            try {
                java.lang.reflect.Method deleteMarkerSetMethod = markerSet.getClass().getMethod("deleteMarkerSet");
                try {
                    deleteMarkerSetMethod.invoke(markerSet);
                } catch (Exception e) {
                    deleteMarkerSetMethod.setAccessible(true);
                    deleteMarkerSetMethod.invoke(markerSet);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Erro ao limpar MarkerSet: " + e.getMessage());
            }
            markerSet = null;
        }
        claimMarkers.clear();
        clanColors.clear();
    }
}

