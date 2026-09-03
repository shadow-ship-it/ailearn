package com.oa.rag_ai.document;

/**
 * 文档上传结果。
 */
public record DocumentUploadResult(
        String objectName,
        String bucket,
        String originalFilename,
        String contentType,
        long size
) {
}
