package vn.rikkei.exam.vehiclereservation.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class LangfuseConfig {

    @Bean
    OpenTelemetry openTelemetry(
            @Value("${langfuse.enabled:true}") boolean enabled,
            @Value("${langfuse.host:http://localhost:3000}") String host,
            @Value("${langfuse.public-key:}") String publicKey,
            @Value("${langfuse.secret-key:}") String secretKey) {
        if (!enabled || publicKey.isBlank() || secretKey.isBlank()) {
            return OpenTelemetry.noop();
        }
        String auth = Base64.getEncoder().encodeToString(
                (publicKey + ":" + secretKey)
                        .getBytes(StandardCharsets.UTF_8)
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Basic " + auth);
        headers.put("x-langfuse-ingestion-version", "4");
        OtlpHttpSpanExporter exporter =
                OtlpHttpSpanExporter.builder()
                        .setEndpoint(host.replaceAll("/$", "") + "/api/public/otel/v1/traces")
                        .setHeaders(() -> headers)
                        .build();

        SdkTracerProvider provider =
                SdkTracerProvider.builder()
                        .setResource(
                                Resource.create(
                                        Attributes.builder().put(
                                                        "service.name",
                                                        "rikkei-vehicle-reservation").put(
                                                        "service.version",
                                                        "DE-002").build())
                        )
                        .addSpanProcessor(
                                BatchSpanProcessor.builder(exporter).build()).build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(provider)
                .build();
    }

    @Bean
    Tracer langfuseTracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(
                "rikkei-vehicle-reservation"
        );
    }
}