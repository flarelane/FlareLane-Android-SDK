package com.flarelane;

public class NullValueException extends Exception {
    public NullValueException(String valueName) {
        super(valueName + " is null");
    }
}
