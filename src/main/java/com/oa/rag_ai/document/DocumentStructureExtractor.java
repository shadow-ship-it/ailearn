package com.oa.rag_ai.document;

import com.oa.rag_ai.document.parser.DocumentBlock;
import com.oa.rag_ai.document.parser.DocumentParserRegistry;
import com.oa.rag_ai.document.parser.DocumentStructure;
import com.oa.rag_ai.document.parser.ParserUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档结构化提取入口：按格式分派解析器，输出标题 / 段落 / 表格 / 列表项。
 */
@Component
public class DocumentStructureExtractor {

    private final DocumentParserRegistry registry;

    public DocumentStructureExtractor(DocumentParserRegistry registry) {
        this.registry = registry;
    }

    /**
     * @param bytes    文档二进制内容
     * @param filename 文件名，用于类型探测
     * @return 结构化解析结果
     */
    public DocumentStructure extract(byte[] bytes, String filename) {
        DocumentType type = DocumentType.fromFilename(filename)
                .orElseThrow(() -> new UnsupportedDocumentTypeException(
                        "不支持的文档类型：" + filename + "，仅支持 " + DocumentType.supportedExtensions()));
        return withOrder(registry.parse(bytes, filename, type));
    }

    /**
     * 限制块数量与总文本量并补齐块序号，避免超大文档撑爆 MongoDB 单文档 16MB 限制。
     */
    private DocumentStructure withOrder(DocumentStructure structure) {
        List<DocumentBlock> blocks = structure.blocks();
        List<DocumentBlock> kept = new ArrayList<>();
        int totalChars = 0;
        for (DocumentBlock block : blocks) {
            int textChars = block.getText() == null ? 0 : block.getText().length();
            if (kept.size() >= ParserUtils.MAX_BLOCKS || totalChars + textChars > ParserUtils.MAX_TOTAL_TEXT_CHARS) {
                break;
            }
            totalChars += textChars;
            block.setOrder(kept.size());
            kept.add(block);
        }
        return new DocumentStructure(structure.parser(), kept);
    }
}
