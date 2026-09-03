RAG with Postgres + pgvector (setup and usage)

Overview
- Chunk by headings, store per-chunk metadata (title, source doc id, chunk_index)
- Deterministic dedup: SHA-256 of chunk content -> content_hash UNIQUE (ON CONFLICT DO NOTHING)
- Embeddings stored in pgvector embedding column; retrieval uses <-> operator (distance)
- API response must include: answer, conversationId, resources, toolsUsed
- If no evidence from corpus, fallback answer: "Không đủ căn cứ trong tài liệu nội bộ."

Postgres prep (run as DB superuser)
- Install extension:
  CREATE EXTENSION IF NOT EXISTS vector;
- Create helper functions (pgcrypto for gen_random_uuid):
  CREATE EXTENSION IF NOT EXISTS pgcrypto;

Table (example)
  CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doc_id TEXT,
    chunk_index INT,
    content TEXT,
    content_hash TEXT UNIQUE,
    metadata JSONB,
    embedding VECTOR(1536)
  );

Index for fast retrieval (IVF-Flat)
  -- Choose lists based on corpus size
  CREATE INDEX IF NOT EXISTS idx_documents_embedding ON documents USING ivfflat (embedding vector_l2_ops) WITH (lists = 100);

Deduplication
- Use deterministic content_hash = SHA256(chunk_text)
- Insert with ON CONFLICT (content_hash) DO NOTHING

Ingestion flow (Java tool provided)
- Read file
- Chunk by headings (or fallback fixed window)
- For each chunk compute embedding (OpenAI embeddings or other embedding provider)
- Upsert into documents table with content_hash to avoid duplicates
- Each chunk stores metadata JSONB with source tracing (file path, heading line number)

Retrieval flow
- Embed user query
- SELECT doc_id, chunk_index, content, metadata, embedding <-> query_embedding AS distance
  FROM documents ORDER BY distance ASC LIMIT k
- If top results are above a similarity threshold (tune empirically), use them as evidence and construct answer; include resources = list of {doc_id, chunk_index, metadata}
- If no results pass threshold -> return fallback: "Không đủ căn cứ trong tài liệu nội bộ."

API Response JSON (minimum fields)
{
  "conversationId": "<uuid>",
  "answer": "<generated or fallback answer>",
  "resources": [ {"doc_id": "...", "chunk_index": 0, "metadata": {...}, "content": "..." } ],
  "toolsUsed": ["RagPgvectorTool","OpenAI-embeddings","Postgres+pgvector"]
}

Notes
- Business data MUST go through the Java tool to be ingested (per requirement).
- Use content_hash dedup and optional doc_id deterministic scheme if ingesting same document multiple times.
- Ensure environment variables: OPENAI_API_KEY, PG_JDBC_URL, PG_USER, PG_PASSWORD
- Tune embedding model and vector dim (1536 for OpenAI text-embedding-3-large)

Fallback policy
- When retrieval returns no adequate evidence, the assistant must answer: "Không đủ căn cứ trong tài liệu nội bộ." and must NOT hallucinate.

Next steps
- Compile java file and install JDBC + Jackson dependencies
- Run: java -cp <classpath> RagPgvectorTool ingest <docId> <filePath>
- Run retrieval: java -cp <classpath> RagPgvectorTool retrieve "question"

