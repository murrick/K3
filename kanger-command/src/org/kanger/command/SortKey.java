/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.command;

import java.util.Objects;

/** One ordered Values projection key. */
public final class SortKey {

    public enum Direction {
        ASC,
        DESC
    }

    private final String field;
    private final Direction direction;

    public SortKey(String field, Direction direction) {
        if (field == null || field.isEmpty()) {
            throw new IllegalArgumentException("field must not be empty");
        }
        if (direction == null) {
            throw new IllegalArgumentException("direction must not be null");
        }
        this.field = field;
        this.direction = direction;
    }

    public String getField() {
        return field;
    }

    public Direction getDirection() {
        return direction;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SortKey)) {
            return false;
        }
        SortKey key = (SortKey) other;
        return field.equals(key.field) && direction == key.direction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, direction);
    }

    @Override
    public String toString() {
        return field + " " + direction.name().toLowerCase();
    }
}
