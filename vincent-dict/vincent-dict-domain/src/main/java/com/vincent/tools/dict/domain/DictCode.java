package com.vincent.tools.dict.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public final class DictCode {
    private static final Pattern CANONICAL = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");

    private final String value;

    private DictCode(String value) {
        this.value = value;
    }

    public static DictCode of(String value) {
        if (value == null || !CANONICAL.matcher(value).matches()) {
            throw new DictException(DictErrorCode.INVALID_ARGUMENT, "invalid dictCode");
        }
        return new DictCode(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DictCode)) {
            return false;
        }
        DictCode that = (DictCode) other;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
