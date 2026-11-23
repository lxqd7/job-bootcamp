package com.jobbootcamp.assignment.application;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiReviewResponseParserTest {

    private final AiReviewResponseParser parser = new AiReviewResponseParser();
    private final Set<String> slots = Set.of("s1");

    @Test
    void parsesStrictReviewJson() throws Exception {
        AiReviewEngine.ReviewFeedback feedback = parser.parse("""
                {"verdict":"PARTIAL","summary":"存在一个问题","issues":[
                  {"slotId":"s1","evidence":"return 0","reason":"缺少负数判断","suggestion":"增加判断"}
                ],"suggestions":["补充边界"],"limitations":["未执行代码"]}
                """, slots);
        assertEquals("PARTIAL", feedback.verdict());
        assertEquals(1, feedback.issues().size());
    }

    @Test
    void allowsSatisfiedWithNoIssues() throws Exception {
        AiReviewEngine.ReviewFeedback feedback = parser.parse("""
                {"verdict":"SATISFIED","summary":"符合要求","issues":[],
                 "suggestions":[],"limitations":["未执行代码"]}
                """, slots);
        assertEquals("SATISFIED", feedback.verdict());
        assertEquals(0, feedback.issues().size());
    }

    @Test
    void rejectsUnknownTopLevelField() {
        assertThrows(AiReviewResponseParser.FormatException.class, () -> parser.parse("""
                {"verdict":"SATISFIED","summary":"ok","issues":[],
                 "suggestions":[],"limitations":[],"score":99}
                """, slots));
    }

    @Test
    void rejectsUnknownSlot() {
        assertThrows(AiReviewResponseParser.FormatException.class, () -> parser.parse("""
                {"verdict":"PARTIAL","summary":"问题","issues":[
                  {"slotId":"other","evidence":"x","reason":"y","suggestion":"z"}
                ],"suggestions":[],"limitations":[]}
                """, slots));
    }

    @Test
    void rejectsMarkdownWrappedJson() {
        assertThrows(AiReviewResponseParser.FormatException.class, () -> parser.parse("""
                ```json
                {"verdict":"SATISFIED","summary":"ok","issues":[],"suggestions":[],"limitations":[]}
                ```
                """, slots));
    }

    @Test
    void rejectsNonSatisfiedWithoutIssue() {
        assertThrows(AiReviewResponseParser.FormatException.class, () -> parser.parse("""
                {"verdict":"NEEDS_CHANGE","summary":"需要修改","issues":[],
                 "suggestions":[],"limitations":[]}
                """, slots));
    }
}