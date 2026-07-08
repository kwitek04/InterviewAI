package com.interviewai.cv.application;

/**
 * Thrown when a {@link com.interviewai.cv.application.port.out.FileStorage} lookup
 * finds no object stored under the requested key.
 */
public final class FileNotFoundInStorageException extends RuntimeException {

    private final String key;

    public FileNotFoundInStorageException(String key) {
        super("No file found in storage under key " + key);
        this.key = key;
    }

    public String key() {
        return key;
    }
}
