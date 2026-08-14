package com.vincent.tools.dict.domain;

public class DictException extends RuntimeException {
    private final DictErrorCode code;

    public DictException(DictErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public DictErrorCode getCode() {
        return code;
    }
}
