package com.jobbootcamp.course.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.course.domain.ImportMapping;
import com.jobbootcamp.course.domain.MediaAsset;
import com.jobbootcamp.course.domain.MediaImportPlanItem;
import com.jobbootcamp.course.domain.MediaOperation;
import com.jobbootcamp.course.domain.PlaySession;
import com.jobbootcamp.course.domain.dto.DemoImportRequest;
import com.jobbootcamp.course.domain.dto.MediaImportItem;
import com.jobbootcamp.course.domain.dto.MediaImportPreviewRequest;
import com.jobbootcamp.course.domain.dto.MediaImportPreviewResponse;
import com.jobbootcamp.course.domain.dto.MediaImportRequest;
import com.jobbootcamp.course.domain.dto.MediaImportResponse;
import com.jobbootcamp.course.domain.dto.MediaImportResultItem;
import com.jobbootcamp.course.domain.dto.MediaItemView;
import com.jobbootcamp.course.domain.dto.MediaOperationView;
import com.jobbootcamp.course.domain.dto.MediaPreviewItem;
import com.jobbootcamp.course.domain.dto.PageResponse;
import com.jobbootcamp.course.infrastructure.ImportMappingMapper;
import com.jobbootcamp.course.infrastructure.MediaAssetMapper;
import com.jobbootcamp.course.infrastructure.MediaImportPlanMapper;
import com.jobbootcamp.course.infrastructure.MediaOperationMapper;
import com.jobbootcamp.course.infrastructure.PlaySessionMapper;
import com.jobbootcamp.course.media.DemoMediaAdapter;
import com.jobbootcamp.course.media.MediaAuthorization;
import com.jobbootcamp.course.media.MediaPort;
import com.jobbootcamp.course.media.MediaRef;
import com.jobbootcamp.course.media.MediaVerification;
import com.jobbootcamp.course.media.TencentVodAdapter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MediaService {

    private static final String ENTITY_MEDIA = "MEDIA";

    private final MediaAssetMapper mediaMapper;
    private final MediaOperationMapper operationMapper;
    private final MediaImportPlanMapper planMapper;
    private final ImportMappingMapper importMappingMapper;
    private final PlaySessionMapper playSessionMapper;
    private final DemoMediaAdapter demoMediaAdapter;
    private final ObjectProvider<TencentVodAdapter> tencentVodAdapterProvider;
    private final long previewTtlSeconds;
    private final boolean demoImportEnabled;

    public MediaService(MediaAssetMapper mediaMapper,
                        MediaOperationMapper operationMapper,
                        MediaImportPlanMapper planMapper,
                        ImportMappingMapper importMappingMapper,
                        PlaySessionMapper playSessionMapper,
                        DemoMediaAdapter demoMediaAdapter,
                        ObjectProvider<TencentVodAdapter> tencentVodAdapterProvider,
                        @Value("${app.media.preview-ttl-seconds:120}") long previewTtlSeconds,
                        @Value("${app.media.demo-import-enabled:true}") boolean demoImportEnabled) {
        this.mediaMapper = mediaMapper;
        this.operationMapper = operationMapper;
        this.planMapper = planMapper;
        this.importMappingMapper = importMappingMapper;
        this.playSessionMapper = playSessionMapper;
        this.demoMediaAdapter = demoMediaAdapter;
        this.tencentVodAdapterProvider = tencentVodAdapterProvider;
        this.previewTtlSeconds = previewTtlSeconds;
        this.demoImportEnabled = demoImportEnabled;
    }

    public PageResponse<MediaItemView> list(String status, String provider, int pageNumber, int pageSize) {
        var query = Wrappers.<MediaAsset>lambdaQuery();
        if (status != null && !status.isBlank()) {
            query.eq(MediaAsset::getStatus, status);
        }
        if (provider != null && !provider.isBlank()) {
            query.eq(MediaAsset::getProvider, provider);
        }
        query.orderByDesc(MediaAsset::getId);
        Page<MediaAsset> result = mediaMapper.selectPage(
                new Page<>(Math.max(pageNumber, 1), Math.min(Math.max(pageSize, 1), 100)), query);
        return new PageResponse<>(result.getRecords().stream().map(this::toView).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Transactional
    public MediaImportPreviewResponse previewImport(MediaImportPreviewRequest request) {
        String inputJson = SimpleJson.write(Map.of(
                "batchId", request.batchId(),
                "items", request.items().stream().map(item -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("oldId", item.oldId());
                    map.put("appId", item.appId());
                    map.put("fileId", item.fileId());
                    map.put("courseMapping", item.courseMapping());
                    return map;
                }).toList()));
        String inputHash = sha256(inputJson);
        String operationKey = operationKey("preview", request.batchId());
        MediaOperation existing = operationMapper.selectOne(Wrappers.<MediaOperation>lambdaQuery()
                .eq(MediaOperation::getOperationKey, operationKey));
        if (existing != null) {
            if (!existing.getInputHash().equals(inputHash)) {
                throw new BusinessException(ErrorCode.CONFLICT, "同一批次 ID 的输入内容已变化，请重新使用新的 batchId");
            }
            return previewFromPlan(operationKey, request.batchId(), inputHash);
        }

        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        List<MediaPreviewItem> items = new ArrayList<>();
        for (MediaImportItem item : request.items()) {
            if (!seen.add(item.oldId())) {
                duplicates.add(item.oldId());
                items.add(new MediaPreviewItem(item.oldId(), "DUPLICATE", null, "oldId 在本次输入中重复"));
                planMapper.insertItem(plan(operationKey, item, inputHash, "DUPLICATE"));
                continue;
            }
            ImportMapping mapping = importMappingMapper.selectOne(Wrappers.<ImportMapping>lambdaQuery()
                    .eq(ImportMapping::getEntityType, ENTITY_MEDIA)
                    .eq(ImportMapping::getOldId, item.oldId()));
            if (mapping != null) {
                items.add(new MediaPreviewItem(item.oldId(), "SKIP_EXISTING",
                        String.valueOf(mapping.getNewId()), "已经导入过该 oldId"));
                planMapper.insertItem(plan(operationKey, item, inputHash, "SKIP_EXISTING"));
                continue;
            }
            MediaAsset existingAsset = findVod(item.appId(), item.fileId());
            if (existingAsset != null) {
                items.add(new MediaPreviewItem(item.oldId(), "REUSE_EXISTING",
                        String.valueOf(existingAsset.getId()), "相同 appId/fileId 已存在"));
                planMapper.insertItem(plan(operationKey, item, inputHash, "REUSE_EXISTING"));
                continue;
            }
            items.add(new MediaPreviewItem(item.oldId(), "CREATE", null, null));
            planMapper.insertItem(plan(operationKey, item, inputHash, "CREATE"));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", request.batchId());
        result.put("inputHash", inputHash);
        result.put("duplicates", duplicates);
        saveOperation(operationKey, "PREVIEW", inputJson, inputHash, SimpleJson.write(result), "SUCCESS");
        return new MediaImportPreviewResponse(operationKey, request.batchId(), inputHash, items, duplicates, List.of());
    }

    @Transactional
    public MediaImportResponse importMedia(MediaImportRequest request) {
        String previewKey = operationKey("preview", request.batchId());
        MediaOperation preview = operationMapper.selectOne(Wrappers.<MediaOperation>lambdaQuery()
                .eq(MediaOperation::getOperationKey, previewKey));
        if (preview == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请先执行 import-preview");
        }
        if (!preview.getInputHash().equals(request.inputHash())) {
            throw new BusinessException(ErrorCode.CONFLICT, "inputHash 与预览不一致");
        }
        List<MediaImportPlanItem> plan = planMapper.selectByBatch(previewKey);
        Map<String, MediaImportPlanItem> byOldId = new HashMap<>();
        for (MediaImportPlanItem item : plan) {
            byOldId.put(item.getOldId(), item);
        }
        for (String oldId : request.selectedOldIds()) {
            if (!byOldId.containsKey(oldId)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "selectedOldIds 未包含在预览中：" + oldId);
            }
        }
        String importKey = operationKey("import", request.batchId());
        MediaOperation existingImport = operationMapper.selectOne(Wrappers.<MediaOperation>lambdaQuery()
                .eq(MediaOperation::getOperationKey, importKey));
        if (existingImport != null) {
            return new MediaImportResponse(importKey, request.batchId(), rebuildImportResults(request.selectedOldIds()));
        }

        List<MediaImportResultItem> results = new ArrayList<>();
        for (String oldId : request.selectedOldIds()) {
            MediaImportPlanItem planItem = byOldId.get(oldId);
            ImportMapping mapping = importMappingMapper.selectOne(Wrappers.<ImportMapping>lambdaQuery()
                    .eq(ImportMapping::getEntityType, ENTITY_MEDIA)
                    .eq(ImportMapping::getOldId, oldId));
            MediaAsset asset;
            if (mapping != null) {
                asset = mediaMapper.selectById(mapping.getNewId());
                results.add(new MediaImportResultItem(oldId, String.valueOf(mapping.getNewId()),
                        asset == null ? "MISSING" : asset.getStatus(), "沿用已有导入映射"));
                continue;
            }
            asset = findVod(planItem.getAppId(), planItem.getFileId());
            if (asset == null) {
                asset = createVodAsset(planItem);
            }
            ImportMapping newMapping = new ImportMapping();
            newMapping.setBatchId(request.batchId());
            newMapping.setEntityType(ENTITY_MEDIA);
            newMapping.setOldId(oldId);
            newMapping.setNewId(asset.getId());
            newMapping.setInputHash(request.inputHash());
            newMapping.setCreatedAt(utcNow());
            importMappingMapper.insert(newMapping);
            MediaVerification verification = verifyAsset(asset);
            applyVerification(asset, verification);
            results.add(new MediaImportResultItem(oldId, String.valueOf(asset.getId()),
                    asset.getStatus(), verification.errorMessage()));
        }
        String resultJson = SimpleJson.write(Map.of(
                "batchId", request.batchId(),
                "items", results.stream().map(item -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("oldId", item.oldId());
                    map.put("mediaId", item.mediaId());
                    map.put("status", item.status());
                    map.put("message", item.message());
                    return map;
                }).toList()));
        saveOperation(importKey, "IMPORT",
                SimpleJson.write(Map.of("batchId", request.batchId(), "selectedOldIds", request.selectedOldIds())),
                request.inputHash(), resultJson, "SUCCESS");
        return new MediaImportResponse(importKey, request.batchId(), results);
    }

    @Transactional
    public MediaItemView demoImport(DemoImportRequest request) {
        if (!demoImportEnabled) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前环境未启用 DEMO 媒体导入");
        }
        var path = demoMediaAdapter.resolve(request.relativePath());
        if (path == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "相对路径不能离开 DEMO_MEDIA_ROOT");
        }
        String normalizedPath = request.relativePath().replace('\\', '/');
        MediaAsset existing = mediaMapper.selectOne(Wrappers.<MediaAsset>lambdaQuery()
                .eq(MediaAsset::getProvider, "DEMO")
                .eq(MediaAsset::getDemoPath, normalizedPath));
        if (existing != null) {
            return toView(existing);
        }
        MediaVerification verification = demoMediaAdapter.verify(new MediaRef(null, "DEMO",
                null, null, request.relativePath(), null));
        if (!verification.available()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    verification.errorCode() + ": " + verification.errorMessage());
        }
        LocalDateTime now = utcNow();
        MediaAsset asset = new MediaAsset();
        asset.setProvider("DEMO");
        asset.setDemoPath(normalizedPath);
        asset.setDurationMs(verification.durationMs());
        asset.setStatus("AVAILABLE");
        asset.setDetached(false);
        asset.setOrigin("DEMO");
        asset.setVerifiedAt(now);
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        asset.setRevision(0L);
        mediaMapper.insert(asset);
        return toView(asset);
    }

    @Transactional
    public String verify(Long id) {
        MediaAsset asset = requireMedia(id);
        MediaVerification verification = verifyAsset(asset);
        applyVerification(asset, verification);
        String operationKey = UUID.randomUUID().toString();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mediaId", String.valueOf(id));
        result.put("status", asset.getStatus());
        result.put("durationMs", asset.getDurationMs());
        result.put("errorCode", verification.errorCode());
        result.put("errorMessage", verification.errorMessage());
        saveOperation(operationKey, "VERIFY", SimpleJson.write(Map.of("mediaId", String.valueOf(id))),
                sha256("verify:" + id), SimpleJson.write(result),
                verification.available() ? "SUCCESS" : "FAILED");
        return operationKey;
    }

    public MediaOperationView operation(String operationKey) {
        MediaOperation operation = operationMapper.selectOne(Wrappers.<MediaOperation>lambdaQuery()
                .eq(MediaOperation::getOperationKey, operationKey));
        if (operation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "操作不存在");
        }
        return new MediaOperationView(operation.getOperationKey(), operation.getKind(),
                operation.getState(), operation.getInputHash(), operation.getResultJson());
    }

    @Transactional
    public MediaAuthorization previewAuthorization(Long mediaId, Long userId, Long authSessionId) {
        MediaAsset asset = requireMedia(mediaId);
        if (!"AVAILABLE".equals(asset.getStatus()) || Boolean.TRUE.equals(asset.getDetached())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "媒体未就绪或已解除引用");
        }
        Instant expiresAt = Instant.now().plusSeconds(previewTtlSeconds);
        String playSessionId = UUID.randomUUID().toString();
        PlaySession session = new PlaySession();
        session.setId(playSessionId);
        session.setUserId(userId);
        session.setAuthSessionId(authSessionId);
        session.setCourseId(0L);
        session.setLessonId(0L);
        session.setMediaId(asset.getId());
        session.setCatalogVersion(0L);
        session.setIssuedAt(LocalDateTime.now(ZoneOffset.UTC));
        session.setExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        session.setMode("ADMIN_PREVIEW");
        playSessionMapper.insert(session);
        return mediaPort(asset).authorize(mediaRef(asset), userId, authSessionId, playSessionId, expiresAt);
    }

    @Transactional
    public MediaItemView detach(Long id, Long revision) {
        MediaAsset asset = requireMedia(id);
        int updated = mediaMapper.update(null, Wrappers.<MediaAsset>lambdaUpdate()
                .set(MediaAsset::getDetached, true)
                .set(MediaAsset::getUpdatedAt, utcNow())
                .setSql("revision = revision + 1")
                .eq(MediaAsset::getId, id)
                .eq(MediaAsset::getRevision, revision));
        if (updated == 0) {
            Long currentRevision = mediaMapper.selectById(id).getRevision();
            throw new BusinessException(ErrorCode.CONFLICT, "媒体已在其他位置更新，请刷新后重试",
                    new com.jobbootcamp.common.web.ErrorData(null, currentRevision, null));
        }
        return toView(mediaMapper.selectById(id));
    }

    public long availableCount(List<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return 0;
        }
        Long count = mediaMapper.selectCount(Wrappers.<MediaAsset>lambdaQuery()
                .in(MediaAsset::getId, mediaIds)
                .eq(MediaAsset::getStatus, "AVAILABLE")
                .eq(MediaAsset::getDetached, false));
        return count == null ? 0 : count;
    }

    private MediaVerification verifyAsset(MediaAsset asset) {
        try {
            return mediaPort(asset).verify(mediaRef(asset));
        } catch (RuntimeException e) {
            return MediaVerification.fail("MEDIA_PORT_ERROR", "媒体适配器异常：" + e.getMessage());
        }
    }

    private void applyVerification(MediaAsset asset, MediaVerification verification) {
        String status;
        if (verification.available()) {
            status = "AVAILABLE";
        } else if (verification.errorCode() != null
                && (verification.errorCode().contains("MISSING") || verification.errorCode().contains("NOT_FOUND"))) {
            status = "MISSING";
        } else {
            status = "ERROR";
        }
        asset.setStatus(status);
        asset.setDurationMs(verification.durationMs());
        asset.setVerifiedAt(utcNow());
        asset.setUpdatedAt(utcNow());
        asset.setRevision((asset.getRevision() == null ? 0 : asset.getRevision()) + 1);
        mediaMapper.updateById(asset);
    }

    private MediaPort mediaPort(MediaAsset asset) {
        if ("DEMO".equals(asset.getProvider())) {
            return demoMediaAdapter;
        }
        TencentVodAdapter adapter = tencentVodAdapterProvider.getIfAvailable();
        if (adapter == null) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "VOD 未配置，无法播放或校验该媒体");
        }
        return adapter;
    }

    private MediaRef mediaRef(MediaAsset asset) {
        return new MediaRef(asset.getId(), asset.getProvider(), asset.getAppId(), asset.getFileId(),
                asset.getDemoPath(), asset.getDurationMs());
    }

    private MediaAsset createVodAsset(MediaImportPlanItem planItem) {
        LocalDateTime now = utcNow();
        MediaAsset asset = new MediaAsset();
        asset.setProvider("VOD");
        asset.setAppId(planItem.getAppId());
        asset.setFileId(planItem.getFileId());
        asset.setStatus("PENDING");
        asset.setDetached(false);
        asset.setOrigin("OLD_IMPORT");
        asset.setOldId(planItem.getOldId());
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        asset.setRevision(0L);
        mediaMapper.insert(asset);
        return asset;
    }

    private MediaAsset findVod(String appId, String fileId) {
        return mediaMapper.selectOne(Wrappers.<MediaAsset>lambdaQuery()
                .eq(MediaAsset::getProvider, "VOD")
                .eq(MediaAsset::getAppId, appId)
                .eq(MediaAsset::getFileId, fileId)
                .last("LIMIT 1"));
    }

    private MediaAsset requireMedia(Long id) {
        MediaAsset asset = mediaMapper.selectById(id);
        if (asset == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "媒体不存在");
        }
        return asset;
    }

    private MediaPreviewItem toPreviewItem(MediaImportPlanItem plan) {
        return new MediaPreviewItem(plan.getOldId(), plan.getAction(), null, null);
    }

    private MediaImportPreviewResponse previewFromPlan(String operationKey, String batchId, String inputHash) {
        List<MediaPreviewItem> items = planMapper.selectByBatch(operationKey).stream()
                .map(this::toPreviewItem).toList();
        return new MediaImportPreviewResponse(operationKey, batchId, inputHash, items, List.of(), List.of());
    }

    private List<MediaImportResultItem> rebuildImportResults(List<String> selectedOldIds) {
        List<MediaImportResultItem> results = new ArrayList<>();
        for (String oldId : selectedOldIds) {
            ImportMapping mapping = importMappingMapper.selectOne(Wrappers.<ImportMapping>lambdaQuery()
                    .eq(ImportMapping::getEntityType, ENTITY_MEDIA)
                    .eq(ImportMapping::getOldId, oldId)
                    .last("LIMIT 1"));
            if (mapping == null) {
                results.add(new MediaImportResultItem(oldId, null, "NOT_IMPORTED", null));
                continue;
            }
            MediaAsset asset = mediaMapper.selectById(mapping.getNewId());
            results.add(new MediaImportResultItem(oldId, String.valueOf(mapping.getNewId()),
                    asset == null ? "MISSING" : asset.getStatus(), null));
        }
        return results;
    }

    private MediaImportPlanItem plan(String batchId, MediaImportItem item, String inputHash, String action) {
        MediaImportPlanItem planItem = new MediaImportPlanItem();
        planItem.setBatchId(batchId);
        planItem.setOldId(item.oldId());
        planItem.setAppId(item.appId());
        planItem.setFileId(item.fileId());
        planItem.setInputHash(inputHash);
        planItem.setAction(action);
        return planItem;
    }

    private String operationKey(String prefix, String batchId) {
        return sha256(prefix + ":" + batchId).substring(0, 36);
    }

    private void saveOperation(String operationKey, String kind, String inputJson, String inputHash,
                               String resultJson, String state) {
        MediaOperation operation = new MediaOperation();
        operation.setOperationKey(operationKey);
        operation.setKind(kind);
        operation.setInputJson(inputJson);
        operation.setInputHash(inputHash);
        operation.setResultJson(resultJson);
        operation.setState(state);
        operation.setAttempts(0);
        operation.setNextRetryAt(utcNow());
        operation.setCreatedAt(utcNow());
        operation.setUpdatedAt(utcNow());
        operation.setRevision(0L);
        operationMapper.insert(operation);
    }

    private MediaItemView toView(MediaAsset asset) {
        return new MediaItemView(
                String.valueOf(asset.getId()),
                asset.getProvider(),
                asset.getAppId(),
                asset.getFileId(),
                asset.getDemoPath(),
                asset.getDurationMs(),
                asset.getStatus(),
                Boolean.TRUE.equals(asset.getDetached()),
                asset.getOrigin(),
                asset.getVerifiedAt() == null ? null : asset.getVerifiedAt().toInstant(ZoneOffset.UTC).toString(),
                asset.getRevision() == null ? 0 : asset.getRevision());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}