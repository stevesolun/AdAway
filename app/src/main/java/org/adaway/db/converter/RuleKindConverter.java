package org.adaway.db.converter;

import androidx.room.TypeConverter;

import org.adaway.db.entity.RuleKind;

/**
 * Room converter for {@link RuleKind}.
 */
public final class RuleKindConverter {
    private RuleKindConverter() {
        // Prevent instantiation.
    }

    @TypeConverter
    public static RuleKind fromValue(Integer value) {
        return value == null ? null : RuleKind.fromValue(value);
    }

    @TypeConverter
    public static Integer kindToValue(RuleKind kind) {
        return kind == null ? null : kind.getValue();
    }
}
