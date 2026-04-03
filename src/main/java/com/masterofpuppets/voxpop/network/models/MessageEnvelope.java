package com.masterofpuppets.voxpop.network.models;

import com.google.gson.JsonElement;

public record MessageEnvelope(String eventType, JsonElement payload) {
}