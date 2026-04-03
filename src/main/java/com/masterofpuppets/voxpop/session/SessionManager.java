package com.masterofpuppets.voxpop.session;

import com.masterofpuppets.voxpop.network.models.QueueUpdate;
import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SessionManager {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    private final ConcurrentHashMap<WebSocket, Participant> participants = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Participant> speakQueue = new ConcurrentLinkedQueue<>();

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
            logger.info("Participant unregistered: {}", participant.getName());
        }
    }

    public Participant getParticipant(WebSocket connection) {
        return participants.get(connection);
    }

    public Participant getParticipantBySessionId(String sessionId) {
        return participants.values().stream()
                .filter(p -> p.getSessionId().equals(sessionId))
                .findFirst()
                .orElse(null);
    }

    public List<Participant> getAllParticipants() {
        return new ArrayList<>(participants.values());
    }

    public boolean requestToSpeak(WebSocket connection, String sessionId) {
        Participant participant = participants.get(connection);

        if (participant != null && participant.getSessionId().equals(sessionId)) {
            if (!speakQueue.contains(participant)) {
                speakQueue.add(participant);
                logger.info("Participant added to speak queue: {}", participant.getName());
                return true;
            }
        } else {
            logger.warn("Invalid SpeakRequest: connection not found or sessionId mismatch.");
        }
        return false;
    }

    public List<QueueUpdate.QueueItem> getQueueState() {
        return speakQueue.stream()
                .map(p -> new QueueUpdate.QueueItem(p.getSessionId(), p.getName()))
                .toList();
    }

    public boolean grantSpeech(String sessionId) {
        Participant target = getParticipantBySessionId(sessionId);
        if (target != null) {
            target.setSpeaking(true);
            target.setMuted(false); // Open mic when granted speech
            logger.info("Speech granted to: {}", target.getName());
            return true;
        }
        return false;
    }

    public boolean revokeSpeech(String sessionId) {
        Participant target = getParticipantBySessionId(sessionId);
        if (target != null) {
            target.setSpeaking(false);
            speakQueue.remove(target);
            logger.info("Speech revoked from: {}", target.getName());
            return true;
        }
        return false;
    }

    public void closeTopic() {
        for (Participant p : speakQueue) {
            p.setSpeaking(false);
        }
        speakQueue.clear();
        logger.info("Topic closed. Queue cleared and all speeches revoked.");
    }

    public boolean toggleMute(String sessionId) {
        Participant target = getParticipantBySessionId(sessionId);
        if (target != null) {
            boolean newMuteState = !target.isMuted();
            target.setMuted(newMuteState);

            // Removed mutual exclusivity. Muting no longer revokes the active speaker status.

            logger.info("Participant {} mute state toggled to: {}", target.getName(), target.isMuted());
            return target.isMuted();
        }
        return false;
    }

    public WebSocket getConnectionBySessionId(String sessionId) {
        Participant target = getParticipantBySessionId(sessionId);
        return target != null ? target.getConnection() : null;
    }

    public List<Participant> getActiveSpeakers() {
        return speakQueue.stream().filter(Participant::isSpeaking).toList();
    }
}