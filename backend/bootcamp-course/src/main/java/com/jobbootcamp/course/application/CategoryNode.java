package com.jobbootcamp.course.application;

import java.util.List;

public record CategoryNode(String id, String name, List<CategoryNode> children) {
}