package vn.rikkei.exam.vehiclereservation.service.langfuse;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LangfuseService {
    private final Tracer tracer;

    public LangfuseService(Tracer tracer) {
        this.tracer = tracer;
    }

    public Span startChatTrace(String conversationId) {
        Span span = tracer.spanBuilder("assistant.ask").startSpan();
        span.setAttribute("langfuse.observation.type", "agent");
        span.setAttribute("langfuse.session.id", conversationId);
        span.setAttribute("langfuse.observation.metadata.conversationId", conversationId);
        span.setAttribute("langfuse.observation.metadata.examCode", "DE-002");
        return span;
    }

    public void finish(Span span, List<String> toolsUsed, Object sources, String answer) {
        span.setAttribute("langfuse.observation.metadata.toolsUsed", String.join(",", toolsUsed));
        span.setAttribute("langfuse.observation.metadata.sources", String.valueOf(sources));
        span.setAttribute("langfuse.observation.output", answer);
        span.end();
    }
}
