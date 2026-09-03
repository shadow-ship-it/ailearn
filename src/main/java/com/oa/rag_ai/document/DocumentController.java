package com.oa.rag_ai.document;

import com.oa.rag_ai.common.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档上传与访问接口，支持 PDF / Word / Excel。
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final long DEFAULT_EXPIRY_SECONDS = 3600L;

    private final DocumentStorageService documentStorageService;

    public DocumentController(DocumentStorageService documentStorageService) {
        this.documentStorageService = documentStorageService;
    }

    /**
     * 上传单个文档。
     */
    @PostMapping("/upload")
    public ApiResponse<DocumentUploadResult> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success("上传成功", documentStorageService.upload(file));
    }

    /**
     * 批量上传文档。
     */
    @PostMapping("/upload/batch")
    public ApiResponse<List<DocumentUploadResult>> uploadBatch(@RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        return ApiResponse.success("上传成功", documentStorageService.upload(files));
    }

    /**
     * 获取文档的预签名下载地址，默认有效期 1 小时。
     */
    @GetMapping("/url")
    public ApiResponse<DocumentUrl> downloadUrl(
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "expiry", required = false, defaultValue = "3600") long expirySeconds) {
        String url = documentStorageService.presignedDownloadUrl(objectName,
                expirySeconds > 0 ? expirySeconds : DEFAULT_EXPIRY_SECONDS);
        return ApiResponse.success(new DocumentUrl(objectName,
                documentStorageService.getBucket(), url, expirySeconds));
    }

    /**
     * 删除文档。
     */
    @DeleteMapping
    public ApiResponse<Void> delete(@RequestParam("objectName") String objectName) {
        documentStorageService.delete(objectName);
        return ApiResponse.success("删除成功", null);
    }
}
