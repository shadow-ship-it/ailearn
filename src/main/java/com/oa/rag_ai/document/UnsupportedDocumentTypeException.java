package com.oa.rag_ai.document;

/**
 * 上传了非 PDF / Word / Excel 文档时抛出。
 */
public class UnsupportedDocumentTypeException extends RuntimeException {

    public UnsupportedDocumentTypeException(String message) {
        super(message);
    }
}
