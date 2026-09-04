package com.oa.rag_ai.document.parser;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档中的一个结构化块，对应标题、段落、表格或列表项。
 *
 * <p>会作为 {@code DocumentRecord} 的内嵌数组存入 MongoDB。
 */
@Data
public class DocumentBlock {

    /** 块类型 */
    private BlockType type;

    /** 标题层级（1~6），仅 HEADING 有值 */
    private Integer level;

    /** 块内文本；表格块为 Markdown 渲染结果，便于直接全文检索与向量化 */
    private String text;

    /** 表格内容，仅 TABLE 有值，第一行为表头 */
    private List<List<String>> rows;

    /** 位置信息：PDF 为页码、Excel 为工作表名、Word 为段落/表格序号 */
    private String location;

    /** 在文档中的顺序，从 0 开始 */
    private int order;

    public static DocumentBlock heading(String text, int level, String location) {
        DocumentBlock block = new DocumentBlock();
        block.type = BlockType.HEADING;
        block.level = Math.min(6, Math.max(1, level));
        block.text = text;
        block.location = location;
        return block;
    }

    public static DocumentBlock paragraph(String text, String location) {
        DocumentBlock block = new DocumentBlock();
        block.type = BlockType.PARAGRAPH;
        block.text = text;
        block.location = location;
        return block;
    }

    public static DocumentBlock listItem(String text, String location) {
        DocumentBlock block = new DocumentBlock();
        block.type = BlockType.LIST_ITEM;
        block.text = text;
        block.location = location;
        return block;
    }

    /**
     * 构造表格块，文本自动渲染为 Markdown 表格。
     */
    public static DocumentBlock table(List<List<String>> rows, String location) {
        DocumentBlock block = new DocumentBlock();
        block.type = BlockType.TABLE;
        block.rows = rows;
        block.text = renderTable(rows);
        block.location = location;
        return block;
    }

    private static String renderTable(List<List<String>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        int columns = rows.stream().mapToInt(List::size).max().orElse(0);
        if (columns == 0) {
            return "";
        }
        StringBuilder markdown = new StringBuilder();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            List<String> cells = new ArrayList<>(columns);
            for (int column = 0; column < columns; column++) {
                String value = column < row.size() ? row.get(column) : "";
                // 单元格中的换行会破坏 Markdown 表格结构
                cells.add(value.replace('|', '／').replace('\n', ' ').strip());
            }
            markdown.append('|');
            for (String cell : cells) {
                markdown.append(' ').append(cell).append(" |");
            }
            markdown.append('\n');
            if (rowIndex == 0) {
                markdown.append('|');
                for (int column = 0; column < columns; column++) {
                    markdown.append(" --- |");
                }
                markdown.append('\n');
            }
        }
        return markdown.toString().strip();
    }
}
