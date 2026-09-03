package vn.rikkei.exam.vehiclereservation.service.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import vn.rikkei.exam.vehiclereservation.dto.SourceDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class RAGService {
    public static final String FALLBACK = "Không đủ căn cứ trong tài liệu nội bộ";
    private static final double SIMILARITY_THRESHOLD = 0.70;
    private final VectorStore vectorStore;

    public RAGService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public RetrievalResult retrieve(String query) {
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(5)
                        .similarityThreshold(SIMILARITY_THRESHOLD)
                        .build());

        List<SourceDto> sources = new ArrayList<>();
        StringBuilder context = new StringBuilder();
        for (Document doc : documents) {
            Map<String, Object> metadata = doc.getMetadata();
            Integer index = metadata.get("chunk_index") instanceof Number n ? n.intValue() : 0;
            SourceDto source = new SourceDto(
                    doc.getId(),
                    String.valueOf(metadata.getOrDefault("source", "unknown")),
                    String.valueOf(metadata.getOrDefault("section", "unknown")),
                    index,
                    doc.getScore());
            sources.add(source);
            context.append("[SECTION: ").append(source.section()).append("]\n")
                    .append(doc.getText()).append("\n\n");
        }
        return new RetrievalResult(context.toString().trim(), sources);
    }

    public record RetrievalResult(String context, List<SourceDto> sources) {
        public boolean hasEvidence() { return !sources.isEmpty(); }
    }
}
