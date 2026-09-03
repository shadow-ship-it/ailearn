package com.oa.rag_ai.rag;

import java.util.List;

/**
 * 文档摄入（清洗 -> 章节切分 -> 向量化）的结果。
 */
public record IngestionResult(
        String filename,
        String model,
        int dimensions,
        int chunkCount,
        int totalChars,
        List<EmbeddedChunk> chunks
) {
}
