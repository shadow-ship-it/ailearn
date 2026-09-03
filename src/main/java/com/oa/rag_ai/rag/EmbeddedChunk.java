package com.oa.rag_ai.rag;

/**
 * 已完成向量化的文档分块。
 */
public record EmbeddedChunk(
        int index,
        String chapter,
        String text,
        int charCount,
        float[] vector
) {
}
