package com.jobbootcamp.marketing.application;

import com.jobbootcamp.contracts.internal.ProfileBatchItem;
import com.jobbootcamp.marketing.infrastructure.StudentProfileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class ProfileSyncService {

    private static final Logger log = LoggerFactory.getLogger(ProfileSyncService.class);
    private static final int BATCH_SIZE = 100;

    private final ProfileClient profileClient;
    private final StudentProfileMapper profileMapper;

    public ProfileSyncService(ProfileClient profileClient, StudentProfileMapper profileMapper) {
        this.profileClient = profileClient;
        this.profileMapper = profileMapper;
    }

    public void sync(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (int from = 0; from < userIds.size(); from += BATCH_SIZE) {
            List<Long> chunk = userIds.subList(from, Math.min(from + BATCH_SIZE, userIds.size()));
            try {
                List<ProfileBatchItem> items = profileClient.fetch(chunk);
                for (ProfileBatchItem item : items) {
                    profileMapper.upsert(Long.valueOf(item.id()), item.nickname(), item.role(),
                            item.revision(), now);
                }
            } catch (RuntimeException e) {
                log.warn("profile sync failed for {} users: {}", chunk.size(), e.getMessage());
            }
        }
    }
}