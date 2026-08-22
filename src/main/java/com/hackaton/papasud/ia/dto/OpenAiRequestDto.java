package com.hackaton.papasud.ia.dto;

import java.util.List;

public record OpenAiRequestDto(
    String model,
    List<Message> messages
) {
    public record Message(String role, String content) {}
}
