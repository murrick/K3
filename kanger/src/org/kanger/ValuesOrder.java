/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import java.util.Objects;

/**
 * Immutable ordering key for one invocation-local Values projection.
 *
 * <p>The value object describes presentation order only. It is never stored in
 * {@code ValuesStore} and therefore cannot participate in Values membership or
 * deduplication.</p>
 */
public final class ValuesOrder {

    public enum Direction {
        ASC,
        DESC
    }

    private final String field;
    private final Direction direction;

    private ValuesOrder(String field, Direction direction) {
        if (field == null || field.isEmpty()) {
            throw new IllegalArgumentException("field must not be empty");
        }
        if (direction == null) {
            throw new IllegalArgumentException("direction must not be null");
        }
        this.field = field;
        this.direction = direction;
    }

    public static ValuesOrder asc(String field) {
        return new ValuesOrder(field, Direction.ASC);
    }

    public static ValuesOrder desc(String field) {
        return new ValuesOrder(field, Direction.DESC);
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
        if (!(other instanceof ValuesOrder)) {
            return false;
        }
        ValuesOrder order = (ValuesOrder) other;
        return field.equals(order.field) && direction == order.direction;
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
