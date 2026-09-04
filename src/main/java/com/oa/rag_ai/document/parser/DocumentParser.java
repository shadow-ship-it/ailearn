package com.oa.rag_ai.document.parser;

import com.oa.rag_ai.document.DocumentType;

import java.util.Set;

/**
 * 按具体文件格式实现的解析器。
 *
 * <p>PDF、Word、Excel 差异很大：PDF 只有带坐标的文本行，需要靠字号/位置推断结构；
 * Word 有段落样式与表格对象；Excel 本身即二维表格。因此每种格式各写一个实现。
 */
public interface DocumentParser {

    /** 该解析器支持的文件格式 */
    Set<DocumentType> supports();

    /**
     * @param bytes    文档二进制内容
     * @param filename 原始文件名，用于日志与位置标记
     * @param type     已识别的文档格式
     * @return 结构化解析结果
     */
    DocumentStructure parse(byte[] bytes, String filename, DocumentType type);
}
