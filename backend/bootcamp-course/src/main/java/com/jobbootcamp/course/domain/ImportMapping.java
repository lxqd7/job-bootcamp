package com.jobbootcamp.course.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("import_mapping")
public class ImportMapping {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchId;
    private String entityType;
    private String oldId;
    private Long newId;
    private String inputHash;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getOldId() { return oldId; }
    public void setOldId(String oldId) { this.oldId = oldId; }
    public Long getNewId() { return newId; }
    public void setNewId(Long newId) { this.newId = newId; }
    public String getInputHash() { return inputHash; }
    public void setInputHash(String inputHash) { this.inputHash = inputHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}