package com.oa.rag_ai.rag;

import com.oa.rag_ai.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档摄入接口：清洗 -> 按章节切分 -> 向量化，向量直接返回，不落库。
 */
@RestController
@RequestMapping("/api/ingest")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * 上传文档并直接处理，仅支持 PDF / Word / Excel。
     */
    @PostMapping("/upload")
    public ApiResponse<IngestionResult> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success("处理完成", ingestionService.ingest(file));
    }

    /**
     * 处理 MinIO 中已上传的文档。
     */
    @PostMapping("/minio")
    public ApiResponse<IngestionResult> fromMinio(@RequestParam("objectName") String objectName) {
        return ApiResponse.success("处理完成", ingestionService.ingestFromMinio(objectName));
    }
}
