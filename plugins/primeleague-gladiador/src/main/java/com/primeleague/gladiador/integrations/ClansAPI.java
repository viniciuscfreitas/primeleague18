package com.primeleague.gladiador.integrations;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * API wrapper para ClansPlugin
 * Grug Brain: Reflection para softdepend, similar ao CoreAPI pattern
 * Reduz acoplamento direto com ClansPlugin
 */
public class ClansAPI {

    private static Boolean enabled = null;
    private static Object clansPluginInstance = null;
    private static Object clansManagerInstance = null;

    /**
     * Verifica se ClansPlugin está habilitado
     * Grug Brain: Cache do status para evitar reflection repetida
     */
    public static boolean isEnabled() {
        if (enabled != null) {
            return enabled;
        }

        Plugin plugin = Bukkit.getPluginManager().getPlugin("PrimeleagueClans");
        if (plugin == null || !plugin.isEnabled()) {
            enabled = false;
            return false;
        }

        try {
            clansPluginInstance = plugin;
            enabled = true;
            return true;
        } catch (Exception e) {
            enabled = false;
            return false;
        }
    }

    /**
     * Obtém ClansManager via reflection
     * Grug Brain: Cache da instância para performance
     */
    private static Object getClansManager() {
        if (!isEnabled()) {
            return null;
        }

        if (clansManagerInstance != null) {
            return clansManagerInstance;
        }

        try {
            java.lang.reflect.Method getClansManagerMethod = clansPluginInstance.getClass().getMethod("getClansManager");
            clansManagerInstance = getClansManagerMethod.invoke(clansPluginInstance);
            return clansManagerInstance;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Obtém ClanData por ID
     * Grug Brain: Wrapper para getClan(int)
     */
    public static Object getClan(int clanId) {
        Object manager = getClansManager();
        if (manager == null) {
            return null;
        }

        try {
            java.lang.reflect.Method method = manager.getClass().getMethod("getClan", int.class);
            return method.invoke(manager, clanId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Obtém ClanData por membro UUID
     * Grug Brain: Wrapper para getClanByMember(UUID)
     */
    public static Object getClanByMember(UUID playerUuid) {
        Object manager = getClansManager();
        if (manager == null) {
            return null;
        }

        try {
            java.lang.reflect.Method method = manager.getClass().getMethod("getClanByMember", UUID.class);
            return method.invoke(manager, playerUuid);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Adiciona vitória de evento ao clan
     * Grug Brain: Wrapper para addEventWin(int, String, int, UUID)
     */
    public static boolean addEventWin(int clanId, String eventName, int points, UUID awardedBy) {
        Object manager = getClansManager();
        if (manager == null) {
            return false;
        }

        try {
            java.lang.reflect.Method method = manager.getClass().getMethod("addEventWin",
                int.class, String.class, int.class, UUID.class);
            Object result = method.invoke(manager, clanId, eventName, points, awardedBy);
            return result != null && (Boolean) result;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Adiciona balance ao clan
     * Grug Brain: Wrapper para addClanBalance(int, long)
     */
    public static boolean addClanBalance(int clanId, long cents) {
        Object manager = getClansManager();
        if (manager == null) {
            return false;
        }

        try {
            java.lang.reflect.Method method = manager.getClass().getMethod("addClanBalance", int.class, long.class);
            Object result = method.invoke(manager, clanId, cents);
            return result != null && (Boolean) result;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Obtém ID do clan de um ClanData
     * Grug Brain: Helper para extrair ID via reflection
     */
    public static int getClanId(Object clanData) {
        if (clanData == null) {
            return -1;
        }

        try {
            java.lang.reflect.Method method = clanData.getClass().getMethod("getId");
            return (Integer) method.invoke(clanData);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Obtém nome do clan de um ClanData
     * Grug Brain: Helper para extrair nome via reflection
     */
    public static String getClanName(Object clanData) {
        if (clanData == null) {
            return null;
        }

        try {
            java.lang.reflect.Method method = clanData.getClass().getMethod("getName");
            return (String) method.invoke(clanData);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Obtém tag do clan de um ClanData
     * Grug Brain: Helper para extrair tag via reflection
     */
    public static String getClanTag(Object clanData) {
        if (clanData == null) {
            return null;
        }

        try {
            java.lang.reflect.Method method = clanData.getClass().getMethod("getTag");
            return (String) method.invoke(clanData);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Invalida cache (útil quando plugin é recarregado)
     * Grug Brain: Reset de cache para recarregamento
     */
    public static void invalidateCache() {
        enabled = null;
        clansPluginInstance = null;
        clansManagerInstance = null;
    }
}


