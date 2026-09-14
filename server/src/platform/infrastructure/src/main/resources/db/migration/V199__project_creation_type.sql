ALTER TABLE pm_project
    ADD COLUMN creation_type VARCHAR(16) NOT NULL DEFAULT 'NEW',
    ADD CONSTRAINT chk_pm_project_creation_type
        CHECK (creation_type IN ('NEW', 'CONTINUATION'));
