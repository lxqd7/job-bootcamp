package com.jobbootcamp.user.domain;

public enum ClientApp {
    STUDENT,
    ADMIN;

    public boolean allowsRole(String roleCode) {
        if (this == STUDENT) {
            return "STUDENT".equals(roleCode);
        }
        return "TEACHER".equals(roleCode) || "ADMIN".equals(roleCode);
    }
}