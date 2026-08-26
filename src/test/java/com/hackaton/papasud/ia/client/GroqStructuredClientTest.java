package com.hackaton.papasud.ia.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class GroqStructuredClientTest {

    private static final String URL = "http://groq.test/v1/chat/completions";
    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", java.util.List.of("ok"),
            "properties", Map.of("ok", Map.of("type", "boolean")));

    public static class OkDto {
        public boolean ok;
    }

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private GroqStructuredClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new GroqStructuredClient(
                restTemplate, new ObjectMapper(), URL, "secret-key", "openai/gpt-oss-20b", 4096);
    }

    @Test
    void sendsGptOssReasoningLimitsAndParsesFencedJson() {
        server.expect(once(), requestTo(URL))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.reasoning_effort").value("low"))
                .andExpect(jsonPath("$.max_completion_tokens").value(4096))
                .andExpect(jsonPath("$.include_reasoning").value(false))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"```json\\n{\\"ok\\":true}\\n```"}}]}
                        """, APPLICATION_JSON));

        OkDto result = client.complete("fixture", SCHEMA, "system", "user", OkDto.class);
        assertThat(result.ok).isTrue();
        server.verify();
    }

    @Test
    void retriesJsonValidateFailedWithoutStrict() {
        server.expect(once(), requestTo(URL))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(APPLICATION_JSON)
                        .body("""
                                {"error":{"code":"json_validate_failed","type":"invalid_request_error"}}
                                """));
        server.expect(once(), requestTo(URL))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(false))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"ok\\":true}"}}]}
                        """, APPLICATION_JSON));

        OkDto result = client.complete("fixture", SCHEMA, "system", "user", OkDto.class);
        assertThat(result.ok).isTrue();
        server.verify();
    }

    @Test
    void readsJsonFromReasoningWhenContentIsEmpty() {
        server.expect(once(), requestTo(URL))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"","reasoning":"paso 1\\n{\\"ok\\":true}"}}]}
                        """, APPLICATION_JSON));

        OkDto result = client.complete("fixture", SCHEMA, "system", "user", OkDto.class);
        assertThat(result.ok).isTrue();
        server.verify();
    }

    @Test
    void doesNotSendReasoningFieldsForNonGptOssModels() {
        client = new GroqStructuredClient(
                restTemplate, new ObjectMapper(), URL, "secret-key", "llama-3.3-70b-versatile", 4096);
        server.expect(once(), requestTo(URL))
                .andExpect(method(POST))
                .andExpect(request -> assertThat(((MockClientHttpRequest) request).getBodyAsString())
                        .doesNotContain("reasoning_effort"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"ok\\":true}"}}]}
                        """, APPLICATION_JSON));

        assertThat(client.complete("fixture", SCHEMA, "system", "user", OkDto.class).ok).isTrue();
        server.verify();
    }
}
