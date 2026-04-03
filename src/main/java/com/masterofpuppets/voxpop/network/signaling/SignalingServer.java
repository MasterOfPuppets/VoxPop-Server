package com.masterofpuppets.voxpop.network.signaling;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.masterofpuppets.voxpop.network.models.EventTypes;
import com.masterofpuppets.voxpop.network.models.ForceMute;
import com.masterofpuppets.voxpop.network.models.MessageEnvelope;
import com.masterofpuppets.voxpop.network.models.ParticipantHandshake;
import com.masterofpuppets.voxpop.network.models.QueueCleared;
import com.masterofpuppets.voxpop.network.models.QueueUpdate;
import com.masterofpuppets.voxpop.network.models.ServerHandshakeAck;
import com.masterofpuppets.voxpop.network.models.SpeakRequest;
import com.masterofpuppets.voxpop.network.models.SpeechGranted;
import com.masterofpuppets.voxpop.network.models.SpeechRevoked;
import com.masterofpuppets.voxpop.network.models.Unmute;
import com.masterofpuppets.voxpop.session.Participant;
import com.masterofpuppets.voxpop.session.SessionManager;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.function.Consumer;

public class SignalingServer extends WebSocketServer {

    private static final Logger logger = LoggerFactory.getLogger(SignalingServer.class);
    private final Gson gson = new Gson();
    private final SessionManager sessionManager;
    private final Consumer<String> onNetworkLocked;

    private Consumer<List<QueueUpdate.QueueItem>> onQueueUpdatedUI;
    private Consumer<List<Participant>> onParticipantsUpdatedUI;

    public SignalingServer(int port, SessionManager sessionManager, Consumer<String> onNetworkLocked) {
        super(new InetSocketAddress(port));
        this.sessionManager = sessionManager;
        this.onNetworkLocked = onNetworkLocked;
    }

    public void setOnQueueUpdatedUI(Consumer<List<QueueUpdate.QueueItem>> onQueueUpdatedUI) {
        this.onQueueUpdatedUI = onQueueUpdatedUI;
    }

