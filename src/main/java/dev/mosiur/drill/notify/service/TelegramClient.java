package dev.mosiur.drill.notify.service;

import dev.mosiur.drill.notify.model.SendResult;

public interface TelegramClient {

    SendResult send(String html);
}
