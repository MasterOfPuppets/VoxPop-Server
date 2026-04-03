package com.masterofpuppets.voxpop.network.models;

public final class EventTypes {
    // Prevent instantiation of utility class
    private EventTypes() {}

    public static final String PARTICIPANT_HANDSHAKE = "ParticipantHandshake";
    public static final String SERVER_HANDSHAKE_ACK = "ServerHandshakeAck";

    public static final String SPEAK_REQUEST = "SpeakRequest";
    public static final String QUEUE_UPDATE = "QueueUpdate";
    public static final String SPEECH_GRANTED = "SpeechGranted";
    public static final String FORCE_MUTE = "ForceMute";
    public static final String UNMUTE = "Unmute";
    public static final String SPEECH_REVOKED = "SpeechRevoked";
    public static final String QUEUE_CLEARED = "QueueCleared";
}