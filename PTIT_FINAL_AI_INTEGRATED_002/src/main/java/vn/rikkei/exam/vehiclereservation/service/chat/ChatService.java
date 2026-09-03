package vn.rikkei.exam.vehiclereservation.service.chat;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import vn.rikkei.exam.vehiclereservation.dto.AssistantResponse;
import vn.rikkei.exam.vehiclereservation.dto.SourceDto;
import vn.rikkei.exam.vehiclereservation.dto.ApprovalResponse;
import vn.rikkei.exam.vehiclereservation.service.ReservationService;
import vn.rikkei.exam.vehiclereservation.service.rag.RAGService;
import vn.rikkei.exam.vehiclereservation.service.tool.VehicleReservationTools;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final RAGService ragService;
    private final VehicleReservationTools tools;
    private final ReservationService reservationService;
    private final Tracer langfuseTracer;

    public AssistantResponse ask(String conversationId, String message) {
        String id = chatMemory.ensureConversation(conversationId);
        Span span = langfuseTracer.spanBuilder("assistant.ask").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            span.setAttribute("langfuse.observation.type", "agent");
            span.setAttribute("langfuse.session.id", id);
            span.setAttribute("langfuse.observation.metadata.conversationId", id);
            span.setAttribute("langfuse.observation.metadata.examCode", "DE-002");
            span.setAttribute("langfuse.observation.input", message);

            chatMemory.addUserTurn(id, message);
            RAGService.RetrievalResult retrieval = ragService.retrieve(message);
            String history = chatMemory.historyAsText(id);

            String system = """
                    Bạn là trợ lý điều phối xe công tác nội bộ của Rikkei.
                    Quy tắc bắt buộc:
                    1. Câu hỏi về chính sách/quy định nội bộ chỉ được trả lời dựa trên CONTEXT tài liệu nội bộ được cung cấp.
                    2. Câu hỏi về dữ liệu nghiệp vụ như khả dụng xe hoặc tạo yêu cầu đặt xe phải dùng Java tools; không tự suy đoán dữ liệu DB.
                    3. Nếu cần đặt xe nhưng thiếu userId, loại xe, ngày, số người hoặc mục đích, hãy hỏi thông tin còn thiếu.
                    4. Không bịa nguồn, số liệu, trạng thái hay requestId.
                    5. Trả lời tiếng Việt, ngắn gọn và rõ ràng.
                    """;

            String prompt = system
                    + "\n\nCONTEXT TÀI LIỆU NỘI BỘ:\n"
                    + (retrieval.hasEvidence() ? retrieval.context() : "(Không tìm thấy bằng chứng đủ ngưỡng.)")
                    + "\n\nLỊCH SỬ HỘI THOẠI:\n"
                    + (history.isBlank() ? "(chưa có)" : history)
                    + "\n\nYÊU CẦU HIỆN TẠI:\n" + message;

            String answer = chatClient.prompt()
                    .system(prompt)
                    .user(message)
                    .tools(tools)
                    .call()
                    .content();

            Set<String> executed = tools.consumeExecutedTools();
            List<String> toolsUsed = executed.stream().toList();
            List<SourceDto> sources = retrieval.sources();

            if (!retrieval.hasEvidence() && toolsUsed.isEmpty()) {
                answer = RAGService.FALLBACK;
            }

            chatMemory.addAssistantTurn(id, answer);
            span.setAttribute("langfuse.observation.metadata.toolsUsed", String.join(",", toolsUsed));
            span.setAttribute("langfuse.observation.metadata.sources", sources.toString());
            span.setAttribute("langfuse.observation.output", answer);
            return new AssistantResponse(answer, id, sources, toolsUsed);
        } catch (RuntimeException ex) {
            span.recordException(ex);
            throw ex;
        } finally {
            span.end();
        }
    }

    public ApprovalResponse approve(String requestId, String decision, String note) {
        Map<String, Object> result = reservationService.approveOrReject(requestId, decision, note);
        return new ApprovalResponse(
                String.valueOf(result.get("status")),
                requestId,
                "Request processed successfully");
    }
}
