package com.masterofpuppets.voxpop.network.models;

public record ServerHandshakeAck(boolean success, String message, String assignedSessionId) {
}