package com.oa.rag_ai.document.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 上传文档的元数据与提取内容，存入 MongoDB 的 {@code documents} 集合。
 */
@Data
@Document(collection = "documents")
public class DocumentRecord {

    @Id
    private String id;

    /** 文档名称（原始文件名） */
    private String name;

    /** 文档大小（字节） */
    private long size;

    /** 文档内容（解析提取的纯文本） */
    private String content;

    /** 上传时间 */
    private LocalDateTime uploadTime;

    /** MinIO 中的对象名，便于回查原始文件 */
    private String objectName;

    /** 所属存储桶 */
    private String bucket;

    /** 内容类型 */
    private String contentType;
}
