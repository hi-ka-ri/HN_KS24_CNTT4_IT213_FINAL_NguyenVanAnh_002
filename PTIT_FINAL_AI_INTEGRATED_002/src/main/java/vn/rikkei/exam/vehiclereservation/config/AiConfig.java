package vn.rikkei.exam.vehiclereservation.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.rikkei.exam.vehiclereservation.service.tool.VehicleReservationTools;

@Configuration
public class AiConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, VehicleReservationTools tools) {
        return builder
                .defaultTools(tools)
                .build();
    }
}