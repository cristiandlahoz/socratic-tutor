package com.wornux.services.context;

public class SetupRequiredException extends RuntimeException {

    public SetupRequiredException(String message) {
        super(message);
    }
}
