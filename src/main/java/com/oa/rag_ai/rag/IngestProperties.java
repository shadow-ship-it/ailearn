package com.oa.rag_ai.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文档清洗、切分与向量化的参数配置，对应 {@code rag.ingest.*}。
 */
@ConfigurationProperties(prefix = "rag.ingest")
public class IngestProperties {

    /** 单个分块的目标 token 数 */
    private int chunkSize = 800;

    /** 分块的最小字符数 */
    private int minChunkSizeChars = 350;

    /** 少于该字符数的文本不再单独向量化 */
    private int minChunkLengthToEmbed = 5;

    /** 单个章节最多切出多少分块 */
    private int maxNumChunks = 10000;

    /** 切分时是否保留分隔符 */
    private boolean keepSeparator = true;

    /** 章节正文超过该字符数才做二次切分 */
    private int maxChapterChars = 1500;

    /** 单次 embedding 请求的文本条数 */
    private int embeddingBatchSize = 10;

    /** 是否合并 PDF 换行产生的软换行 */
    private boolean mergeSoftLineBreaks = true;

    /** 标题行的最大长度 */
    private int maxHeadingLength = 60;

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getMinChunkSizeChars() {
        return minChunkSizeChars;
    }

    public void setMinChunkSizeChars(int minChunkSizeChars) {
        this.minChunkSizeChars = minChunkSizeChars;
    }

    public int getMinChunkLengthToEmbed() {
        return minChunkLengthToEmbed;
    }

    public void setMinChunkLengthToEmbed(int minChunkLengthToEmbed) {
        this.minChunkLengthToEmbed = minChunkLengthToEmbed;
    }

    public int getMaxNumChunks() {
        return maxNumChunks;
    }

    public void setMaxNumChunks(int maxNumChunks) {
        this.maxNumChunks = maxNumChunks;
    }

    public boolean isKeepSeparator() {
        return keepSeparator;
    }

    public void setKeepSeparator(boolean keepSeparator) {
        this.keepSeparator = keepSeparator;
    }

    public int getMaxChapterChars() {
        return maxChapterChars;
    }

    public void setMaxChapterChars(int maxChapterChars) {
        this.maxChapterChars = maxChapterChars;
    }

    public int getEmbeddingBatchSize() {
        return embeddingBatchSize;
    }

    public void setEmbeddingBatchSize(int embeddingBatchSize) {
        this.embeddingBatchSize = embeddingBatchSize;
    }

    public boolean isMergeSoftLineBreaks() {
        return mergeSoftLineBreaks;
    }

    public void setMergeSoftLineBreaks(boolean mergeSoftLineBreaks) {
        this.mergeSoftLineBreaks = mergeSoftLineBreaks;
    }

    public int getMaxHeadingLength() {
        return maxHeadingLength;
    }

    public void setMaxHeadingLength(int maxHeadingLength) {
        this.maxHeadingLength = maxHeadingLength;
    }
}
