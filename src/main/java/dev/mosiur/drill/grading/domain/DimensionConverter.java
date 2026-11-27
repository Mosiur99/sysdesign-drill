package dev.mosiur.drill.grading.domain;

import dev.mosiur.drill.problem.domain.Dimension;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
class DimensionConverter implements AttributeConverter<Dimension, String> {

    @Override
    public String convertToDatabaseColumn(Dimension dimension) {
        return dimension == null ? null : dimension.code();
    }

    @Override
    public Dimension convertToEntityAttribute(String code) {
        return code == null ? null : Dimension.fromCode(code);
    }
}
