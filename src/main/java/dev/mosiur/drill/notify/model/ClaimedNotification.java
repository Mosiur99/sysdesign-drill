package dev.mosiur.drill.notify.model;

public record ClaimedNotification(long id, String payload, int attempts) {
}
