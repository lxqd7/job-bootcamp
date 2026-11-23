package com.jobbootcamp.interaction.domain.dto;

import java.util.List;

public record ThreadView(
        QuestionView question,
        List<AnswerView> answers,
        long answersTotal,
        long page,
        long pageSize
) {
}