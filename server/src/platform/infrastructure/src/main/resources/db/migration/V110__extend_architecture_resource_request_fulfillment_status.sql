-- REQ-20260825-053：资源申请办理下发后的状态扩展。

ALTER TABLE arch_resource_request
    DROP CHECK chk_arch_resource_request_status;

ALTER TABLE arch_resource_request
    ADD CONSTRAINT chk_arch_resource_request_status CHECK (
        status IN (
            'DRAFT',
            'IN_REVIEW',
            'RETURNED',
            'APPROVED',
            'FULFILLED',
            'DIFF_FULFILLED',
            'REJECTED',
            'CANCELLED'
        )
    );
