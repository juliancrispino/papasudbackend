package com.hackaton.papasud.ia.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiRequestDto(
    String model,
    List<Message> messages,
    Double temperature,
    Map<String, Object> response_format,
    String reasoning_effort,
    Integer max_completion_tokens,
    Boolean include_reasoning
) {
    public OpenAiRequestDto(String model, List<Message> messages, Double temperature, Map<String, Object> response_format) {
        this(model, messages, temperature, response_format, null, null, null);
    }

    public record Message(String role, String content) {}
}
