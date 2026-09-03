package vn.rikkei.exam.vehiclereservation.dto;

public record ApprovalResponse(
        String status,
        String requestId,
        String message
) {}