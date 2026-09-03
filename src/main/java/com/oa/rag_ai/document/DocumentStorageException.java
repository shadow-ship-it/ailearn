package com.oa.rag_ai.document;

/**
 * 文档存储（MinIO）操作失败时抛出。
 */
public class DocumentStorageException extends RuntimeException {

    public DocumentStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
