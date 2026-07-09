package com.interviewai.cv.application.port.out;

/**
 * Persistence port for binary files such as uploaded CVs.
 * <p>
 * Implemented by an adapter in the infrastructure layer; the domain and application
 * layers depend only on this abstraction.
 */
public interface FileStorage {

    /**
     * Stores the given content under the given key, overwriting any existing
     * object at that key.
     */
    StoredFile store(String key, byte[] content, String contentType);

    /**
     * Retrieves the content stored under the given key.
     *
     * @throws com.interviewai.cv.application.FileNotFoundInStorageException if no
     *         object exists under the given key
     */
    byte[] retrieve(String key);
}
