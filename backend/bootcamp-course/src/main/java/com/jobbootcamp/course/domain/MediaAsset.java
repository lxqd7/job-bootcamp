package com.jobbootcamp.course.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("media_asset")
public class MediaAsset {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String provider;
    private String appId;
    private String fileId;
    private String demoPath;
    private Long durationMs;
    private String status;
    private Boolean detached;
    private String origin;
    private LocalDateTime verifiedAt;
    private String oldId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long revision;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getDemoPath() { return demoPath; }
    public void setDemoPath(String demoPath) { this.demoPath = demoPath; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getDetached() { return detached; }
    public void setDetached(Boolean detached) { this.detached = detached; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
    public String getOldId() { return oldId; }
    public void setOldId(String oldId) { this.oldId = oldId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getRevision() { return revision; }
    public void setRevision(Long revision) { this.revision = revision; }
}