package com.jobbootcamp.learning.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("course_grant")
public class CourseGrant {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long courseId;
    private Long sourceId;
    private LocalDateTime grantedAt;
    private Long grantSequence;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public LocalDateTime getGrantedAt() { return grantedAt; }
    public void setGrantedAt(LocalDateTime grantedAt) { this.grantedAt = grantedAt; }
    public Long getGrantSequence() { return grantSequence; }
    public void setGrantSequence(Long grantSequence) { this.grantSequence = grantSequence; }
}