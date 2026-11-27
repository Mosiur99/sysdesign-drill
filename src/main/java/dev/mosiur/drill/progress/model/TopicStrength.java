package dev.mosiur.drill.progress.model;

public record TopicStrength(String name, int drills, Integer average) {

    public boolean tried() {
        return drills > 0;
    }
}
