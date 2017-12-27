package com.galvanize.util;

public enum Visibility {
    PUBLIC("public"), PROTECTED("protected"), PRIVATE("private"), PACKAGE_PRIVATE("package private", false);

    private final String name;
    private final boolean emitsName;

    Visibility(String name) {
        this(name, true);
    }

    Visibility(String name, boolean emitsName) {
        this.name = name;
        this.emitsName = emitsName;
    }

    public String toMethodSignatureString() {
        return emitsName ? name + " " : "";
    }

    public String getName() {
        return name;
    }
}
