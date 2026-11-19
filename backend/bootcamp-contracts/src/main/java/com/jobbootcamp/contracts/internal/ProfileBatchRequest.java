package com.jobbootcamp.contracts.internal;

import java.util.List;

public record ProfileBatchRequest(List<String> userIds) {
}