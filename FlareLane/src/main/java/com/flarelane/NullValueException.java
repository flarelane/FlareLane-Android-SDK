package com.flarelane;

class NullValueException extends Exception {
    NullValueException(String valueName) {
        super(valueName + " is null");
    }
}
