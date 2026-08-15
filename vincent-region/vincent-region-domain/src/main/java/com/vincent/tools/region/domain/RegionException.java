package com.vincent.tools.region.domain;

public final class RegionException extends RuntimeException {
    private final RegionErrorCode code;

    public RegionException(RegionErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public RegionException(RegionErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public RegionErrorCode getCode() {
        return code;
    }
}
