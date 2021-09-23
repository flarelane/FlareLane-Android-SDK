package com.flarelane;

class BaseErrorHandler {
    static void handle(Exception e) {
        com.flarelane.Logger.error("exception: " + e.getLocalizedMessage());
        e.printStackTrace();
    }
}
