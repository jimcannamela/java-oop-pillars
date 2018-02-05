package com.galvanize.util;

import com.google.common.reflect.Invokable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public enum Visibility {
    PUBLIC("public"), PROTECTED("protected"), PACKAGE_PRIVATE("package private", false), PRIVATE("private");

    public static Visibility of(Invokable invokable) {
        if (invokable.isPublic()) return PUBLIC;
        if (invokable.isProtected()) return PROTECTED;
        if (invokable.isPrivate()) return PRIVATE;
        return PACKAGE_PRIVATE;
    }

    public static Visibility of(Method method) {
        if (isPublic(method)) return PUBLIC;
        if (isProtected(method)) return PROTECTED;
        if (isPrivate(method)) return PRIVATE;
        return PACKAGE_PRIVATE;
    }

    private static boolean isPublic(Method method) {
        return Modifier.isPublic(method.getModifiers());
    }

    private static boolean isProtected(Method method) {
        return Modifier.isProtected(method.getModifiers());
    }

    private static boolean isPrivate(Method method) {
        return Modifier.isPrivate(method.getModifiers());
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
