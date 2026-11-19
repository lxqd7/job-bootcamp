package com.jobbootcamp.course.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("play_session")
public class PlaySession {

    @TableId(type = IdType.INPUT)
    private String id;
    private Long userId;
    private Long authSessionId;
    private Long courseId;
    private Long lessonId;
    private Long mediaId;
    private Long catalogVersion;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private String mode;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getAuthSessionId() { return authSessionId; }
    public void setAuthSessionId(Long authSessionId) { this.authSessionId = authSessionId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getLessonId() { return lessonId; }
    public void setLessonId(Long lessonId) { this.lessonId = lessonId; }
    public Long getMediaId() { return mediaId; }
    public void setMediaId(Long mediaId) { this.mediaId = mediaId; }
    public Long getCatalogVersion() { return catalogVersion; }
    public void setCatalogVersion(Long catalogVersion) { this.catalogVersion = catalogVersion; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}