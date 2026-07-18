package xac.cloud.session;

import win.ac.x.ml.logic.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSessionManager {

    private final Map<String, Map<String, PlayerSession>> sessions; // serverId -> playerId -> session
    private final int rawBufferSize;
    private final int rnnBufferSize;

    public PlayerSessionManager(int rawBufferSize, int rnnBufferSize) {
        this.sessions = new ConcurrentHashMap<>();
        this.rawBufferSize = rawBufferSize;
        this.rnnBufferSize = rnnBufferSize;
    }

    public PlayerSession getOrCreate(String serverId, String playerId) {
        Map<String, PlayerSession> serverSessions = sessions.computeIfAbsent(serverId, k -> new ConcurrentHashMap<>());
        return serverSessions.computeIfAbsent(playerId, k -> {
            Logger.info("New session: " + playerId + " @ " + serverId);
            return new PlayerSession(playerId, serverId, rawBufferSize, rnnBufferSize);
        });
    }

    public PlayerSession get(String serverId, String playerId) {
        Map<String, PlayerSession> serverSessions = sessions.get(serverId);
        if (serverSessions == null) return null;
        return serverSessions.get(playerId);
    }

    public void removePlayer(String serverId, String playerId) {
        Map<String, PlayerSession> serverSessions = sessions.get(serverId);
        if (serverSessions != null) {
            PlayerSession removed = serverSessions.remove(playerId);
            if (removed != null) {
                Logger.info("Removed session: " + playerId + " @ " + serverId);
            }
            if (serverSessions.isEmpty()) {
                sessions.remove(serverId);
            }
        }
    }

    public void removeServer(String serverId) {
        Map<String, PlayerSession> removed = sessions.remove(serverId);
        if (removed != null) {
            Logger.info("Removed all sessions for server: " + serverId + " (" + removed.size() + " players)");
        }
    }

    public int getTotalPlayerCount() {
        int count = 0;
        for (Map<String, PlayerSession> serverSessions : sessions.values()) {
            count += serverSessions.size();
        }
        return count;
    }

    public int getServerCount() {
        return sessions.size();
    }

    public Map<String, Map<String, PlayerSession>> getAllSessions() {
        return sessions;
    }

    public void cleanup() {
        sessions.clear();
    }
}
