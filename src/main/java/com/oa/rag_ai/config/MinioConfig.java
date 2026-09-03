package com.oa.rag_ai.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置，启动时确保文档存储桶存在。
 */
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        MinioClient client = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .region(properties.getRegion())
                .build();
        ensureBucket(client, properties.getBucket());
        return client;
    }

    private void ensureBucket(MinioClient client, String bucket) {
        try {
            if (client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                log.info("MinIO bucket [{}] already exists", bucket);
                return;
            }
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("MinIO bucket [{}] created", bucket);
        } catch (MinioException e) {
            throw new IllegalStateException("初始化 MinIO bucket [" + bucket + "] 失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("无法连接 MinIO 服务: " + e.getMessage(), e);
        }
    }
}
