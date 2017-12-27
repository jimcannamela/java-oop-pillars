package com.galvanize.util;

public enum ReferenceType {
    CLASS("class"), INTERFACE("interface"), ENUM("enum");

    private final String name;

    ReferenceType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
