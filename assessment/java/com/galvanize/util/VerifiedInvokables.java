package com.galvanize.util;

import com.google.common.reflect.Invokable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static com.galvanize.util.ReflectionUtils.*;
import static java.util.stream.Collectors.joining;

public class VerifiedInvokables {

    private final Class<?> declaringClass;
    private final HashMap<String, List<Invokable>> invokablesByName = new HashMap<>();

    public VerifiedInvokables(Class<?> clazz) {
        declaringClass = clazz;
    }

    public void add(Invokable invokable) {
        String name = invokable.getName();

        List<Invokable> invokables = invokablesByName.computeIfAbsent(name, k -> new ArrayList<>());
        if (! invokables.contains(invokable)) {
            invokables.add(invokable);
        }
    }

    public void add(Constructor constructor) {
        add(Invokable.from(constructor));
    }

    public void add(Method method) {
        add(Invokable.from(method));
    }

    public void addAll(VerifiedInvokables verifiedInvokables) {
        // TODO pab: ensure declaring classes are equal
        verifiedInvokables.invokablesByName.values()
                .forEach(invokables -> invokables.forEach(this::add));
    }

    public List<Invokable> get(String name) {
        return invokablesByName.getOrDefault(name, Collections.emptyList());
    }

    public Optional<Invokable> getFirst(String name) {
        return get(name).stream().findFirst();
    }

    public List<Invokable> getConstructors() {
        return get(declaringClass.getName());
    }

    public Object invoke(Object receiver, String methodName, Object... args) throws Throwable {

        if (args == null) args = new Object[] {null};

        List<Invokable> possibleInvokables = get(methodName);
        if (possibleInvokables.isEmpty()) {
            // TODO pab: throw UnverifiedInvokableException instead
            failFormat(
                    "Error! You attempted to call the method `%s` on `%s` before calling `ensureMethod`",
                    methodName,
                    simpleName(receiver));
        }

        Invokable invokable = MethodMatcher.bestMatch(possibleInvokables, resolveInstanceProxies(args)).orElse(null);
        if (invokable == null) {
            // TODO pab: use orElseThrow UnmatchedInvokableException instead
            failFormat(
                    "Couldn't find a verified method matching `%s` on `%s` for args `%s`",
                    methodName,
                    simpleName(receiver),
                    Arrays.stream(args).map(arg -> arg == null ? null : arg.toString()).collect(joining(DELIMITER)));
        }

        return invoke(receiver, invokable, args);
    }

    public Object invoke(Object receiver, Invokable invokable, Object... args) throws Throwable {

        if (args == null) args = new Object[] {null};

        String methodName = invokable.getName();
        List<Invokable> methodsWithSameName = invokablesByName.get(methodName);
        if (methodsWithSameName == null || ! methodsWithSameName.contains(invokable)) {
            // TODO pab: throw UnverifiedInvokableException instead
            failFormat(
                    "Error! You attempted to call the method `%s` on `%s` before calling `ensureMethod`",
                    methodName,
                    simpleName(receiver));
        }

        Object result = null;
        try {
            invokable.setAccessible(true);
            result = invokable.invoke(receiver, resolveInstanceProxies(args));
        } catch (IllegalAccessException e) {
            failFormat("Error! Couldn't invoke `%s` on `%s` for args `%s`\n\n%s",
                    invokable.getName(),
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

        List<Invokable> possibleInvokables = getConstructors();
        if (possibleInvokables.isEmpty()) {
            // TODO pab: throw UnverifiedInvokableException instead
            failFormat(
                    "Error! You attempted to call a constructor on `%s` before calling `ensureConstructor`",
                    simpleName(declaringClass));
        }

        Invokable invokable = MethodMatcher.bestMatch(possibleInvokables, resolveInstanceProxies(args)).orElse(null);
        if (invokable == null) {
            // TODO pab: use orElseThrow UnmatchedInvokableException instead
            failFormat(
                    "Couldn't find a verified constructor on `%s` for args `%s`",
                    simpleName(declaringClass),
                    // TODO pab: create method to nicely format args. Something like <Type>:<Value(10? chars max)>
                    Arrays.stream(args).map(arg -> arg == null ? null : arg.toString()).collect(joining(DELIMITER)));
        }

        return invoke(declaringClass, invokable, args);
    }
}
