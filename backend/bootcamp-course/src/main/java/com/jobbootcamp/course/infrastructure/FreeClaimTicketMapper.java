package com.jobbootcamp.course.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.course.domain.FreeClaimTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FreeClaimTicketMapper extends BaseMapper<FreeClaimTicket> {

    @Select("""
            SELECT * FROM free_claim_ticket
            WHERE status = 'PROCESSING'
            ORDER BY updated_at
            LIMIT #{limit}
            """)
    List<FreeClaimTicket> selectProcessing(@Param("limit") int limit);
}