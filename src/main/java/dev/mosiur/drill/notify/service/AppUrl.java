package dev.mosiur.drill.notify.service;

import java.util.Optional;

public interface AppUrl {

    String current();

    Optional<String> tunnel();
}
