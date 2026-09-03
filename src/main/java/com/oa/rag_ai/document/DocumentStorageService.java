package com.oa.rag_ai.document;

import com.oa.rag_ai.config.MinioProperties;
import com.oa.rag_ai.document.DocumentContentExtractor;
import com.oa.rag_ai.document.mongo.DocumentRecord;
import com.oa.rag_ai.document.mongo.DocumentRecordRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Http;
import io.minio.errors.MinioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 基于 MinIO 的文档存储服务，仅允许上传 PDF / Word / Excel。
 */
@Service
public class DocumentStorageService {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorageService.class);

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final MinioClient minioClient;
    private final MinioProperties properties;
    private final Executor documentParseExecutor;
    private final DocumentRecordRepository documentRecordRepository;
    private final DocumentContentExtractor contentExtractor;

    public DocumentStorageService(MinioClient minioClient,
                                  MinioProperties properties,
                                  @Qualifier("documentParseExecutor") Executor documentParseExecutor,
                                  DocumentRecordRepository documentRecordRepository,
                                  DocumentContentExtractor contentExtractor) {
        this.minioClient = minioClient;
        this.properties = properties;
        this.documentParseExecutor = documentParseExecutor;
        this.documentRecordRepository = documentRecordRepository;
        this.contentExtractor = contentExtractor;
    }

    /**
     * 上传单个文档。
     */
    public DocumentUploadResult upload(MultipartFile file) {
        String originalFilename = resolveFilename(file);
        DocumentType type = DocumentType.fromFilename(originalFilename)
                .orElseThrow(() -> new UnsupportedDocumentTypeException(
                        "不支持的文档类型：" + originalFilename + "，仅支持 " + DocumentType.supportedExtensions()));

        String objectName = buildObjectName(type);
        try (InputStream inputStream = file.getInputStream()) {
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1L)
                    .contentType(type.getContentType())
                    .build();
            ObjectWriteResponse response = minioClient.putObject(args);
            log.info("文档上传成功: bucket={}, object={}, etag={}", properties.getBucket(), objectName, response.etag());
        } catch (IOException e) {
            throw new DocumentStorageException("读取上传文件失败：" + originalFilename, e);
        } catch (MinioException e) {
            throw new DocumentStorageException("上传文档到 MinIO 失败：" + e.getMessage(), e);
        } catch (Exception e) {
            throw new DocumentStorageException("上传文档失败：" + e.getMessage(), e);
        }

        // 上传成功后异步解析并入库，不阻塞上传响应
        persistDocumentAsync(file, originalFilename, objectName, type.getContentType());

        return new DocumentUploadResult(objectName, properties.getBucket(), originalFilename,
                type.getContentType(), file.getSize());
    }

    /**
     * 在独立线程中解析文档内容并写入 MongoDB（文档名称、大小、内容、上传时间）。
     */
    private void persistDocumentAsync(MultipartFile file,
                                      String filename,
                                      String objectName,
                                      String contentType) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("读取上传文件字节失败，跳过内容入库: name={}", filename, e);
            return;
        }
        long size = file.getSize();
        String bucket = properties.getBucket();
        documentParseExecutor.execute(() -> storeDocumentRecord(bytes, filename, size, objectName, bucket, contentType));
    }

    private void storeDocumentRecord(byte[] bytes, String name, long size, String objectName,
                                     String bucket, String contentType) {
        try {
            String content = contentExtractor.extract(bytes, name);
            DocumentRecord record = new DocumentRecord();
            record.setName(name);
            record.setSize(size);
            record.setContent(content);
            record.setUploadTime(LocalDateTime.now());
            record.setObjectName(objectName);
            record.setBucket(bucket);
            record.setContentType(contentType);
            documentRecordRepository.save(record);
            log.info("文档内容已异步写入 MongoDB: name={}, chars={}", name, content.length());
        } catch (Exception e) {
            log.error("文档内容异步解析/入库失败: name={}", name, e);
        }
    }

    /**
     * 批量上传文档。
     */
    public List<DocumentUploadResult> upload(List<MultipartFile> files) {
        List<DocumentUploadResult> results = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            results.add(upload(file));
        }
        return results;
    }

    /**
     * 生成文档的预签名下载地址。
     */
    public String presignedDownloadUrl(String objectName, long expirySeconds) {
        String safeObjectName = requireObjectName(objectName);
        int expiry = (int) Math.min(expirySeconds, Integer.MAX_VALUE);
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Http.Method.GET)
                    .bucket(properties.getBucket())
                    .object(safeObjectName)
                    .expiry(expiry, TimeUnit.SECONDS)
                    .build());
        } catch (MinioException | RuntimeException e) {
            throw new DocumentStorageException("生成文档下载地址失败：" + e.getMessage(), e);
        }
    }

    /**
     * 删除文档。
     */
    public void delete(String objectName) {
        String safeObjectName = requireObjectName(objectName);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(safeObjectName)
                    .build());
            log.info("文档删除成功: bucket={}, object={}", properties.getBucket(), safeObjectName);
        } catch (MinioException | RuntimeException e) {
            throw new DocumentStorageException("删除文档失败：" + e.getMessage(), e);
        }
    }

    /**
     * 当前使用的存储桶名称。
     */
    public String getBucket() {
        return properties.getBucket();
    }

    private String resolveFilename(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String original = file.getOriginalFilename();
        if (!StringUtils.hasText(original)) {
            throw new IllegalArgumentException("无法获取上传文件名");
        }
        return StringUtils.cleanPath(original);
    }

    private String buildObjectName(DocumentType type) {
        return LocalDate.now().format(DATE_PATH) + "/" + UUID.randomUUID() + "." + type.getExtension();
    }

    private String requireObjectName(String objectName) {
        if (!StringUtils.hasText(objectName) || objectName.contains("..")) {
            throw new IllegalArgumentException("非法的 objectName：" + objectName);
        }
        return objectName;
    }
}
