package com.oa.rag_ai.rag;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 文档文本清洗。
 *
 * <p>分两个层次：
 * <ul>
 *     <li>{@link #normalize(String)}：只做不破坏行结构的轻量归一化，切分前使用，保证章节标题仍可被识别；</li>
 *     <li>{@link #clean(String)}：完整清洗（去页码、压空格、合并软换行），切分后对章节正文使用。</li>
 * </ul>
 */
@Component
public class TextCleaner {

    /** 控制字符，保留换行与制表符 */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");

    /** 零宽字符与软连字符 */
    private static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200B\\u200C\\u200D\\u2060\\uFEFF\\u00AD]");

    /** 连续 3 个以上换行 */
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    /** 行内连续空白（不含换行） */
    private static final Pattern INLINE_SPACES = Pattern.compile("[^\\S\\n]{2,}");

    /** 行尾空白 */
    private static final Pattern LINE_TAIL_SPACES = Pattern.compile("[^\\S\\n]+\\n");

    /** 跨行的英文连字符换行，如 infor-\nmation */
    private static final Pattern HYPHEN_BREAK = Pattern.compile("(\\p{L})[\\u2010-\\u2015-]\\n(\\p{L})");

    /** 独立的页码行，如 "12"、"- 12 -"、"第 12 页" */
    private static final Pattern PAGE_NUMBER_LINE =
            Pattern.compile("(?m)^\\s*(?:第?\\s*\\d{1,4}\\s*页?|-\\s*\\d{1,4}\\s*-)\\s*$");

    /** 句子结束标点 */
    private static final Pattern TERMINAL_PUNCT = Pattern.compile("[。！？；!?]$");

    /** 列表项、编号或引用行的开头 */
    private static final Pattern LIST_START = Pattern.compile("^[-•·*#>(\\[（【0-9]");

    private final IngestProperties properties;

    public TextCleaner(IngestProperties properties) {
        this.properties = properties;
    }

    /**
     * 轻量归一化：统一换行、去除不可见字符、去掉行尾空白、压缩连续空行。保留行结构。
     */
    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        text = text.replace('\u00A0', ' ')
                .replace('\u3000', ' ')
                .replace('\u2007', ' ')
                .replace('\u202F', ' ');
        text = ZERO_WIDTH.matcher(text).replaceAll("");
        text = CONTROL_CHARS.matcher(text).replaceAll("");
        text = LINE_TAIL_SPACES.matcher(text).replaceAll("\n");
        text = BLANK_LINES.matcher(text).replaceAll("\n\n");
        return text.trim();
    }

    /**
     * 完整清洗：在 {@link #normalize(String)} 基础上合并软换行、剔除页码行、压缩行内空白。
     */
    public String clean(String text) {
        String cleaned = normalize(text);
        if (cleaned.isEmpty()) {
            return "";
        }
        cleaned = HYPHEN_BREAK.matcher(cleaned).replaceAll("$1$2");
        if (properties.isMergeSoftLineBreaks()) {
            cleaned = mergeSoftLineBreaks(cleaned);
        }
        cleaned = PAGE_NUMBER_LINE.matcher(cleaned).replaceAll("");
        cleaned = BLANK_LINES.matcher(cleaned).replaceAll("\n\n");
        cleaned = INLINE_SPACES.matcher(cleaned).replaceAll(" ");
        return cleaned.trim();
    }

    /**
     * 合并 PDF 抽取产生的软换行：上一行不以句末标点结尾、且下一行不像列表项时，把两行接起来。
     * 中日韩字符之间直接拼接，不加空格。
     */
    private String mergeSoftLineBreaks(String text) {
        StringBuilder result = new StringBuilder(text.length());
        String previous = null;
        for (String rawLine : text.split("\n", -1)) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                if (result.length() > 0 && result.charAt(result.length() - 1) != '\n') {
                    result.append('\n');
                }
                previous = null;
                continue;
            }
            if (previous == null) {
                result.append(line);
            } else if (TERMINAL_PUNCT.matcher(previous).find() || LIST_START.matcher(line).find()) {
                result.append('\n').append(line);
            } else if (isCjk(previous.charAt(previous.length() - 1)) || isCjk(line.charAt(0))) {
                result.append(line);
            } else {
                result.append(' ').append(line);
            }
            previous = line;
        }
        return result.toString();
    }

    private static boolean isCjk(char c) {
        return (c >= '\u3400' && c <= '\u4DBF') || (c >= '\u4E00' && c <= '\u9FFF');
    }
}
