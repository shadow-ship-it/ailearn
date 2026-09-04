package com.oa.rag_ai.document.parser;

import com.oa.rag_ai.document.DocumentType;
import com.oa.rag_ai.document.UnsupportedDocumentTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 按文档格式路由到对应的解析器实现。
 */
@Component
public class DocumentParserRegistry {

    private static final Logger log = LoggerFactory.getLogger(DocumentParserRegistry.class);

    private final Map<DocumentType, DocumentParser> parsers;

    public DocumentParserRegistry(List<DocumentParser> parsers) {
        Map<DocumentType, DocumentParser> mapping = new EnumMap<>(DocumentType.class);
        for (DocumentParser parser : parsers) {
            for (DocumentType type : parser.supports()) {
                DocumentParser existing = mapping.putIfAbsent(type, parser);
                if (existing != null) {
                    log.warn("文档类型 {} 已由 {} 处理，忽略重复的解析器 {}",
                            type, existing.getClass().getSimpleName(), parser.getClass().getSimpleName());
                }
            }
        }
        for (DocumentType type : DocumentType.values()) {
            if (!mapping.containsKey(type)) {
                throw new IllegalStateException("缺少 " + type + " 对应的文档解析器实现");
            }
        }
        this.parsers = Map.copyOf(mapping);
    }

    /**
     * 按格式解析文档。
     */
    public DocumentStructure parse(byte[] bytes, String filename, DocumentType type) {
        DocumentParser parser = parsers.get(type);
        if (parser == null) {
            throw new UnsupportedDocumentTypeException(
                    "不支持的文档类型：" + filename + "，仅支持 " + DocumentType.supportedExtensions());
        }
        return parser.parse(bytes, filename, type);
    }
}
