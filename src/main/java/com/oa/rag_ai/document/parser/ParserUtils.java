package com.oa.rag_ai.document.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构化解析的公共文本规则：清洗、标题编号识别、列表项识别。
 */
public final class ParserUtils {

    /** 单个单元格 / 文本的最大保留字符数，避免异常文档撑爆 MongoDB 单文档 16MB 限制 */
    public static final int MAX_TEXT_CHARS = 4000;

    /** 单个文档最多保留的结构化块数量 */
    public static final int MAX_BLOCKS = 5000;

    /**
     * 单个文档最多入库的文本字符数（约 6MB UTF-8），
     * 与 {@link #MAX_BLOCKS} 共同保证单条记录不超过 MongoDB 16MB 文档上限。
     */
    public static final int MAX_TOTAL_TEXT_CHARS = 2_000_000;

    /** 超过该长度的整行不再认为是标题 */
    public static final int MAX_HEADING_CHARS = 60;

    /** 中文章节：第一章、第2节、第三部分 */
    private static final Pattern CN_CHAPTER =
            Pattern.compile("^第\\s*[0-9一二三四五六七八九十百千]+\\s*[章节篇部]");

    /** 多级编号：1、1.1、1.1.2 */
    private static final Pattern DOTTED_NUMBER =
            Pattern.compile("^(\\d+(?:\\.\\d+){0,5})[\\s.、]");

    /** 项目符号或编号开头的列表项（中文常无空格，故符号后允许零个空白） */
    private static final Pattern LIST_MARK = Pattern.compile(
            "^([•·▪◦‣⁃*-]|[（(]?\\d{1,2}[）).、]|[a-zA-Z][).、]|第\\s*[0-9一二三四五六七八九十百]+\\s*[条项])\\s*\\S");

    /** 段落结束的常见标点，用于判断 PDF 换行是否为软换行 */
    private static final String SENTENCE_END = "。！？；：”’)]》.!?;:";

    private ParserUtils() {
    }

    /**
     * 清理文本：去掉 Word 单元格结束符、零宽字符、软连字符等，合并多余空白。
     */
    public static String clean(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value
                .replace('\u0007', ' ')
                .replace('\u000B', ' ')
                .replace('\u000C', ' ')
                .replace('\u00A0', ' ')
                .replace('\uFEFF', ' ')
                .replace('\u200B', ' ')
                .replace('\u200C', ' ')
                .replace('\u200D', ' ')
                .replace('\u2060', ' ')
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .strip();
        return truncate(cleaned, MAX_TEXT_CHARS);
    }

    public static String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }

    /**
     * 根据编号推断标题层级：{@code 1.2.3} 返回 3，{@code 第一章} 返回 1；不是编号标题返回 -1。
     */
    public static int headingLevel(String text) {
        if (text == null || text.isBlank()) {
            return -1;
        }
        String value = text.strip();
        if (value.length() > MAX_HEADING_CHARS) {
            return -1;
        }
        if (CN_CHAPTER.matcher(value).find()) {
            return 1;
        }
        Matcher matcher = DOTTED_NUMBER.matcher(value);
        if (matcher.find()) {
            String number = matcher.group(1);
            long depth = number.chars().filter(ch -> ch == '.').count();
            return (int) Math.min(6, depth + 1);
        }
        return -1;
    }

    public static boolean isListItem(String text) {
        return text != null && LIST_MARK.matcher(text.strip()).find();
    }

    /**
     * 判断一行是否为上一行的续行（PDF 软换行合并时使用）。
     */
    public static boolean isContinuation(String previous, String current) {
        if (previous == null || current == null) {
            return false;
        }
        String prev = previous.strip();
        String next = current.strip();
        if (prev.isEmpty() || next.isEmpty()) {
            return false;
        }
        char last = prev.charAt(prev.length() - 1);
        if (SENTENCE_END.indexOf(last) >= 0) {
            return false;
        }
        char first = next.charAt(0);
        // 新行以编号、项目符号或大写字母开头时，视为新段落
        if (LIST_MARK.matcher(next).find() || headingLevel(next) > 0) {
            return false;
        }
        return !Character.isUpperCase(first) && !Character.isDigit(first);
    }

    /**
     * 删除表格中全空的列，并去掉每行尾部空单元格。
     */
    public static List<List<String>> trimEmptyColumns(List<List<String>> rows) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        int columns = rows.stream().mapToInt(List::size).max().orElse(0);
        boolean[] keep = new boolean[columns];
        for (List<String> row : rows) {
            for (int column = 0; column < row.size(); column++) {
                if (!row.get(column).isEmpty()) {
                    keep[column] = true;
                }
            }
        }
        List<List<String>> result = new ArrayList<>(rows.size());
        for (List<String> row : rows) {
            List<String> cells = new ArrayList<>(columns);
            for (int column = 0; column < columns; column++) {
                if (keep[column]) {
                    cells.add(column < row.size() ? row.get(column) : "");
                }
            }
            result.add(cells);
        }
        return result;
    }

    /**
     * 判断字体是否为加粗系列（PDF 字体名推断）。
     */
    public static boolean isBoldFont(String fontName) {
        if (fontName == null) {
            return false;
        }
        String lower = fontName.toLowerCase(Locale.ROOT);
        return lower.contains("bold") || lower.contains("black") || lower.contains("semibold")
                || lower.contains("heavy");
    }
}
