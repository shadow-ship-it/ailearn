package com.oa.rag_ai.document.parser;

import com.oa.rag_ai.document.DocumentStorageException;
import com.oa.rag_ai.document.DocumentType;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.hwpf.usermodel.Table;
import org.apache.poi.hwpf.usermodel.TableCell;
import org.apache.poi.hwpf.usermodel.TableRow;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDecimalNumber;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Word 文档解析器：按段落样式识别标题，按列表编号识别列表项，原生读取表格。
 *
 * <ul>
 *   <li>{@code .docx}：XWPF，可读取段落大纲级别、样式与编号；</li>
 *   <li>{@code .doc}：HWPF，遍历区间段落，表格按对象去重后整体输出。</li>
 * </ul>
 */
@Component
public class WordDocumentParser implements DocumentParser {

    /** Word 内置标题样式的 styleId 形如 Heading1 / heading 1 / 1 */
    private static final Pattern HEADING_STYLE =
            Pattern.compile("(?i)(?:heading\\s*)?([1-9])");

    /** 大纲级别 9 表示正文，不算标题 */
    private static final int BODY_OUTLINE_LEVEL = 9;

    @Override
    public Set<DocumentType> supports() {
        return EnumSet.of(DocumentType.DOC, DocumentType.DOCX);
    }

    @Override
    public DocumentStructure parse(byte[] bytes, String filename, DocumentType type) {
        List<DocumentBlock> blocks = type == DocumentType.DOCX
                ? parseDocx(bytes, filename)
                : parseDoc(bytes, filename);
        return new DocumentStructure("word", blocks);
    }

    private List<DocumentBlock> parseDocx(byte[] bytes, String filename) {
        List<DocumentBlock> blocks = new ArrayList<>();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            int tableIndex = 0;
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    // 表格内的段落由表格块统一输出，避免重复
                    if (paragraph.getBody() instanceof XWPFTableCell) {
                        continue;
                    }
                    String text = ParserUtils.clean(paragraph.getText());
                    if (text.isEmpty()) {
                        continue;
                    }
                    int level = headingLevel(paragraph, text);
                    if (level > 0) {
                        blocks.add(DocumentBlock.heading(text, level, "paragraph@" + blocks.size()));
                    } else if (paragraph.getNumID() != null || ParserUtils.isListItem(text)) {
                        blocks.add(DocumentBlock.listItem(text, "paragraph@" + blocks.size()));
                    } else {
                        blocks.add(DocumentBlock.paragraph(text, "paragraph@" + blocks.size()));
                    }
                } else if (element instanceof XWPFTable table) {
                    List<List<String>> rows = readDocxTable(table);
                    if (!rows.isEmpty()) {
                        blocks.add(DocumentBlock.table(rows, "table@" + tableIndex++));
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new DocumentStorageException("解析 Word 文档失败：" + filename + "，原因：" + e.getMessage(), e);
        }
        return blocks;
    }

    private List<List<String>> readDocxTable(XWPFTable table) {
        List<List<String>> rows = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            boolean hasValue = false;
            for (XWPFTableCell cell : row.getTableCells()) {
                String value = ParserUtils.clean(cell.getText());
                hasValue |= !value.isEmpty();
                cells.add(value);
            }
            if (hasValue) {
                rows.add(cells);
            }
        }
        return ParserUtils.trimEmptyColumns(rows);
    }

    private List<DocumentBlock> parseDoc(byte[] bytes, String filename) {
        List<DocumentBlock> blocks = new ArrayList<>();
        Set<Integer> handledTables = new HashSet<>();
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(bytes))) {
            Range range = document.getRange();
            for (int index = 0; index < range.numParagraphs(); index++) {
                Paragraph paragraph = range.getParagraph(index);
                if (paragraph.isInTable()) {
                    Table table = range.getTable(paragraph);
                    if (table != null && handledTables.add(table.getStartOffset())) {
                        List<List<String>> rows = readDocTable(table);
                        if (!rows.isEmpty()) {
                            blocks.add(DocumentBlock.table(rows, "table@" + blocks.size()));
                        }
                    }
                    continue;
                }
                String text = ParserUtils.clean(paragraph.text());
                if (text.isEmpty()) {
                    continue;
                }
                String location = "paragraph@" + index;
                int level = ParserUtils.headingLevel(text);
                boolean boldStart = paragraph.numCharacterRuns() > 0 && paragraph.getCharacterRun(0).isBold();
                if (level > 0) {
                    blocks.add(DocumentBlock.heading(text, level, location));
                } else if (boldStart && text.length() <= 40 && !text.endsWith("。")) {
                    blocks.add(DocumentBlock.heading(text, 3, location));
                } else if (ParserUtils.isListItem(text)) {
                    blocks.add(DocumentBlock.listItem(text, location));
                } else {
                    blocks.add(DocumentBlock.paragraph(text, location));
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new DocumentStorageException("解析 Word 文档失败：" + filename + "，原因：" + e.getMessage(), e);
        }
        return blocks;
    }

    private List<List<String>> readDocTable(Table table) {
        List<List<String>> rows = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < table.numRows(); rowIndex++) {
            TableRow row = table.getRow(rowIndex);
            List<String> cells = new ArrayList<>();
            boolean hasValue = false;
            for (int cellIndex = 0; cellIndex < row.numCells(); cellIndex++) {
                TableCell cell = row.getCell(cellIndex);
                String value = cell == null ? "" : ParserUtils.clean(cell.text());
                hasValue |= !value.isEmpty();
                cells.add(value);
            }
            if (hasValue) {
                rows.add(cells);
            }
        }
        return ParserUtils.trimEmptyColumns(rows);
    }

    private static int headingLevel(XWPFParagraph paragraph, String text) {
        CTDecimalNumber outline = paragraph.getCTP().getPPr() == null
                ? null
                : paragraph.getCTP().getPPr().getOutlineLvl();
        if (outline != null && outline.getVal().intValue() < BODY_OUTLINE_LEVEL) {
            return Math.min(6, outline.getVal().intValue() + 1);
        }
        String styleId = paragraph.getStyleID();
        if (styleId != null) {
            Matcher matcher = HEADING_STYLE.matcher(styleId.strip());
            if (matcher.matches()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return ParserUtils.headingLevel(text);
    }
}
