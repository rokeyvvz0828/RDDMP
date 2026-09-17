package com.ccb.system.org;

import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class OrganizationRepository {
    private final OrganizationMapper mapper;
    public OrganizationRepository(OrganizationMapper mapper) { this.mapper = mapper; }
    public List<Map<String,Object>> organizations(long tenantId) { return mapper.selectOrganizations(tenantId); }
    public List<Map<String,Object>> users(long tenantId) { return mapper.selectUsers(tenantId); }
}
