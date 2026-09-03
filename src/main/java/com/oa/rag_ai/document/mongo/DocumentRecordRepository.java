package com.oa.rag_ai.document.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 上传文档记录的 MongoDB 仓储。
 */
public interface DocumentRecordRepository extends MongoRepository<DocumentRecord, String> {
}
