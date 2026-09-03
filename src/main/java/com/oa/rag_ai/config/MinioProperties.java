package com.oa.rag_ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 连接配置，对应 application.yaml 中的 {@code minio.*} 配置。
 */
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /** MinIO 服务地址，例如 http://localhost:9000 */
    private String endpoint = "http://localhost:9000";

    /** Access Key */
    private String accessKey = "admin";

    /** Secret Key */
    private String secretKey = "Admin@123456";

    /** 文档存储桶 */
    private String bucket = "documents";

    /** 区域，MinIO 默认使用 us-east-1 */
    private String region = "us-east-1";

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}
