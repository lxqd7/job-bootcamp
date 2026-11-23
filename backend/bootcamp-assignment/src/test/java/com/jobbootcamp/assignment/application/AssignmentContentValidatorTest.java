package com.jobbootcamp.assignment.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobbootcamp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssignmentContentValidatorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final AssignmentContentValidator validator = new AssignmentContentValidator();

    @Test
    void benefitChoiceAllowsExactlyOneQuestion() throws Exception {
        JsonNode publicBody = JSON.readTree("""
                {"questions":[{"id":"q1","type":"SINGLE","body":"福利题","score":10,
                  "options":[{"id":"A","text":"A"},{"id":"B","text":"B"}]}]}
                """);
        JsonNode answerBody = JSON.readTree("""
                {"answers":{"q1":["A"]},"analysis":{"q1":"解析"}}
                """);
        assertDoesNotThrow(() -> validator.validate("BENEFIT_CHOICE", publicBody, answerBody));
    }

    @Test
    void benefitChoiceRejectsTwoQuestions() throws Exception {
        JsonNode publicBody = JSON.readTree("""
                {"questions":[
                  {"id":"q1","type":"SINGLE","body":"题1","score":10,"options":[{"id":"A","text":"A"},{"id":"B","text":"B"}]},
                  {"id":"q2","type":"SINGLE","body":"题2","score":10,"options":[{"id":"A","text":"A"},{"id":"B","text":"B"}]}
                ]}
                """);
        JsonNode answerBody = JSON.readTree("""
                {"answers":{"q1":["A"],"q2":["B"]},"analysis":{}}
                """);
        assertThrows(BusinessException.class,
                () -> validator.validate("BENEFIT_CHOICE", publicBody, answerBody));
    }

    @Test
    void benefitCodeUsesSameCodeRules() throws Exception {
        JsonNode publicBody = JSON.readTree("""
                {"scenario":"s","requirements":"r","segments":[{"slotId":"s1","initialCode":"//"}]}
                """);
        JsonNode answerBody = JSON.readTree("""
                {"referenceBySlot":{"s1":"return 1;"},"rubric":"ok"}
                """);
        assertDoesNotThrow(() -> validator.validate("BENEFIT_CODE", publicBody, answerBody));
    }
}