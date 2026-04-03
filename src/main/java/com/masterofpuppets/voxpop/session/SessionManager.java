package com.masterofpuppets.voxpop.session;

import com.masterofpuppets.voxpop.network.models.QueueUpdate;
import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SessionManager {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    private final ConcurrentHashMap<WebSocket, Participant> participants = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Participant> speakQueue = new ConcurrentLinkedQueue<>();
    private Participant currentSpeaker = null;

    public String registerParticipant(String deviceId, String name, WebSocket connection) {
        String sessionId = UUID.randomUUID().toString();
        Participant participant = new Participant(sessionId, deviceId, name, connection);

        participants.put(connection, participant);
        logger.info("Participant registered: {} (Session ID: {})", name, sessionId);

        return sessionId;
    }

    public void unregisterParticipant(WebSocket connection) {
        Participant participant = participants.remove(connection);
        if (participant != null) {
            speakQueue.remove(participant);
            if (currentSpeaker != null && currentSpeaker.equals(participant)) {
                currentSpeaker = null;
                logger.info("Current speaker disconnected: {}", participant.name());
            }
            logger.info("Participant unregistered: {}", participant.name());
        }
    }

    public Participant getParticipant(WebSocket connection) {
        return participants.get(connection);
    }

    public boolean requestToSpeak(WebSocket connection, String sessionId) {
        Participant participant = participants.get(connection);

        if (participant != null && participant.sessionId().equals(sessionId)) {
            if (!speakQueue.contains(participant) && !participant.equals(currentSpeaker)) {
                speakQueue.add(participant);
                logger.info("Participant added to speak queue: {}", participant.name());
                return true;
            }
        } else {
            logger.warn("Invalid SpeakRequest: connection not found or sessionId mismatch.");
        }
        return false;
    }

    public List<QueueUpdate.QueueItem> getQueueState() {
        return speakQueue.stream()
                .map(p -> new QueueUpdate.QueueItem(p.sessionId(), p.name()))
                .toList();
    }

    public boolean grantSpeech(String sessionId) {
        Participant target = participants.values().stream()
                .filter(p -> p.sessionId().equals(sessionId))
                .findFirst()
                .orElse(null);

        if (target != null) {
            currentSpeaker = target;
            speakQueue.remove(target); // Remove from queue if they were there
            logger.info("Speech granted to: {}", target.name());
            return true;
        }
        return false;
    }

    public boolean revokeSpeech(String sessionId) {
        if (currentSpeaker != null && currentSpeaker.sessionId().equals(sessionId)) {
            logger.info("Speech revoked from: {}", currentSpeaker.name());
            currentSpeaker = null;
            return true;
        }
        return false;
    }

    public void clearQueue() {
        speakQueue.clear();
        logger.info("Speak queue cleared");
    }

    public Participant getCurrentSpeaker() {
        return currentSpeaker;
    }

    public WebSocket getConnectionBySessionId(String sessionId) {
        Participant target = participants.values().stream()
                .filter(p -> p.sessionId().equals(sessionId))
                .findFirst()
                .orElse(null);
        return target != null ? target.connection() : null;
    }
}