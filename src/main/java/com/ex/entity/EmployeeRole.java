package com.ex.entity;

public enum EmployeeRole {
    ADMIN("책임자"),
    STAFF("사원");

    private final String label;

    EmployeeRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
