package com.jobbootcamp.course.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.FreeClaimCommand;
import com.jobbootcamp.contracts.internal.FreeClaimResult;
import com.jobbootcamp.course.domain.Course;
import com.jobbootcamp.course.domain.CourseVersion;
import com.jobbootcamp.course.domain.FreeClaimTicket;
import com.jobbootcamp.course.domain.dto.ClaimView;
import com.jobbootcamp.course.infrastructure.CourseMapper;
import com.jobbootcamp.course.infrastructure.CourseVersionMapper;
import com.jobbootcamp.course.infrastructure.FreeClaimTicketMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class FreeClaimService {

    private static final Logger log = LoggerFactory.getLogger(FreeClaimService.class);
    private static final String PROCESSING = "PROCESSING";
    private static final String FULFILLED = "FULFILLED";

    private final CourseMapper courseMapper;
    private final CourseVersionMapper versionMapper;
    private final FreeClaimTicketMapper ticketMapper;
    private final LearningClaimClient learningClient;
    private final TransactionTemplate transactionTemplate;

    public FreeClaimService(CourseMapper courseMapper,
                            CourseVersionMapper versionMapper,
                            FreeClaimTicketMapper ticketMapper,
                            LearningClaimClient learningClient,
                            PlatformTransactionManager transactionManager) {
        this.courseMapper = courseMapper;
        this.versionMapper = versionMapper;
        this.ticketMapper = ticketMapper;
        this.learningClient = learningClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ClaimView claim(long userId, long courseId) {
        FreeClaimTicket ticket = transactionTemplate.execute(status -> claimInTransaction(userId, courseId));
        return fulfill(ticket);
    }

    private FreeClaimTicket claimInTransaction(long userId, long courseId) {
        Course course = courseMapper.selectForUpdate(courseId);
        if (course == null || !"ONLINE".equals(course.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在或未上架");
        }
        if (!Boolean.TRUE.equals(course.getIsFree())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "课程当前不是免费课程");
        }
        FreeClaimTicket existing = ticketMapper.selectOne(Wrappers.<FreeClaimTicket>lambdaQuery()
                .eq(FreeClaimTicket::getUserId, userId)
                .eq(FreeClaimTicket::getCourseId, courseId)
                .in(FreeClaimTicket::getStatus, PROCESSING, FULFILLED)
                .orderByDesc(FreeClaimTicket::getCreatedAt)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        CourseVersion version = versionMapper.selectOne(Wrappers.<CourseVersion>lambdaQuery()
                .eq(CourseVersion::getCourseId, courseId)
                .orderByDesc(CourseVersion::getVersionNo)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        FreeClaimTicket ticket = new FreeClaimTicket();
        ticket.setClaimId(UUID.randomUUID().toString());
        ticket.setUserId(userId);
        ticket.setCourseId(courseId);
        ticket.setCourseVersion(version == null ? 0 : version.getVersionNo());
        ticket.setStatus(PROCESSING);
        ticket.setAcceptedAt(now);
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        ticket.setRevision(0L);
        ticketMapper.insert(ticket);
        return ticket;
    }

    public ClaimView getClaim(long userId, String claimId) {
        FreeClaimTicket ticket = ticketMapper.selectById(claimId);
        if (ticket == null || !ticket.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "领取记录不存在");
        }
        return fulfill(ticket);
    }

    public void retryPending() {
        List<FreeClaimTicket> pending = ticketMapper.selectProcessing(20);
        for (FreeClaimTicket ticket : pending) {
            try {
                fulfill(ticket);
            } catch (RuntimeException e) {
                log.warn("free claim retry failed claimId={}: {}", ticket.getClaimId(), e.getMessage());
            }
        }
    }

    private ClaimView fulfill(FreeClaimTicket ticket) {
        if (FULFILLED.equals(ticket.getStatus())) {
            return toView(ticket);
        }
        try {
            FreeClaimResult result = learningClient.grant(new FreeClaimCommand(
                    ticket.getClaimId(), ticket.getUserId(), ticket.getCourseId(),
                    ticket.getCourseVersion(), ticket.getAcceptedAt().toInstant(ZoneOffset.UTC)));
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            int updated = ticketMapper.update(null, Wrappers.<FreeClaimTicket>lambdaUpdate()
                    .set(FreeClaimTicket::getStatus, FULFILLED)
                    .set(FreeClaimTicket::getSourceId, Long.valueOf(result.sourceId()))
                    .set(FreeClaimTicket::getUpdatedAt, now)
                    .setSql("revision = revision + 1")
                    .eq(FreeClaimTicket::getClaimId, ticket.getClaimId())
                    .eq(FreeClaimTicket::getStatus, PROCESSING));
            if (updated > 0) {
                ticket = ticketMapper.selectById(ticket.getClaimId());
            }
        } catch (RuntimeException e) {
            log.warn("free claim fulfillment deferred claimId={}: {}", ticket.getClaimId(), e.getMessage());
        }
        return toView(ticketMapper.selectById(ticket.getClaimId()));
    }

    private ClaimView toView(FreeClaimTicket ticket) {
        return new ClaimView(ticket.getClaimId(), String.valueOf(ticket.getCourseId()),
                ticket.getStatus(), ticket.getSourceId() == null ? null : String.valueOf(ticket.getSourceId()));
    }
}