package com.vincent.tools.region.domain;

public final class RegionFieldLimits {
    public static final int MAX_CODE_LENGTH = 12;
    public static final int MAX_NAME_LENGTH = 64;

    private RegionFieldLimits() {
    }

    public static String requireNonBlank(String value, String field, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new RegionException(RegionErrorCode.INVALID_ARGUMENT, field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new RegionException(RegionErrorCode.INVALID_ARGUMENT, field + " is too long");
        }
        return trimmed;
    }
}
