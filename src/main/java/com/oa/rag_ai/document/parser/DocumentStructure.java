package com.oa.rag_ai.document.parser;

import java.util.List;
import java.util.Locale;

/**
 * 单个文档解析出的结构化内容。
 *
 * @param parser 使用的解析器标识：pdf / word / excel
 * @param blocks 按文档顺序排列的结构化块
 */
public record DocumentStructure(String parser, List<DocumentBlock> blocks) {

    /**
     * 将结构化块还原为带层级标记的纯文本，用于全文检索与向量化。
     */
    public String toPlainText() {
        StringBuilder builder = new StringBuilder();
        for (DocumentBlock block : blocks) {
            if (block.getText() == null || block.getText().isBlank()) {
                continue;
            }
            if (block.getType() == BlockType.HEADING) {
                builder.append("#".repeat(block.getLevel() == null ? 1 : block.getLevel()));
                builder.append(' ');
            } else if (block.getType() == BlockType.LIST_ITEM) {
                builder.append("- ");
            }
            builder.append(block.getText()).append("\n\n");
        }
        return builder.toString().strip();
    }

    public long countByType(BlockType type) {
        return blocks.stream().filter(block -> block.getType() == type).count();
    }

    public String describe() {
        return String.format(Locale.ROOT, "parser=%s, blocks=%d, headings=%d, paragraphs=%d, tables=%d, listItems=%d",
                parser, blocks.size(), countByType(BlockType.HEADING), countByType(BlockType.PARAGRAPH),
                countByType(BlockType.TABLE), countByType(BlockType.LIST_ITEM));
    }
}
