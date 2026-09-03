package com.oa.rag_ai.document;

/**
 * 文档下载地址。
 */
public record DocumentUrl(String objectName, String bucket, String url, long expiresInSeconds) {
}
