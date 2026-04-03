package com.masterofpuppets.voxpop.network.models;

import java.util.List;

public record QueueUpdate(List<QueueItem> queue) {
    public record QueueItem(String sessionId, String name) {}
}