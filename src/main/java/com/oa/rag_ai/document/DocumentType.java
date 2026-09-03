package com.oa.rag_ai.document;

import java.util.Locale;
import java.util.Optional;

/**
 * 允许上传的文档类型：PDF、Word、Excel。
 */
public enum DocumentType {

    PDF("application/pdf"),
    DOC("application/msword"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    XLS("application/vnd.ms-excel"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final String contentType;

    DocumentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }

    public String getExtension() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * 根据文件名后缀解析文档类型，不支持的类型返回 {@link Optional#empty()}。
     */
    public static Optional<DocumentType> fromFilename(String filename) {
        if (filename == null) {
            return Optional.empty();
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return Optional.empty();
        }
        String extension = filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        for (DocumentType type : values()) {
            if (type.getExtension().equals(extension)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    public static String supportedExtensions() {
        return "pdf, doc, docx, xls, xlsx";
    }
}
