package vn.rikkei.exam.vehiclereservation.dto;

public record SourceDto(
        String id,
        String source,
        String section,
        Integer chunkIndex,
        Double score
) {}