package vn.rikkei.exam.vehiclereservation.config;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class McpConfig {

    @Bean
    RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }

    @Bean
    McpClientCustomizer<HttpClientStreamableHttpTransport.Builder> antigravityTokenCustomizer() {
        return (name, builder) -> {
            String token = System.getenv("MCP_ANTIGRAVITY_TOKEN");

            if ("antigravity".equals(name)
                    && token != null
                    && !token.isBlank()) {

                builder.httpRequestCustomizer(
                        (request, method, endpoint, body, context) -> {
                            request.header(
                                    "Authorization",
                                    "Bearer " + token
                            );
                        }
                );
            }
        };
    }
}