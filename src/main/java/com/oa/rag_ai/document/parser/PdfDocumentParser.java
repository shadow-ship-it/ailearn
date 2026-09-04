package com.oa.rag_ai.document.parser;

import com.oa.rag_ai.document.DocumentStorageException;
import com.oa.rag_ai.document.DocumentType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PDF 解析器。
 *
 * <p>PDF 没有段落 / 标题 / 表格这类语义结构，只能拿到带坐标的文本行，因此按以下规则还原：
 * <ul>
 *   <li>标题：字号明显大于正文字号，或加粗的短行，或含编号（1 / 1.1 / 第一章）；</li>
 *   <li>表格：按字符水平间隙切列，连续多行列数与列起点一致时聚合为表格；</li>
 *   <li>段落：其余文本行，跨行软换行按标点与首字母规则合并。</li>
 * </ul>
 */
@Component
public class PdfDocumentParser implements DocumentParser {

    /** 判定分列的最小水平间隙（pt） */
    private static final float COLUMN_GAP = 8.0f;

    /** 同一表格相邻两行列起点的容差（pt） */
    private static final float COLUMN_TOLERANCE = 20.0f;

    /** 字号达到正文字号的该倍数即视为标题 */
    private static final float HEADING_FONT_RATIO = 1.12f;

    /** 至少两行同构才认定为表格 */
    private static final int MIN_TABLE_ROWS = 2;

    /** 单行列数上限，超过则认为是排版异常，按段落处理 */
    private static final int MAX_TABLE_COLUMNS = 24;

    @Override
    public Set<DocumentType> supports() {
        return EnumSet.of(DocumentType.PDF);
    }

    @Override
    public DocumentStructure parse(byte[] bytes, String filename, DocumentType type) {
        List<Line> lines = collectLines(bytes, filename);
        return new DocumentStructure("pdf", buildBlocks(lines));
    }

    private List<Line> collectLines(byte[] bytes, String filename) {
        // Loader 没有 loadPDF(InputStream) 重载，直接传字节数组
        try (PDDocument document = Loader.loadPDF(bytes)) {
            LineCollector collector = new LineCollector();
            collector.setSortByPosition(true);
            collector.setStartPage(1);
            collector.setEndPage(document.getNumberOfPages());
            collector.getText(document);
            return collector.lines();
        } catch (IOException | RuntimeException e) {
            throw new DocumentStorageException("解析 PDF 文档失败：" + filename + "，原因：" + e.getMessage(), e);
        }
    }

    /**
     * 逐行收集文本、字号、是否加粗与字符坐标。
     */
    private static final class LineCollector extends PDFTextStripper {

        private final List<Line> lines = new ArrayList<>();
        private final StringBuilder buffer = new StringBuilder();
        private final List<TextPosition> positions = new ArrayList<>();
        private float fontSize;
        private boolean bold;

        LineCollector() throws IOException {
            super();
        }

        List<Line> lines() {
            return lines;
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) {
            for (TextPosition position : textPositions) {
                buffer.append(position.getUnicode());
                positions.add(position);
                fontSize = Math.max(fontSize, position.getFontSizeInPt());
                if (position.getFont() != null && ParserUtils.isBoldFont(position.getFont().getName())) {
                    bold = true;
                }
            }
        }

        @Override
        protected void writeLineSeparator() {
            flush();
        }

        @Override
        protected void endPage(PDPage page) throws IOException {
            flush();
            super.endPage(page);
        }

        private void flush() {
            String text = ParserUtils.clean(buffer.toString());
            if (!text.isEmpty()) {
                lines.add(new Line(text, fontSize, bold, getCurrentPageNo(), List.copyOf(positions)));
            }
            buffer.setLength(0);
            positions.clear();
            fontSize = 0f;
            bold = false;
        }
    }

    private record Line(String text, float fontSize, boolean bold, int page, List<TextPosition> positions) {
    }

    /** 正在累积的表格 */
    private static final class TableBuffer {
        private final List<List<String>> rows = new ArrayList<>();
        private List<Float> stops;
        private int page;
    }

    private List<DocumentBlock> buildBlocks(List<Line> lines) {
        List<DocumentBlock> blocks = new ArrayList<>();
        float bodyFontSize = detectBodyFontSize(lines);

        StringBuilder paragraph = new StringBuilder();
        String paragraphLocation = null;
        TableBuffer table = null;

        for (Line line : lines) {
            List<Float> stops = columnStops(line);
            List<String> cells = splitColumns(line);

            if (cells.size() >= 2 && cells.size() <= MAX_TABLE_COLUMNS) {
                if (paragraph.length() > 0) {
                    flushParagraph(paragraph, paragraphLocation, blocks);
                    paragraphLocation = null;
                }
                if (table != null && sameLayout(table.stops, stops)) {
                    table.rows.add(cells);
                } else {
                    flushTable(table, blocks);
                    table = new TableBuffer();
                    table.stops = stops;
                    table.page = line.page();
                    table.rows.add(cells);
                }
                continue;
            }

            flushTable(table, blocks);
            table = null;

            String text = line.text();
            if (text.isEmpty()) {
                continue;
            }
            String location = "page-" + line.page();
            int level = headingLevel(line, bodyFontSize, text);
            if (level > 0) {
                if (paragraph.length() > 0) {
                    flushParagraph(paragraph, paragraphLocation, blocks);
                    paragraphLocation = null;
                }
                blocks.add(DocumentBlock.heading(text, level, location));
            } else if (ParserUtils.isListItem(text)) {
                if (paragraph.length() > 0) {
                    flushParagraph(paragraph, paragraphLocation, blocks);
                    paragraphLocation = null;
                }
                blocks.add(DocumentBlock.listItem(text, location));
            } else if (paragraph.length() == 0) {
                paragraphLocation = location;
                paragraph.append(text);
            } else if (ParserUtils.isContinuation(paragraph.toString(), text)) {
                appendLine(paragraph, text);
            } else {
                flushParagraph(paragraph, paragraphLocation, blocks);
                paragraphLocation = location;
                paragraph.setLength(0);
                paragraph.append(text);
            }
        }

        if (paragraph.length() > 0) {
            flushParagraph(paragraph, paragraphLocation, blocks);
        }
        flushTable(table, blocks);
        return blocks;
    }

