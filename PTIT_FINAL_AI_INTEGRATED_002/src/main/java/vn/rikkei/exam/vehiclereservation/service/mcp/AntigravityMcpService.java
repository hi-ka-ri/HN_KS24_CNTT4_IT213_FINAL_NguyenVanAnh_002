package vn.rikkei.exam.vehiclereservation.service.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class AntigravityMcpService {

    private final RestClient restClient;
    private final boolean enabled;
    private final String endpoint;
    private final String token;

    public AntigravityMcpService(
            RestClient restClient,
            @Value("${mcp.antigravity.enabled:false}") boolean enabled,
            @Value("${mcp.antigravity.endpoint:}") String endpoint,
            @Value("${mcp.antigravity.token:}") String token) {
        this.restClient = restClient;
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.token = token;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        if (!enabled) {
            log.info("MCP Antigravity đang bị tắt");
            return;
        }

        if (endpoint == null || endpoint.isBlank()
                || token == null || token.isBlank()) {
            log.warn("MCP Antigravity đã được bật nhưng chưa cấu hình endpoint hoặc token");
            return;
        }

        try {
            restClient.get()
                    .uri(endpoint)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Kiểm tra kết nối MCP Antigravity thành công");

        } catch (Exception ex) {
            log.warn(
                    "Kiểm tra kết nối MCP Antigravity thất bại: {}",
                    ex.getClass().getSimpleName()
            );
        }
    }
}