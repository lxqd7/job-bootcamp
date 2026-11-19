package com.jobbootcamp.course.domain;

public class MediaImportPlanItem {

    private String batchId;
    private String oldId;
    private String appId;
    private String fileId;
    private String inputHash;
    private String action;

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public String getOldId() { return oldId; }
    public void setOldId(String oldId) { this.oldId = oldId; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getInputHash() { return inputHash; }
    public void setInputHash(String inputHash) { this.inputHash = inputHash; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
}