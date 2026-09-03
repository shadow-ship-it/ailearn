package com.oa.rag_ai.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按章节标题切分文档。
 *
 * <p>支持中英文常见标题形式：
 * <ul>
 *     <li>第一章 / 第二节 / 第三篇 / 第四部</li>
 *     <li>Chapter 1、Section 2、Part 3</li>
 *     <li>1. / 1.1 / 1.1.1 形式的编号标题</li>
 *     <li>一、二、三、 形式的中文编号标题</li>
 *     <li>摘要、Abstract、绪论、结论、参考文献、附录 等固定章节名</li>
 * </ul>
 * 识别到少于 2 个标题时，整篇作为一个章节返回。
 */
@Component
public class ChapterSplitter {

    public record Chapter(String title, String body, int level) {
    }

    private record Heading(int lineIndex, String title, int level) {
    }

    private static final Pattern CN_CHAPTER = Pattern.compile(
            "^\\s*(第\\s*[0-9一二三四五六七八九十百千零〇]+\\s*[篇章节部])[\\s:：、.．\\-]*(.*)$");

    private static final Pattern EN_CHAPTER = Pattern.compile(
            "^\\s*(chapter|section|part)\\s+([0-9IVXLC]+|[一二三四五六七八九十]+)[\\s:：、.．\\-]*(.*)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NUMBER_HEADING = Pattern.compile(
            "^\\s*(\\d{1,3}(?:[.．]\\d{1,3}){0,3})[.．、]?(?=\\s)\\s*(.*)$");

    private static final Pattern CN_NUMBER_HEADING = Pattern.compile(
            "^\\s*([一二三四五六七八九十]{1,4})\\s*[、.．]\\s*(.*)$");

    private static final Pattern KEYWORD_HEADING = Pattern.compile(
            "^\\s*(摘要|abstract|前言|序言|绪论|引言|结论|总结|参考文献|致谢|目录|附录\\s*[A-Za-z0-9]*)[\\s:：、.．]*(.*)$",
            Pattern.CASE_INSENSITIVE);

    /** 形如年份的数字，避免把 "2024 年营收" 当成编号标题 */
    private static final Pattern YEAR_LIKE = Pattern.compile("^(19|20)\\d{2}$");

    /** 以句末标点结尾的行基本不是标题 */
    private static final Pattern TERMINAL_PUNCT = Pattern.compile("[。！？；.!?,，;:：]$");

    private final IngestProperties properties;

    public ChapterSplitter(IngestProperties properties) {
        this.properties = properties;
    }

    /**
     * @param text          已做过 {@link TextCleaner#normalize(String)} 的文本
     * @param fallbackTitle 未识别到章节时使用的标题（一般是文件名）
     */
    public List<Chapter> split(String text, String fallbackTitle) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] lines = text.split("\n", -1);
        List<Heading> headings = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            Heading heading = detect(lines[i]);
            if (heading != null) {
                headings.add(new Heading(i, heading.title(), heading.level()));
            }
        }
        if (headings.size() < 2) {
            String body = text.trim();
            return body.isEmpty() ? List.of() : List.of(new Chapter(fallbackTitle, body, 1));
        }

        List<Chapter> chapters = new ArrayList<>();
        String preamble = slice(lines, 0, headings.get(0).lineIndex());
        if (!preamble.isBlank()) {
            chapters.add(new Chapter(fallbackTitle, preamble, 1));
        }
        for (int i = 0; i < headings.size(); i++) {
            Heading heading = headings.get(i);
            int end = (i + 1 < headings.size()) ? headings.get(i + 1).lineIndex() : lines.length;
            String body = slice(lines, heading.lineIndex() + 1, end);
            if (!body.isBlank()) {
                chapters.add(new Chapter(heading.title(), body, heading.level()));
            }
        }
        return chapters;
    }

    private Heading detect(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.length() > properties.getMaxHeadingLength()) {
            return null;
        }
        if (TERMINAL_PUNCT.matcher(trimmed).find()) {
            return null;
        }

        Matcher matcher = CN_CHAPTER.matcher(trimmed);
        if (matcher.matches()) {
            String unit = matcher.group(1).replaceAll("\\s", "");
            return new Heading(0, trimmed, switch (unit.substring(unit.length() - 1)) {
                case "篇", "部" -> 1;
                case "章" -> 2;
                case "节" -> 3;
                default -> 2;
            });
        }

        matcher = EN_CHAPTER.matcher(trimmed);
        if (matcher.matches()) {
            return new Heading(0, trimmed, switch (matcher.group(1).toLowerCase()) {
                case "part" -> 1;
                case "chapter" -> 2;
                default -> 3;
            });
        }

        matcher = NUMBER_HEADING.matcher(trimmed);
        if (matcher.matches() && !YEAR_LIKE.matcher(matcher.group(1)).matches()) {
            int level = 1 + (int) matcher.group(1).chars().filter(c -> c == '.' || c == '\uFF0E').count();
            return new Heading(0, trimmed, level + 1);
        }

        if (CN_NUMBER_HEADING.matcher(trimmed).matches()) {
            return new Heading(0, trimmed, 2);
        }

        if (KEYWORD_HEADING.matcher(trimmed).matches()) {
            return new Heading(0, trimmed, 2);
        }
        return null;
    }

    private static String slice(String[] lines, int fromInclusive, int toExclusive) {
        if (toExclusive <= fromInclusive) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = fromInclusive; i < toExclusive; i++) {
            builder.append(lines[i]).append('\n');
        }
        return builder.toString().trim();
    }
}
