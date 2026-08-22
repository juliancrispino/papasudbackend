package com.hackaton.papasud.ia.dto;

import java.util.List;

public record OpenAiResponseDto(
    List<Choice> choices
) {
    public record Choice(Message message) {}
    public record Message(String content) {}
}
