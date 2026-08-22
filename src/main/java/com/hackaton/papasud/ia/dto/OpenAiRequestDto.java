package com.hackaton.papasud.ia.dto;

import java.util.List;
import java.util.Map;

public record OpenAiRequestDto(
    String model,
    List<Message> messages,
    Double temperature,
    Map<String, Object> response_format
) {
    public record Message(String role, String content) {}
}
