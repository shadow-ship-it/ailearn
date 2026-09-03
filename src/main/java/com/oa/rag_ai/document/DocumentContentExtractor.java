package com.oa.rag_ai.document;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.List;

/**
 * 使用 Apache Tika 解析文档并提取纯文本内容。
 */
@Component
public class DocumentContentExtractor {

    /**
     * @param bytes    文档二进制内容
     * @param filename 文件名，用于类型探测
     * @return 提取的纯文本
     */
    public String extract(byte[] bytes, String filename) {
        InputStreamResource resource = new InputStreamResource(new ByteArrayInputStream(bytes), filename);
        StringBuilder builder = new StringBuilder();
        List<Document> documents = new TikaDocumentReader(resource).get();
        for (Document document : documents) {
            if (document.isText() && document.getText() != null) {
                builder.append(document.getText()).append('\n');
            }
        }
        return builder.toString();
    }
}
