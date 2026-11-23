ALTER TABLE moderation_action
    DROP CHECK chk_moderation_action;

ALTER TABLE moderation_action
    ADD CONSTRAINT chk_moderation_action
        CHECK (action IN ('HIDE', 'TRACE_AUTHOR', 'REVOKE_EXCELLENT'));