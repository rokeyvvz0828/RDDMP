package com.ccb.security.model;

import java.util.List;

public record RouteNode(long id, long parentId, String menuType, String menuName,
                        String routeName, String routePath, String componentPath,
                        String permissionCode, String icon, int sortNo, List<RouteNode> children) {
}
