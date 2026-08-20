package com.ccb.release.window.model;

public enum ReleaseWindowStatus {
    UPCOMING("未开始"), DECLARATION_OPEN("申报中"), URGENT("紧急申报期"),
    IN_PRODUCTION("投产中"), CLOSED("已关闭");

    private final String label;

    ReleaseWindowStatus(String label) { this.label = label; }

    public String label() { return label; }
}
