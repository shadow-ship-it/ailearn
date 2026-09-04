package com.oa.rag_ai.document.mongo;

import com.oa.rag_ai.document.parser.DocumentBlock;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 上传文档的元数据与结构化内容，存入 MongoDB 的 {@code documents} 集合。
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

    /** 文档全文（结构化块还原出的纯文本，标题带 # 层级标记，表格为 Markdown） */
    private String content;

    /** 结构化内容：标题 / 段落 / 表格 / 列表项 */
    private List<DocumentBlock> blocks;

    /** 使用的解析器：pdf / word / excel */
    private String parser;

    /** 标题、段落、表格、列表项数量统计 */
    private int headingCount;

    private int paragraphCount;

    private int tableCount;

    private int listItemCount;

    /** 上传时间 */
    private LocalDateTime uploadTime;

    /** MinIO 中的对象名，便于回查原始文件 */
    private String objectName;

    /** 所属存储桶 */
    private String bucket;

    /** 内容类型 */
    private String contentType;
}