    /**
     * 正文字号取「按字符数加权」后出现最多的字号，避免页眉页脚干扰。
     */
    private static float detectBodyFontSize(List<Line> lines) {
        Map<Float, Integer> weights = new HashMap<>();
        for (Line line : lines) {
            float size = Math.round(line.fontSize() * 2f) / 2f;
            weights.merge(size, line.text().length(), Integer::sum);
        }
        return weights.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0f);
    }

    private static int headingLevel(Line line, float bodyFontSize, String text) {
        if (text.length() > ParserUtils.MAX_HEADING_CHARS) {
            return -1;
        }
        int numbered = ParserUtils.headingLevel(text);
        boolean largerFont = bodyFontSize > 0 && line.fontSize() >= bodyFontSize * HEADING_FONT_RATIO;
        boolean shortBold = line.bold() && text.length() <= 40 && !text.endsWith("。");
        if (numbered < 0 && !largerFont && !shortBold) {
            return -1;
        }
        if (numbered > 0) {
            return numbered;
        }
        float ratio = bodyFontSize > 0 ? line.fontSize() / bodyFontSize : 1f;
        if (ratio >= 1.6f) {
            return 1;
        }
        if (ratio >= 1.35f) {
            return 2;
        }
        return ratio >= 1.15f ? 3 : 4;
    }

    /**
     * 按字符间的水平间隙把一行切成多列。
     */
    private static List<String> splitColumns(Line line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        Float previousEnd = null;
        for (TextPosition position : line.positions()) {
            float start = position.getXDirAdj();
            if (previousEnd != null && start - previousEnd >= COLUMN_GAP && cell.length() > 0) {
                String value = ParserUtils.clean(cell.toString());
                if (!value.isEmpty()) {
                    cells.add(value);
                }
                cell.setLength(0);
            }
            cell.append(position.getUnicode());
            previousEnd = start + position.getWidthDirAdj();
        }
        String value = ParserUtils.clean(cell.toString());
        if (!value.isEmpty()) {
            cells.add(value);
        }
        return cells;
    }

    /**
     * 一行的列起点坐标，用于判断相邻两行是否属于同一表格。
     */
    private static List<Float> columnStops(Line line) {
        List<Float> stops = new ArrayList<>();
        Float previousEnd = null;
        for (TextPosition position : line.positions()) {
            float start = position.getXDirAdj();
            if (previousEnd == null || start - previousEnd >= COLUMN_GAP) {
                stops.add(start);
            }
            previousEnd = start + position.getWidthDirAdj();
        }
        return stops;
    }

    private static boolean sameLayout(List<Float> previous, List<Float> current) {
        if (previous == null || previous.size() != current.size()) {
            return false;
        }
        for (int index = 0; index < previous.size(); index++) {
            if (Math.abs(previous.get(index) - current.get(index)) > COLUMN_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    private static void flushTable(TableBuffer table, List<DocumentBlock> blocks) {
        if (table == null || table.rows.isEmpty()) {
            return;
        }
        String location = "page-" + table.page;
        if (table.rows.size() >= MIN_TABLE_ROWS) {
            blocks.add(DocumentBlock.table(ParserUtils.trimEmptyColumns(table.rows), location));
            return;
        }
        // 只有一行时不成表，退回段落
        blocks.add(DocumentBlock.paragraph(String.join(" ", table.rows.get(0)), location));
    }

    private static void flushParagraph(StringBuilder paragraph, String location, List<DocumentBlock> blocks) {
        if (paragraph == null || paragraph.length() == 0) {
            return;
        }
        blocks.add(DocumentBlock.paragraph(paragraph.toString(), location));
        paragraph.setLength(0);
    }

    /**
     * 合并软换行：中文直接相连，西文之间补一个空格。
     */
    private static void appendLine(StringBuilder paragraph, String text) {
        if (paragraph.length() == 0 || text.isEmpty()) {
            paragraph.append(text);
            return;
        }
        char previous = paragraph.charAt(paragraph.length() - 1);
        char next = text.charAt(0);
        if (isAsciiWord(previous) && isAsciiWord(next)) {
            paragraph.append(' ');
        }
        paragraph.append(text);
    }

    private static boolean isAsciiWord(char value) {
        return Character.isLetterOrDigit(value) && value < 128;
    }
}
