ALTER TABLE assignment
    DROP CHECK chk_assignment_kind;

ALTER TABLE assignment
    DROP CHECK chk_assignment_scope;

ALTER TABLE assignment
    ADD CONSTRAINT chk_assignment_kind CHECK (
        kind IN ('CHAPTER_CHOICE', 'COURSE_CODE', 'BENEFIT_CHOICE', 'BENEFIT_CODE')
    );

ALTER TABLE assignment
    ADD CONSTRAINT chk_assignment_scope CHECK (
        (kind = 'CHAPTER_CHOICE' AND chapter_id IS NOT NULL)
        OR (kind IN ('COURSE_CODE', 'BENEFIT_CHOICE', 'BENEFIT_CODE') AND chapter_id IS NULL)
    );

ALTER TABLE assignment_route
    ADD KEY idx_assignment_route_route (route_id, epoch_id, assignment_id);