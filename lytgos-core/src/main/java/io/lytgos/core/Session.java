package io.lytgos.core;

/**
 * Conversation context container.
 */
public record Session(String sessionId, String profileName, String channel, String userId) {
}
