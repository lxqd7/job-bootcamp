package com.jobbootcamp.trade.application;

import com.jobbootcamp.trade.infrastructure.PriceQuoteMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class QuoteStatusService {

    private final PriceQuoteMapper quoteMapper;

    public QuoteStatusService(PriceQuoteMapper quoteMapper) {
        this.quoteMapper = quoteMapper;
    }

    /**
     * 报价失效是下单事务的外部事实；独立提交，避免下单回滚把 EXPIRED/STALE 证据一起抹掉。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mark(String quoteId, String status) {
        quoteMapper.updateStatusFromReady(quoteId, status, LocalDateTime.now(ZoneOffset.UTC));
    }
}