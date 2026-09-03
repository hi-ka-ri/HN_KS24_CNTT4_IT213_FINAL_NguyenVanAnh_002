package vn.rikkei.exam.vehiclereservation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.rikkei.exam.vehiclereservation.dto.AssistantAskRequest;
import vn.rikkei.exam.vehiclereservation.dto.AssistantResponse;
import vn.rikkei.exam.vehiclereservation.service.chat.ChatService;

@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {
    private final ChatService chatService;

    @PostMapping("/ask")
    public ResponseEntity<AssistantResponse> ask(@Valid @RequestBody AssistantAskRequest request) {
        return ResponseEntity.ok(chatService.ask(request.conversationId(), request.message()));
    }
}
