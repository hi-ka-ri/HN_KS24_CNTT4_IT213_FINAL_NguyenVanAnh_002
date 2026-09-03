package vn.rikkei.exam.vehiclereservation.service.rag;

import org.springframework.ai.document.Document;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IngestionService {
    private final VectorStore vectorStore;
    private final Resource corpus;
    private final String corpusSource;

    public IngestionService(
            VectorStore vectorStore,
            @Value("classpath:tai_lieu_noi_bo.md") Resource corpus,
            @Value("${rag.corpus-source:tai_lieu_noi_bo.md}") String corpusSource) {
        this.vectorStore = vectorStore;
        this.corpus = corpus;
        this.corpusSource = corpusSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ingestOnStartup() {
        try {
            ingestCorpus();
        } catch (Exception e) {
            // Không ngăn ứng dụng khởi động
            System.err.println(
                    "Đã bỏ qua quá trình nạp dữ liệu RAG: "
                            + e.getClass().getSimpleName()
            );
        }
    }

    public synchronized Map<String, Object> ingestCorpus() {
        try {
            String text = new String(
                    corpus.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            List<Document> documents = chunkBySections(text);
            vectorStore.add(documents);

            return Map.of(
                    "source", corpusSource,
                    "chunks", documents.size(),
                    "status", "ok"
            );

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Không thể nạp tài liệu nội bộ vào hệ thống RAG",
                    e
            );
        }
    }

    private List<Document> chunkBySections(String text) {
        String[] lines = text
                .replace("\r\n", "\n")
                .trim()
                .split("\\n");

        List<Document> docs = new ArrayList<>();
        String currentSection = "Introduction";
        StringBuilder current = new StringBuilder();
        int index = 0;

        for (String line : lines) {
            if (line.matches("^#{1,6}\\s+.+$")) {
                if (!current.isEmpty()) {
                    docs.add(
                            buildDocument(
                                    currentSection,
                                    current.toString(),
                                    index++
                            )
                    );
                    current.setLength(0);
                }

                currentSection = line
                        .replaceFirst("^#{1,6}\\s+", "")
                        .trim();
            }

            current.append(line).append('\n');
        }

        if (!current.isEmpty()) {
            docs.add(
                    buildDocument(
                            currentSection,
                            current.toString(),
                            index
                    )
            );
        }

        return docs;
    }

    private Document buildDocument(
            String section,
            String content,
            int index) {

        String clean = content.trim();

        String hash = sha256(
                corpusSource + "\n" + section + "\n" + clean
        );

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", corpusSource);
        metadata.put("doc_id", sha256(corpusSource));
        metadata.put("section", section);
        metadata.put("chunk_index", index);
        metadata.put("content_hash", hash);

        return new Document(hash, clean, metadata);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));

            StringBuilder result = new StringBuilder();

            for (byte b : digest) {
                result.append(String.format("%02x", b));
            }

            return result.toString();

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Không thể tạo mã băm SHA-256 cho đoạn tài liệu RAG",
                    e
            );
        }
    }
}