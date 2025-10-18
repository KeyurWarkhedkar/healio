package com.keyur.healio.CustomExceptions;

public class SlotOverlapException extends RuntimeException {
    public SlotOverlapException(String message) {
        super(message);
    }
}
