package vn.rikkei.exam.vehiclereservation.dto;

import jakarta.validation.constraints.NotBlank;

public record AssistantAskRequest(
        String conversationId,
        @NotBlank(message = "message is required") String message
) {}
