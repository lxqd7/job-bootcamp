package com.jobbootcamp.course.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.course.domain.Course;
import com.jobbootcamp.course.domain.CourseVersion;
import com.jobbootcamp.course.domain.FreeClaimTicket;
import com.jobbootcamp.course.domain.OutboxEvent;
import com.jobbootcamp.course.domain.dto.ClaimView;
import com.jobbootcamp.course.infrastructure.CourseMapper;
import com.jobbootcamp.course.infrastructure.CourseVersionMapper;
import com.jobbootcamp.course.infrastructure.FreeClaimTicketMapper;
import com.jobbootcamp.course.infrastructure.OutboxEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 免费领取的受理端。课程行锁保证“当时是否免费”的判断与受理顺序一致；
 * 领取票据先落库再写 outbox，学习侧只消费已持久化的事实，重复请求返回原票据。
 */
@Service
public class FreeClaimService {

    private static final Logger log = LoggerFactory.getLogger(FreeClaimService.class);
    private static final String PROCESSING = "PROCESSING";
    private static final String FULFILLED = "FULFILLED";
    private static final String CLAIM_EVENT = "course.free-claim-accepted.v1";

    private final CourseMapper courseMapper;
    private final CourseVersionMapper versionMapper;
    private final FreeClaimTicketMapper ticketMapper;
    private final OutboxEventMapper outboxEventMapper;
    private final TransactionTemplate transactionTemplate;

    public FreeClaimService(CourseMapper courseMapper,
                            CourseVersionMapper versionMapper,
                            FreeClaimTicketMapper ticketMapper,
                            OutboxEventMapper outboxEventMapper,
                            PlatformTransactionManager transactionManager) {
        this.courseMapper = courseMapper;
        this.versionMapper = versionMapper;
        this.ticketMapper = ticketMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ClaimView claim(long userId, long courseId) {
        FreeClaimTicket ticket = transactionTemplate.execute(status -> claimInTransaction(userId, courseId));
        return toView(ticket);
    }

    private FreeClaimTicket claimInTransaction(long userId, long courseId) {
        // 与改价操作串行化，确保“领取时免费”在同一课程版本上判定。
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
        // acceptedAt 是后续学习授予和积分的业务时间，不能用消费者处理时间替代。
        writeClaimEvent(ticket, now);
        return ticket;
    }

    private void writeClaimEvent(FreeClaimTicket ticket, LocalDateTime now) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setType(CLAIM_EVENT);
        event.setAggregateId(ticket.getClaimId());
        event.setAggregateVersion(1L);
        event.setOccurredAt(now);
        event.setPayload("{\"claimId\":\"" + ticket.getClaimId()
                + "\",\"userId\":\"" + ticket.getUserId()
                + "\",\"courseId\":\"" + ticket.getCourseId()
                + "\",\"courseVersion\":\"" + ticket.getCourseVersion()
                + "\",\"acceptedAt\":\"" + ticket.getAcceptedAt().toInstant(ZoneOffset.UTC) + "\"}");
        event.setStatus("NEW");
        event.setAttempt(0);
        event.setNextRetryAt(now);
        outboxEventMapper.insert(event);
    }

    public ClaimView getClaim(long userId, String claimId) {
        FreeClaimTicket ticket = ticketMapper.selectById(claimId);
        if (ticket == null || !ticket.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "领取记录不存在");
        }
        return toView(ticket);
    }

    public void markFulfilled(String claimId, String sourceId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int updated = ticketMapper.update(null, Wrappers.<FreeClaimTicket>lambdaUpdate()
                .set(FreeClaimTicket::getStatus, FULFILLED)
                .set(FreeClaimTicket::getSourceId, Long.valueOf(sourceId))
                .set(FreeClaimTicket::getUpdatedAt, now)
                .setSql("revision = revision + 1")
                .eq(FreeClaimTicket::getClaimId, claimId)
                .eq(FreeClaimTicket::getStatus, PROCESSING));
        if (updated == 0) {
            log.debug("free claim completion already applied claimId={}", claimId);
        }
    }

    private ClaimView toView(FreeClaimTicket ticket) {
        return new ClaimView(ticket.getClaimId(), String.valueOf(ticket.getCourseId()),
                ticket.getStatus(), ticket.getSourceId() == null ? null : String.valueOf(ticket.getSourceId()));
    }
}