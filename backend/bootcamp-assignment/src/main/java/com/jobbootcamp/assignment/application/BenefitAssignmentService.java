package com.jobbootcamp.assignment.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobbootcamp.assignment.domain.Assignment;
import com.jobbootcamp.assignment.domain.AssignmentRoute;
import com.jobbootcamp.assignment.domain.AssignmentVersion;
import com.jobbootcamp.assignment.domain.dto.BenefitAssignmentRequest;
import com.jobbootcamp.assignment.domain.dto.BenefitAssignmentSummary;
import com.jobbootcamp.assignment.domain.dto.BenefitAssignmentView;
import com.jobbootcamp.assignment.domain.dto.BoundRouteView;
import com.jobbootcamp.assignment.domain.dto.PageResponse;
import com.jobbootcamp.assignment.infrastructure.AssignmentMapper;
import com.jobbootcamp.assignment.infrastructure.AssignmentRouteMapper;
import com.jobbootcamp.assignment.infrastructure.AssignmentVersionMapper;
import com.jobbootcamp.common.error.BusinessException;
import com.jobbootcamp.common.error.ErrorCode;
import com.jobbootcamp.contracts.internal.TeacherBenefitScopeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 教师福利题：课程+路线双范围、每道题单题、首次发布在同一事务绑定路线当前 epoch。
 * 之后内容更新生成新版本但不改路线/epoch；新增开放范围必须另建新题。
 */
