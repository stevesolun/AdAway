package org.adaway.db.entity;

/**
 * Describes how a rule's host value should be matched at runtime.
 */
public enum RuleKind {
    EXACT(0),
    SUFFIX(1);

    private final int value;

    RuleKind(int value) {
        this.value = value;
    }

    public static RuleKind fromValue(int value) {
        for (RuleKind kind : RuleKind.values()) {
            if (kind.value == value) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Invalid value for rule kind: " + value);
    }

    public int getValue() {
        return value;
    }
}
