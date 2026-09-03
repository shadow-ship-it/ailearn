package com.oa.rag_ai.rag;

import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文档摄入相关 Bean 配置。
 */
@Configuration
@EnableConfigurationProperties(IngestProperties.class)
public class RagConfig {

    @Bean
    public TokenTextSplitter tokenTextSplitter(IngestProperties properties) {
        return TokenTextSplitter.builder()
                .withChunkSize(properties.getChunkSize())
                .withMinChunkSizeChars(properties.getMinChunkSizeChars())
                .withMinChunkLengthToEmbed(properties.getMinChunkLengthToEmbed())
                .withMaxNumChunks(properties.getMaxNumChunks())
                .withKeepSeparator(properties.isKeepSeparator())
                .build();
    }
}
