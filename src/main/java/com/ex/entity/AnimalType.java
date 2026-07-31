package com.ex.entity;

public enum AnimalType {
    CATTLE("한우"),
    DAIRY_CATTLE("젖소"),
    PIG("돼지"),
    CHICKEN("닭"),
    DUCK("오리"),
    PET("반려동물"),
    SUPPLEMENT("영양제");

    private final String label;

    AnimalType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
