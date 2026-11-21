package com.jobbootcamp.marketing.domain.dto;

public record ActivityRequest(String name, String openAt, String closeAt, Long revision) {
}