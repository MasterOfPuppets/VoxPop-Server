package com.masterofpuppets.voxpop.session;

import org.java_websocket.WebSocket;
import java.util.Objects;

public class Participant {
    private final String sessionId;
    private final String deviceId;
    private final String name;
    private final WebSocket connection;

    private boolean isMuted;
    private boolean isSpeaking;

    public Participant(String sessionId, String deviceId, String name, WebSocket connection) {
        this.sessionId = sessionId;
        this.deviceId = deviceId;
        this.name = name;
        this.connection = connection;
        this.isMuted = false;
        this.isSpeaking = false;
    }

    public String getSessionId() { return sessionId; }
    public String getDeviceId() { return deviceId; }
    public String getName() { return name; }
    public WebSocket getConnection() { return connection; }

    public boolean isMuted() { return isMuted; }
    public void setMuted(boolean muted) { this.isMuted = muted; }

    public boolean isSpeaking() { return isSpeaking; }
    public void setSpeaking(boolean speaking) { this.isSpeaking = speaking; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Participant that = (Participant) o;
        return Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId);
    }
}