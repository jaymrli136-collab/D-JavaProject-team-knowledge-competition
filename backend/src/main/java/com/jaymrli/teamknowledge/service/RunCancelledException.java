package com.jaymrli.teamknowledge.service;

public class RunCancelledException extends RuntimeException {
    public RunCancelledException(String message) {
        super(message);
    }
}
