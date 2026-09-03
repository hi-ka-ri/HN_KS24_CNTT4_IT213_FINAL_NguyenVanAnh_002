package vn.rikkei.exam.vehiclereservation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.rikkei.exam.vehiclereservation.dto.ApprovalRequest;
import vn.rikkei.exam.vehiclereservation.dto.ApprovalResponse;
import vn.rikkei.exam.vehiclereservation.service.chat.ChatService;

@RestController
@RequestMapping("/api/operations")
@RequiredArgsConstructor
public class OperationsController {
    private final ChatService chatService;

    @PostMapping("/approve-request")
    public ResponseEntity<ApprovalResponse> approveRequest(@Valid @RequestBody ApprovalRequest request) {
        return ResponseEntity.ok(chatService.approve(
                request.requestId(), request.decision().name(), request.note()));
    }
}