package vn.rikkei.exam.vehiclereservation.dto;

import java.util.List;

public record AssistantResponse(
        String answer,
        String conversationId,
        List<SourceDto> sources,
        List<String> toolsUsed
) {}
