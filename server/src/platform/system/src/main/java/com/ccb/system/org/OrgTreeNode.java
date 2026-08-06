package com.ccb.system.org;

import java.util.List;

public record OrgTreeNode(long id, long parentId, String orgCode, String orgName, int sortNo, int status,
                          List<OrgTreeNode> children, List<OrgUserSummary> users) {
}