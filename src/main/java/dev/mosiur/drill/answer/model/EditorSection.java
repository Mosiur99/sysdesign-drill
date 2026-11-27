package dev.mosiur.drill.answer.model;

public record EditorSection(int number, String key, String title, String hint, int rows, boolean code,
                            boolean diagram, String text, int words) {
}
