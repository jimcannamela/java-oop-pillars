package com.galvanize.util;

import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;

public enum Visibility {
    PUBLIC("public"), PROTECTED("protected"), PACKAGE_PRIVATE("package private", false), PRIVATE("private");

    public static Visibility of(Executable executable) {
        if (Modifier.isPublic(executable.getModifiers())) return PUBLIC;
        if (Modifier.isProtected(executable.getModifiers())) return PROTECTED;
        if (Modifier.isPrivate(executable.getModifiers())) return PRIVATE;
        return PACKAGE_PRIVATE;
    }

    public static Visibility of(Member member) {
        if (Modifier.isPublic(member.getModifiers())) return PUBLIC;
        if (Modifier.isProtected(member.getModifiers())) return PROTECTED;
        if (Modifier.isPrivate(member.getModifiers())) return PRIVATE;
        return PACKAGE_PRIVATE;
    }

    public static Visibility of(Class clazz) {
        if (Modifier.isPublic(clazz.getModifiers())) return PUBLIC;
        if (Modifier.isProtected(clazz.getModifiers())) return PROTECTED;
        if (Modifier.isPrivate(clazz.getModifiers())) return PRIVATE;
        return PACKAGE_PRIVATE;
    }

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
