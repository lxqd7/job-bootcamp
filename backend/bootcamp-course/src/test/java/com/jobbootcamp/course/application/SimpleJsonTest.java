package com.jobbootcamp.course.application;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleJsonTest {

    @Test
    void writesNestedMapsListsAndEscapesText() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "a\"b\\c\n");
        payload.put("count", 3);
        payload.put("free", true);
        payload.put("members", List.of(Map.of("courseId", 1L), Map.of("courseId", 2L)));

        String json = SimpleJson.write(payload);

        assertThat(json).isEqualTo(
                "{\"title\":\"a\\\"b\\\\c\\n\",\"count\":3,\"free\":true,"
                        + "\"members\":[{\"courseId\":1},{\"courseId\":2}]}");
    }
}