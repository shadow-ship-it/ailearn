package com.oa.rag_ai.document.parser;

/**
 * 文档结构化块的语义类型。
 */
public enum BlockType {

    /** 标题 */
    HEADING,

    /** 正文段落 */
    PARAGRAPH,

    /** 表格 */
    TABLE,

    /** 列表项 */
    LIST_ITEM
}
