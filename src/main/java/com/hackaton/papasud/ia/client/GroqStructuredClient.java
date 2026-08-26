package com.hackaton.papasud.ia.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackaton.papasud.ia.dto.OpenAiRequestDto;
import com.hackaton.papasud.ia.dto.OpenAiResponseDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class GroqStructuredClient {

    private static final Set<String> RETRYABLE_SCHEMA_CODES = Set.of(
            "json_validate_failed", "structured_generation_failed");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String apiKey;
    private final String apiModel;
    private final int maxCompletionTokens;

    public GroqStructuredClient(
            @Qualifier("groqRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${openai.api.url}") String apiUrl,
            @Value("${openai.api.key:}") String apiKey,
            @Value("${openai.api.model}") String apiModel,
            @Value("${openai.api.max-completion-tokens:4096}") int maxCompletionTokens) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.apiModel = apiModel;
        this.maxCompletionTokens = maxCompletionTokens;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public <T> T complete(
            String schemaName,
            Map<String, Object> jsonSchema,
            String system,
            String user,
            Class<T> type) {
        if (!isConfigured()) {
            throw new IllegalStateException("GROQ API key ausente.");
        }
        try {
            return read(completeOnce(schemaName, jsonSchema, system, user, true), type);
        } catch (SchemaRejectedException rejected) {
            log.warn("reintento structured output sin strict: status={} code={}", rejected.status, rejected.code);
            return read(completeOnce(schemaName, jsonSchema, system, user, false), type);
        }
    }

    private String completeOnce(
            String schemaName,
            Map<String, Object> jsonSchema,
            String system,
            String user,
            boolean strict) {
        OpenAiRequestDto request = buildRequest(schemaName, jsonSchema, system, user, strict);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        try {
            OpenAiResponseDto response = restTemplate.postForObject(
                    apiUrl, new HttpEntity<>(request, headers), OpenAiResponseDto.class);
            return extractJsonText(response);
        } catch (HttpStatusCodeException error) {
            if (strict && isRetryableSchemaError(error)) {
                throw new SchemaRejectedException(error.getStatusCode().value(), schemaErrorCode(error));
            }
            throw error;
        }
    }

    private OpenAiRequestDto buildRequest(
            String schemaName,
            Map<String, Object> jsonSchema,
            String system,
            String user,
            boolean strict) {
        Map<String, Object> jsonSchemaEnvelope = new LinkedHashMap<>();
        jsonSchemaEnvelope.put("name", schemaName);
        jsonSchemaEnvelope.put("strict", strict);
        jsonSchemaEnvelope.put("schema", jsonSchema);

        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "json_schema");
        responseFormat.put("json_schema", jsonSchemaEnvelope);

        boolean gptOss = apiModel != null && apiModel.toLowerCase().contains("gpt-oss");
        return new OpenAiRequestDto(
                apiModel,
                List.of(
                        new OpenAiRequestDto.Message("system", system),
                        new OpenAiRequestDto.Message("user", user)),
                0.0,
                responseFormat,
                gptOss ? "low" : null,
                gptOss ? maxCompletionTokens : null,
                gptOss ? Boolean.FALSE : null);
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception error) {
            throw new IllegalStateException("Groq no devolvió JSON válido.", error);
        }
    }

    String extractJsonText(OpenAiResponseDto response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().get(0).message() == null) {
            throw new IllegalStateException("Groq no devolvió contenido.");
        }
        OpenAiResponseDto.Message message = response.choices().get(0).message();
        String content = textFrom(message.content()).trim();
        if (!content.isEmpty()) {
            return parseJsonObject(content);
        }
        if (message.reasoning() != null && !message.reasoning().isBlank()) {
            return parseJsonObject(message.reasoning());
        }
        throw new IllegalStateException("Groq no devolvió contenido.");
    }

    private String textFrom(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof List<?> parts) {
            StringBuilder text = new StringBuilder();
            for (Object part : parts) {
                if (part instanceof String stringPart) {
                    text.append(stringPart);
                } else if (part instanceof Map<?, ?> map && map.get("text") instanceof String mapText) {
                    text.append(mapText);
                }
            }
            return text.toString();
        }
        if (value instanceof Map<?, ?> map) {
            try {
                return objectMapper.writeValueAsString(map);
            } catch (Exception ignored) {
                return String.valueOf(value);
            }
        }
        return String.valueOf(value);
    }

    private static String parseJsonObject(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private boolean isRetryableSchemaError(HttpStatusCodeException error) {
        return error.getStatusCode().value() == 400 && RETRYABLE_SCHEMA_CODES.contains(schemaErrorCode(error));
    }

    private String schemaErrorCode(HttpStatusCodeException error) {
        try {
            JsonNode code = objectMapper.readTree(error.getResponseBodyAsString()).path("error").path("code");
            return code.isTextual() ? code.asText() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static final class SchemaRejectedException extends RuntimeException {
        private final int status;
        private final String code;

        private SchemaRejectedException(int status, String code) {
            super("Groq rechazó el schema");
            this.status = status;
            this.code = code;
        }
    }
}
