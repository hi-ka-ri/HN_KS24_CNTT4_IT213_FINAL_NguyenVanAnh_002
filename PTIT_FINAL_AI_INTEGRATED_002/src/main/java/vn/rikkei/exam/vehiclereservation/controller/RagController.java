package vn.rikkei.exam.vehiclereservation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.rikkei.exam.vehiclereservation.service.rag.IngestionService;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {
    private final IngestionService ingestionService;

    @PostMapping("/ingest")
    public ResponseEntity<?> ingest() {
        return ResponseEntity.ok(ingestionService.ingestCorpus());
    }
}
