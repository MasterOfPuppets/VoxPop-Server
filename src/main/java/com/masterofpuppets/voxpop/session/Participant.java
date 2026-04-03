package com.masterofpuppets.voxpop.session;

import org.java_websocket.WebSocket;

public record Participant(
        String sessionId,
        String deviceId,
        String name,
        WebSocket connection
) {
}