@Service
public class BenefitAssignmentService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> KINDS = Set.of("BENEFIT_CHOICE", "BENEFIT_CODE");

    private final AssignmentMapper assignmentMapper;
    private final AssignmentVersionMapper versionMapper;
    private final AssignmentRouteMapper routeMapper;
    private final AssignmentContentValidator validator;
    private final CourseScopeClient courseScopeClient;

    public BenefitAssignmentService(AssignmentMapper assignmentMapper,
                                    AssignmentVersionMapper versionMapper,
                                    AssignmentRouteMapper routeMapper,
                                    AssignmentContentValidator validator,
                                    CourseScopeClient courseScopeClient) {
        this.assignmentMapper = assignmentMapper;
        this.versionMapper = versionMapper;
        this.routeMapper = routeMapper;
        this.validator = validator;
        this.courseScopeClient = courseScopeClient;
    }

    public PageResponse<BenefitAssignmentSummary> list(Long teacherId, Long courseId, int page, int pageSize) {
        if (courseId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请指定课程");
        }
        TeacherBenefitScopeResponse scope = courseScopeClient.benefitScope(
                teacherId, courseId, List.of());
        if (!scope.courseAssigned()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不在负责课程范围");
        }
        long pageNumber = Math.max(1, page);
        long size = Math.min(100, Math.max(1, pageSize));
        IPage<Assignment> result = assignmentMapper.selectPage(new Page<>(pageNumber, size),
                Wrappers.<Assignment>lambdaQuery()
                        .in(Assignment::getKind, KINDS)
                        .eq(courseId != null, Assignment::getCourseId, courseId)
                        .orderByDesc(Assignment::getUpdatedAt)
                        .orderByDesc(Assignment::getId));
        List<BenefitAssignmentSummary> items = result.getRecords().stream().map(assignment -> {
            Integer versionNo = currentVersionNo(assignment);
            return new BenefitAssignmentSummary(assignment.getId(), assignment.getKind(),
                    assignment.getCourseId(), assignment.getStatus(), versionNo,
                    assignment.getRevision(), assignment.getUpdatedAt(), routeIdsOf(assignment));
        }).toList();
        return new PageResponse<>(items, result.getTotal(), pageNumber, size);
    }

    public BenefitAssignmentView get(Long teacherId, Long id) {
        Assignment assignment = requireBenefit(id);
        requireCourseAssigned(teacherId, assignment.getCourseId());
        requireAssigned(teacherId, assignment.getCourseId(), routeIdsOf(assignment));
        return view(assignment);
    }

    @Transactional
    public BenefitAssignmentView create(Long teacherId, BenefitAssignmentRequest request) {
        String kind = normalizedKind(request);
        List<Long> routeIds = routeIds(request.routeIds());
        requirePublishable(teacherId, request.courseId(), routeIds);
        JsonNode publicBody = validateAndObject(request.publicBody(), request.answerBody(), kind);
        JsonNode answerBody = JSON.valueToTree(request.answerBody());

        Assignment assignment = new Assignment();
        assignment.setKind(kind);
        assignment.setCourseId(request.courseId());
        assignment.setChapterId(null);
        assignment.setAuthorId(teacherId);
        assignment.setStatus("DRAFT");
        assignment.setDraftJson(writeDraft(publicBody, answerBody, routeIds));
        assignment.setRevision(0L);
        assignment.setFormalScopeKey("BENEFIT:" + request.courseId() + ":" + UUID.randomUUID());
        assignment.setCreatedAt(now());
        assignment.setUpdatedAt(now());
        assignmentMapper.insert(assignment);
        return view(assignment);
    }

    @Transactional
    public BenefitAssignmentView update(Long teacherId, Long id, BenefitAssignmentRequest request) {
        Assignment assignment = assignmentMapper.selectForUpdate(id);
        requireBenefit(assignment);
        requireAuthor(assignment, teacherId);
        requireRevision(assignment, request.revision());
        if (request.kind() != null && !normalizedKind(request).equals(assignment.getKind())
                || request.courseId() != null && !request.courseId().equals(assignment.getCourseId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "已创建福利题的课程/类型不可更换");
        }
        JsonNode draft = readJson(assignment.getDraftJson());
        List<Long> storedRoutes = readRouteIds(draft);
        if (request.routeIds() != null) {
            List<Long> requested = routeIds(request.routeIds());
            if (!requested.equals(storedRoutes)) {
                throw new BusinessException(ErrorCode.CONFLICT, "首次发布前不能更换福利题路线，请另建新题");
            }
        }
        requireAssigned(teacherId, assignment.getCourseId(), storedRoutes);
        JsonNode publicBody = JSON.valueToTree(request.publicBody());
        JsonNode answerBody = JSON.valueToTree(request.answerBody());
        validator.validate(assignment.getKind(), publicBody, answerBody);
        assignment.setDraftJson(writeDraft(publicBody, answerBody, storedRoutes));
        assignment.setRevision(assignment.getRevision() + 1);
        assignment.setUpdatedAt(now());
        assignmentMapper.updateById(assignment);
        return view(assignment);
    }

    @Transactional
    public BenefitAssignmentView publish(Long teacherId, Long id, Long revision) {
        Assignment assignment = assignmentMapper.selectForUpdate(id);
        requireBenefit(assignment);
        requireAuthor(assignment, teacherId);
        requireRevision(assignment, revision);
        JsonNode draft = readJson(assignment.getDraftJson());
        List<Long> routeIds = readRouteIds(draft);
        if (routeIds.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "福利题至少绑定一条路线");
        }
        List<AssignmentRoute> bound = routeMapper.selectByAssignment(id);
        if (bound.isEmpty()) {
            // 首次发布：路线和 epoch 在同一作业库事务内固定。
            TeacherBenefitScopeResponse scope = requirePublishable(teacherId, assignment.getCourseId(), routeIds);
            LocalDateTime now = now();
            for (TeacherBenefitScopeResponse.RouteScope route : scope.routes()) {
                AssignmentRoute link = new AssignmentRoute();
                link.setAssignmentId(id);
                link.setRouteId(route.routeId());
                link.setEpochId(route.epochId());
                link.setBoundAt(now);
                routeMapper.insert(link);
            }
        } else {
            requireAssigned(teacherId, assignment.getCourseId(),
                    bound.stream().map(AssignmentRoute::getRouteId).toList());
        }
        int versionNo = versionMapper.maxVersionNo(id) + 1;
        AssignmentVersion version = new AssignmentVersion();
        version.setAssignmentId(id);
        version.setVersionNo(versionNo);
        version.setPublicBody(write(draft.get("publicBody")));
        version.setAnswerBody(write(draft.get("answerBody")));
        version.setPublishedAt(now());
        version.setPromptVersion("review-v1");
        versionMapper.insert(version);

        assignment.setCurrentVersionId(version.getId());
        assignment.setStatus("ONLINE");
        assignment.setRevision(assignment.getRevision() + 1);
        assignment.setUpdatedAt(now());
        assignmentMapper.updateById(assignment);
        return view(assignment);
    }

    @Transactional
    public BenefitAssignmentView offline(Long teacherId, Long id, Long revision) {
        Assignment assignment = assignmentMapper.selectForUpdate(id);
        requireBenefit(assignment);
        requireAuthor(assignment, teacherId);
        requireRevision(assignment, revision);
        if (assignment.getCurrentVersionId() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "尚未发布的福利题无需下架");
        }
        assignment.setStatus("OFFLINE");
        assignment.setRevision(assignment.getRevision() + 1);
        assignment.setUpdatedAt(now());
        assignmentMapper.updateById(assignment);
        return view(assignment);
    }

    @Transactional
    public void delete(Long teacherId, Long id, Long revision) {
        Assignment assignment = assignmentMapper.selectForUpdate(id);
        requireBenefit(assignment);
        requireAuthor(assignment, teacherId);
        requireRevision(assignment, revision);
        if (assignment.getCurrentVersionId() != null || routeMapper.countByAssignment(id) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "已绑定路线或已发布的福利题不能删除，只能下架");
        }
        assignmentMapper.deleteById(id);
    }

    private TeacherBenefitScopeResponse requirePublishable(Long teacherId, Long courseId, List<Long> routeIds) {
        TeacherBenefitScopeResponse scope = courseScopeClient.benefitScope(teacherId, courseId, routeIds);
        requireAssigned(teacherId, courseId, routeIds, scope);
        for (TeacherBenefitScopeResponse.RouteScope route : scope.routes()) {
            if (!route.routeOnline() || !route.containsCourse()) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "路线 " + route.routeId() + " 未开放或当前不包含该课程");
            }
        }
        return scope;
    }

    private void requireAssigned(Long teacherId, Long courseId, List<Long> routeIds) {
        requireAssigned(teacherId, courseId, routeIds,
                courseScopeClient.benefitScope(teacherId, courseId, routeIds));
    }

    private void requireAssigned(Long teacherId, Long courseId, List<Long> routeIds,
                                 TeacherBenefitScopeResponse scope) {
        if (!scope.courseAssigned()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不在负责课程范围");
        }
        if (!scope.allRoutesAssigned() || scope.routes().size() != routeIds.size()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不在负责路线范围");
        }
    }

    private void requireCourseAssigned(Long teacherId, Long courseId) {
        if (!courseScopeClient.benefitScope(teacherId, courseId, List.of()).courseAssigned()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不在负责课程范围");
        }
    }

    private BenefitAssignmentView view(Assignment assignment) {
        JsonNode draft = readJson(assignment.getDraftJson());
        Object currentPublic = null;
        Object currentAnswer = null;
        Integer versionNo = null;
        if (assignment.getCurrentVersionId() != null) {
            AssignmentVersion version = versionMapper.selectById(assignment.getCurrentVersionId());
            if (version != null) {
                versionNo = version.getVersionNo();
                currentPublic = readObject(version.getPublicBody());
                currentAnswer = readObject(version.getAnswerBody());
            }
        }
        List<BoundRouteView> routes = routeMapper.selectByAssignment(assignment.getId()).stream()
                .map(route -> new BoundRouteView(route.getRouteId(), route.getEpochId(), route.getBoundAt()))
                .toList();
        return new BenefitAssignmentView(assignment.getId(), assignment.getKind(), assignment.getCourseId(),
                assignment.getStatus(), versionNo, readObject(draft.get("publicBody")),
                readObject(draft.get("answerBody")), currentPublic, currentAnswer,
                assignment.getRevision(), assignment.getUpdatedAt(), routes);
    }

    private Integer currentVersionNo(Assignment assignment) {
        if (assignment.getCurrentVersionId() == null) {
            return null;
        }
        AssignmentVersion version = versionMapper.selectById(assignment.getCurrentVersionId());
        return version == null ? null : version.getVersionNo();
    }

    private List<Long> routeIdsOf(Assignment assignment) {
        List<AssignmentRoute> bound = routeMapper.selectByAssignment(assignment.getId());
        if (!bound.isEmpty()) {
            return bound.stream().map(AssignmentRoute::getRouteId).toList();
        }
        return readRouteIds(readJson(assignment.getDraftJson()));
    }

    private JsonNode validateAndObject(Object publicBody, Object answerBody, String kind) {
        if (publicBody == null || answerBody == null) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "题目和答案内容不能为空");
        }
        JsonNode publicNode = JSON.valueToTree(publicBody);
        JsonNode answerNode = JSON.valueToTree(answerBody);
        if (!publicNode.isObject() || !answerNode.isObject()) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "题目和答案必须是 JSON 对象");
        }
        validator.validate(kind, publicNode, answerNode);
        return publicNode;
    }

    private String normalizedKind(BenefitAssignmentRequest request) {
        if (request == null || request.kind() == null) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "kind 不能为空");
        }
        String kind = request.kind().trim().toUpperCase();
        if (!KINDS.contains(kind)) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "福利题类型只能是 BENEFIT_CHOICE/BENEFIT_CODE");
        }
        if (request.courseId() == null || request.courseId() <= 0) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "courseId 不合法");
        }
        return kind;
    }

    private List<Long> routeIds(List<Long> values) {
        if (values == null || values.isEmpty()) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "福利题至少绑定一条路线");
        }
        List<Long> unique = new ArrayList<>(new LinkedHashSet<>(values));
        if (unique.stream().anyMatch(value -> value == null || value <= 0)) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "routeIds 不合法");
        }
        if (unique.size() > 20) {
            throw new BusinessException(ErrorCode.ASSIGNMENT_INVALID, "福利题最多绑定 20 条路线");
        }
        return unique;
    }

    private String writeDraft(JsonNode publicBody, JsonNode answerBody, List<Long> routeIds) {
        ObjectNode root = JSON.createObjectNode();
        root.set("publicBody", publicBody);
        root.set("answerBody", answerBody);
        ArrayNode routes = root.putArray("routeIds");
        routeIds.forEach(routes::add);
        return write(root);
    }

    private List<Long> readRouteIds(JsonNode draft) {
        List<Long> result = new ArrayList<>();
        for (JsonNode value : draft.path("routeIds")) {
            result.add(value.asLong());
        }
        return result;
    }

    private Assignment requireBenefit(Long id) {
        Assignment assignment = assignmentMapper.selectById(id);
        requireBenefit(assignment);
        return assignment;
    }

    private void requireBenefit(Assignment assignment) {
        if (assignment == null || !KINDS.contains(assignment.getKind())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "福利题不存在");
        }
    }

    private void requireAuthor(Assignment assignment, Long teacherId) {
        if (!teacherId.equals(assignment.getAuthorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能维护自己创建的福利题");
        }
    }

    private void requireRevision(Assignment assignment, Long expected) {
        if (expected == null || !expected.equals(assignment.getRevision())) {
            throw new BusinessException(ErrorCode.CONFLICT, "福利题草稿已在其他位置更新，请刷新后重试");
        }
    }

    private Object readObject(String value) {
        try {
            return value == null || value.isBlank() ? null : JSON.readValue(value, Object.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "福利题 JSON 损坏");
        }
    }

    private Object readObject(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull()
                ? null : JSON.convertValue(node, Object.class);
    }

    private JsonNode readJson(String value) {
        try {
            return value == null ? JSON.createObjectNode() : JSON.readTree(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY, "福利题 JSON 损坏");
        }
    }

    private String write(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "福利题 JSON 序列化失败");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}