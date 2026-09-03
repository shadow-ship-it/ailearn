package com.oa.rag_ai.rag;

import com.oa.rag_ai.config.MinioProperties;
import com.oa.rag_ai.document.DocumentType;
import com.oa.rag_ai.document.UnsupportedDocumentTypeException;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档摄入：解析 -> 清洗 -> 按章节切分 -> 调用 Qwen 嵌入模型向量化。
 *
 * <p>向量不落库，直接随接口返回，由调用方自行持久化。
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private record Chunk(String chapter, String text) {
    }

    private final EmbeddingModel embeddingModel;
    private final TextCleaner textCleaner;
    private final ChapterSplitter chapterSplitter;
    private final TokenTextSplitter tokenTextSplitter;
    private final IngestProperties properties;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final String model;

    public IngestionService(EmbeddingModel embeddingModel,
                            TextCleaner textCleaner,
                            ChapterSplitter chapterSplitter,
                            TokenTextSplitter tokenTextSplitter,
                            IngestProperties properties,
                            MinioClient minioClient,
                            MinioProperties minioProperties,
                            @Value("${spring.ai.openai.embedding.model:unknown}") String model) {
        this.embeddingModel = embeddingModel;
        this.textCleaner = textCleaner;
        this.chapterSplitter = chapterSplitter;
        this.tokenTextSplitter = tokenTextSplitter;
        this.properties = properties;
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
        this.model = model;
    }

    /**
     * 摄入上传的文件。
     */
    public IngestionResult ingest(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String original = file.getOriginalFilename();
        if (!StringUtils.hasText(original)) {
            throw new IllegalArgumentException("无法获取上传文件名");
        }
        String filename = StringUtils.cleanPath(original);
        DocumentType type = DocumentType.fromFilename(filename)
                .orElseThrow(() -> new UnsupportedDocumentTypeException(
                        "不支持的文档类型：" + filename + "，仅支持 " + DocumentType.supportedExtensions()));

        Path tempFile = createTempFile(type.getExtension());
        try {
            file.transferTo(tempFile);
            return ingest(new FileSystemResource(tempFile.toFile()), filename);
        } catch (IOException e) {
            throw new IllegalStateException("读取上传文件失败：" + e.getMessage(), e);
        } finally {
            deleteQuietly(tempFile);
        }
    }

    /**
     * 摄入 MinIO 中已存在的文档。
     */
    public IngestionResult ingestFromMinio(String objectName) {
        if (!StringUtils.hasText(objectName) || objectName.contains("..")) {
            throw new IllegalArgumentException("非法的 objectName：" + objectName);
        }
        DocumentType type = DocumentType.fromFilename(objectName)
                .orElseThrow(() -> new UnsupportedDocumentTypeException(
                        "不支持的文档类型：" + objectName + "，仅支持 " + DocumentType.supportedExtensions()));

        Path tempFile = createTempFile(type.getExtension());
        try {
            try (GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectName)
                    .build())) {
                Files.copy(response, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return ingest(new FileSystemResource(tempFile.toFile()), filenameOf(objectName));
        } catch (MinioException e) {
            throw new IllegalStateException("从 MinIO 读取文档失败：" + e.getMessage(), e);
        } catch (IOException e) {
            throw new IllegalStateException("从 MinIO 读取文档失败：" + e.getMessage(), e);
        } finally {
            deleteQuietly(tempFile);
        }
    }

    /**
     * 摄入一个 Spring Resource：解析 -> 清洗 -> 章节切分 -> 向量化。
     */
    public IngestionResult ingest(Resource resource, String filename) {
        String normalized = textCleaner.normalize(extractText(resource));
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("未能从文档中提取到文本：" + filename);
        }

        List<ChapterSplitter.Chapter> chapters = chapterSplitter.split(normalized, filename);
        // 只有一个章节且用的是文件名兜底标题时，说明没识别出真实章节，无需给每个分块加标题前缀
        boolean titled = !(chapters.size() == 1 && filename.equals(chapters.get(0).title()));
        List<Chunk> chunks = buildChunks(chapters, titled);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("文档清洗后没有可向量化的内容：" + filename);
        }

        List<String> texts = chunks.stream().map(Chunk::text).toList();
        List<float[]> vectors = embed(texts);
        if (vectors.size() != chunks.size()) {
            throw new IllegalStateException("嵌入结果数量与分块数量不一致：" + vectors.size() + " != " + chunks.size());
        }

        List<EmbeddedChunk> embeddedChunks = new ArrayList<>(chunks.size());
        int totalChars = 0;
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            totalChars += chunk.text().length();
            embeddedChunks.add(new EmbeddedChunk(i, chunk.chapter(), chunk.text(),
                    chunk.text().length(), vectors.get(i)));
        }

        int dimensions = vectors.isEmpty() || vectors.get(0) == null ? 0 : vectors.get(0).length;
        log.info("文档摄入完成: file={}, chapters={}, chunks={}, dims={}",
                filename, chapters.size(), embeddedChunks.size(), dimensions);
        return new IngestionResult(filename, model, dimensions, embeddedChunks.size(),
                totalChars, embeddedChunks);
    }

    private String extractText(Resource resource) {
        StringBuilder builder = new StringBuilder();
        for (Document document : new TikaDocumentReader(resource).get()) {
            if (document.isText() && document.getText() != null) {
                builder.append(document.getText()).append('\n');
            }
        }
        return builder.toString();
    }

    private List<Chunk> buildChunks(List<ChapterSplitter.Chapter> chapters, boolean titled) {
        List<Chunk> chunks = new ArrayList<>();
        for (ChapterSplitter.Chapter chapter : chapters) {
            String body = textCleaner.clean(chapter.body());
            if (body.isBlank() || body.length() < properties.getMinChunkLengthToEmbed()) {
                continue;
            }
            if (body.length() <= properties.getMaxChapterChars()) {
                chunks.add(new Chunk(chapter.title(), titled ? withTitle(chapter.title(), body) : body));
                continue;
            }
            for (Document part : tokenTextSplitter.apply(List.of(new Document(body)))) {
                String text = part.getText();
                if (text == null || text.isBlank() || text.length() < properties.getMinChunkLengthToEmbed()) {
                    continue;
                }
                chunks.add(new Chunk(chapter.title(), titled ? withTitle(chapter.title(), text.strip()) : text.strip()));
            }
        }
        return chunks;
    }

    /**
     * 分批调用嵌入模型，DashScope 单次请求最多 10 条文本。
     */
    private List<float[]> embed(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        int batchSize = Math.max(1, properties.getEmbeddingBatchSize());
        for (int i = 0; i < texts.size(); i += batchSize) {
            List<String> batch = List.copyOf(texts.subList(i, Math.min(i + batchSize, texts.size())));
            vectors.addAll(embeddingModel.embed(batch));
        }
        return vectors;
    }

    private static String withTitle(String title, String text) {
        return title + "\n\n" + text;
    }

    private static String filenameOf(String objectName) {
        int index = objectName.lastIndexOf('/');
        return index >= 0 ? objectName.substring(index + 1) : objectName;
    }

    private static Path createTempFile(String extension) {
        try {
            return Files.createTempFile("rag-ingest-", "." + extension);
        } catch (IOException e) {
            throw new IllegalStateException("创建临时文件失败：" + e.getMessage(), e);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("删除临时文件失败: {}", path);
        }
    }
}
