package vn.rikkei.exam.vehiclereservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ApprovalRequest(
        @NotBlank(message = "requestId is required") String requestId,
        @NotNull(message = "decision is required") Decision decision,
        String note
) {
    public enum Decision { APPROVE, REJECT }
}