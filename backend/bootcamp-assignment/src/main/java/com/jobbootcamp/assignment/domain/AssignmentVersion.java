package com.jobbootcamp.assignment.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("assignment_version")
public class AssignmentVersion {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assignmentId;
    private Integer versionNo;
    private String publicBody;
    private String answerBody;
    private LocalDateTime publishedAt;
    private String promptVersion;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAssignmentId() { return assignmentId; }
    public void setAssignmentId(Long assignmentId) { this.assignmentId = assignmentId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getPublicBody() { return publicBody; }
    public void setPublicBody(String publicBody) { this.publicBody = publicBody; }
    public String getAnswerBody() { return answerBody; }
    public void setAnswerBody(String answerBody) { this.answerBody = answerBody; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
}