package com.galvanize.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static com.galvanize.util.ReflectionUtils.*;
import static java.util.stream.Collectors.joining;

public class VerifiedExecutables {

    private final Class<?> declaringClass;
    private final HashMap<String, List<Executable>> executablesByName = new HashMap<>();

    public VerifiedExecutables(Class<?> clazz) {
        declaringClass = clazz;
    }

    public void add(Executable executable) {
        String name = executable.getName();

        List<Executable> executables = executablesByName.computeIfAbsent(name, k -> new ArrayList<>());
        if (! executables.contains(executable)) {
            executables.add(executable);
        }
    }

    public void addAll(VerifiedExecutables verifiedExecutables) {
        // TODO pab: ensure declaring classes are equal
        verifiedExecutables.executablesByName.values()
                .forEach(executables -> executables.forEach(this::add));
    }

    public List<Executable> get(String name) {
        return executablesByName.getOrDefault(name, Collections.emptyList());
    }

    public Optional<Executable> getFirst(String name) {
        return get(name).stream().findFirst();
    }

    public List<Executable> getConstructors() {
        return get(declaringClass.getName());
    }

    public Object invoke(Object receiver, String methodName, Object... args) throws Throwable {

        if (args == null) args = new Object[] {null};

        List<Executable> possibleExecutables = get(methodName);
        if (possibleExecutables.isEmpty()) {
            // TODO pab: throw UnverifiedExecutableException instead
            failFormat(
                    "Error! You attempted to call the method `%s` on `%s` before calling `ensureMethod`",
                    methodName,
                    simpleName(receiver));
        }

        Method method = (Method) MethodMatcher.bestMatch(possibleExecutables, resolveInstanceProxies(args)).orElse(null);
        if (method == null) {
            // TODO pab: use orElseThrow UnmatchedExecutableException instead
            failFormat(
                    "Couldn't find a verified method matching `%s` on `%s` for args `%s`",
                    methodName,
                    simpleName(receiver),
                    Arrays.stream(args).map(arg -> arg == null ? null : arg.toString()).collect(joining(DELIMITER)));
        }

        return invoke(receiver, method, args);
    }

    public Object invoke(Object receiver, Method method, Object... args) throws Throwable {

        if (args == null) args = new Object[] {null};

        String methodName = method.getName();
        List<Executable> methodsWithSameName = executablesByName.get(methodName);
        if (methodsWithSameName == null || ! methodsWithSameName.contains(method)) {
            // TODO pab: throw UnverifiedExecutableException instead
            failFormat(
                    "Error! You attempted to call the method `%s` on `%s` before calling `ensureMethod`",
                    methodName,
                    simpleName(receiver));
        }

        Object result = null;
        try {
            method.setAccessible(true);
            result = method.invoke(receiver, resolveInstanceProxies(args));
        } catch (IllegalAccessException e) {
            failFormat("Error! Couldn't invoke `%s` on `%s` for args `%s`\n\n%s",
                    method.getName(),
                    simpleName(declaringClass),
                    Arrays.stream(args).map(arg -> arg == null ? null : arg.toString()).collect(joining(DELIMITER)),
                    exceptionToString(e)
            );
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
        return result;
    }

    public Object newInstance(Object... args) throws Throwable {

        if (args == null) args = new Object[] {null};

        List<Executable> possibleExecutables = getConstructors();
        if (possibleExecutables.isEmpty()) {
            // TODO pab: throw UnverifiedExecutableException instead
            failFormat(
                    "Error! You attempted to call a constructor on `%s` before calling `ensureConstructor`",
                    simpleName(declaringClass));
        }

        Constructor constructor = (Constructor) MethodMatcher.bestMatch(possibleExecutables, resolveInstanceProxies(args)).orElse(null);
        if (constructor == null) {
            // TODO pab: use orElseThrow UnmatchedExecutableException instead
            failFormat(
                    "Couldn't find a verified constructor on `%s` for args `%s`",
                    simpleName(declaringClass),
                    // TODO pab: create method to nicely format args. Something like <Type>:<Value(10? chars max)>
                    Arrays.stream(args).map(arg -> arg == null ? null : arg.toString()).collect(joining(DELIMITER)));
        }

        Object result = null;
        try {
            constructor.setAccessible(true);
            result = constructor.newInstance(resolveInstanceProxies(args));
        } catch (IllegalAccessException e) {
            failFormat("Error! Couldn't invoke `%s` on `%s` for args `%s`\n\n%s",
                    constructor.getName(),
                    simpleName(declaringClass),
                    Arrays.stream(args).map(arg -> arg == null ? null : arg.toString()).collect(joining(DELIMITER)),
                    exceptionToString(e)
            );
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
        return result;
    }
}