    public void setOnParticipantsUpdatedUI(Consumer<List<Participant>> onParticipantsUpdatedUI) {
        this.onParticipantsUpdatedUI = onParticipantsUpdatedUI;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        logger.info("New network connection opened: {}", conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        logger.info("Connection closed: {}. Reason: {}", conn.getRemoteSocketAddress(), reason);
        sessionManager.unregisterParticipant(conn);

        broadcastQueueUpdate();
        updateParticipantsUI();
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        logger.debug("Raw message received: {}", message);

        try {
            MessageEnvelope envelope = gson.fromJson(message, MessageEnvelope.class);
            if (envelope == null || envelope.eventType() == null) {
                logger.warn("Received malformed message without eventType");
                return;
            }

            switch (envelope.eventType()) {
                case EventTypes.PARTICIPANT_HANDSHAKE -> handleParticipantHandshake(conn, envelope.payload());
                case EventTypes.SPEAK_REQUEST -> handleSpeakRequest(conn, envelope.payload());
                default -> logger.warn("Unknown event type received: {}", envelope.eventType());
            }

        } catch (JsonSyntaxException e) {
            logger.error("Failed to parse incoming JSON message", e);
        }
    }

    private void handleParticipantHandshake(WebSocket conn, JsonElement payload) {
        ParticipantHandshake handshake = gson.fromJson(payload, ParticipantHandshake.class);

        if (handshake == null) {
            logger.warn("Received empty or invalid ParticipantHandshake payload");
            return;
        }

        if (onNetworkLocked != null) {
            String localIp = conn.getLocalSocketAddress().getAddress().getHostAddress();
            onNetworkLocked.accept(localIp);
        }

        String sessionId = sessionManager.registerParticipant(handshake.deviceId(), handshake.participantName(), conn);
        ServerHandshakeAck ack = new ServerHandshakeAck(true, "Welcome to VoxPop!", sessionId);

        sendEvent(conn, EventTypes.SERVER_HANDSHAKE_ACK, gson.toJsonTree(ack));
        updateParticipantsUI();
    }

    private void handleSpeakRequest(WebSocket conn, JsonElement payload) {
        SpeakRequest request = gson.fromJson(payload, SpeakRequest.class);

        if (request == null) {
            logger.warn("Received empty or invalid SpeakRequest payload");
            return;
        }

        boolean added = sessionManager.requestToSpeak(conn, request.sessionId());
        if (added) {
            broadcastQueueUpdate();
        }
    }

    private void sendEvent(WebSocket conn, String eventType, JsonElement payload) {
        if (conn != null && conn.isOpen()) {
            MessageEnvelope envelope = new MessageEnvelope(eventType, payload);
            String jsonMessage = gson.toJson(envelope);

            conn.send(jsonMessage);
            logger.debug("Sent event {} to {}", eventType, conn.getRemoteSocketAddress());
        }
    }

    private void broadcastQueueUpdate() {
        List<QueueUpdate.QueueItem> currentState = sessionManager.getQueueState();
        QueueUpdate update = new QueueUpdate(currentState);
        MessageEnvelope envelope = new MessageEnvelope(EventTypes.QUEUE_UPDATE, gson.toJsonTree(update));
        String jsonMessage = gson.toJson(envelope);

        getConnections().forEach(conn -> {
            if (conn.isOpen()) {
                conn.send(jsonMessage);
            }
        });

        logger.debug("Broadcasted QueueUpdate to all clients");

        if (onQueueUpdatedUI != null) {
            onQueueUpdatedUI.accept(currentState);
        }
    }

    private void updateParticipantsUI() {
        if (onParticipantsUpdatedUI != null) {
            onParticipantsUpdatedUI.accept(sessionManager.getAllParticipants());
        }
    }

    public void grantSpeechToParticipant(String sessionId) {
        if (sessionManager.grantSpeech(sessionId)) {
            WebSocket conn = sessionManager.getConnectionBySessionId(sessionId);

            SpeechGranted speechPayload = new SpeechGranted(sessionId);
            sendEvent(conn, EventTypes.SPEECH_GRANTED, gson.toJsonTree(speechPayload));

            Unmute unmutePayload = new Unmute(sessionId);
            sendEvent(conn, EventTypes.UNMUTE, gson.toJsonTree(unmutePayload));

            broadcastQueueUpdate();
            updateParticipantsUI();
        }
    }

    public void revokeSpeechFromParticipant(String sessionId) {
        if (sessionManager.revokeSpeech(sessionId)) {
            WebSocket conn = sessionManager.getConnectionBySessionId(sessionId);

            SpeechRevoked payload = new SpeechRevoked(sessionId);
            sendEvent(conn, EventTypes.SPEECH_REVOKED, gson.toJsonTree(payload));

            broadcastQueueUpdate();
            updateParticipantsUI();
        }
    }

    public void toggleMuteParticipant(String sessionId) {
        boolean isNowMuted = sessionManager.toggleMute(sessionId);
        WebSocket conn = sessionManager.getConnectionBySessionId(sessionId);

        if (conn != null) {
            if (isNowMuted) {
                ForceMute mutePayload = new ForceMute(sessionId);
                sendEvent(conn, EventTypes.FORCE_MUTE, gson.toJsonTree(mutePayload));
                logger.info("ForceMute event sent to session: {}", sessionId);
            } else {
                Unmute unmutePayload = new Unmute(sessionId);
                sendEvent(conn, EventTypes.UNMUTE, gson.toJsonTree(unmutePayload));
                logger.info("Unmute event sent to session: {}", sessionId);
            }
        }

        broadcastQueueUpdate();
        updateParticipantsUI();
    }

    public void closeTopic() {
        sessionManager.closeTopic();

        QueueCleared payload = new QueueCleared();
        MessageEnvelope envelope = new MessageEnvelope(EventTypes.QUEUE_CLEARED, gson.toJsonTree(payload));
        String jsonMessage = gson.toJson(envelope);

        getConnections().forEach(conn -> {
            if (conn.isOpen()) {
                conn.send(jsonMessage);
            }
        });

        logger.info("QueueCleared event broadcasted to all clients");

        broadcastQueueUpdate();
        updateParticipantsUI();
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn != null) {
            logger.error("Error on connection {}: {}", conn.getRemoteSocketAddress(), ex.getMessage(), ex);
        } else {
            logger.error("Server error: {}", ex.getMessage(), ex);
        }
    }

    @Override
    public void onStart() {
        logger.info("Signaling Server started successfully on port: {}", getPort());
    }
}