package com.jobbootcamp.marketing.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jobbootcamp.marketing.domain.CouponRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CouponRuleMapper extends BaseMapper<CouponRule> {

    @Select("SELECT * FROM coupon_rule WHERE id = #{id} FOR UPDATE")
    CouponRule selectForUpdate(@Param("id") Long id);

    @Update("UPDATE coupon_rule SET issued = TRUE, updated_at = #{updatedAt} WHERE id = #{id}")
    int markIssued(@Param("id") Long id, @Param("updatedAt") java.time.LocalDateTime updatedAt);

    @Select("SELECT COALESCE(MAX(version_no), 0) FROM coupon_rule WHERE rule_key = #{ruleKey}")
    int maxVersion(@Param("ruleKey") String ruleKey);
}