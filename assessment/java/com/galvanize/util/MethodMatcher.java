package com.galvanize.util;

import com.google.common.reflect.TypeToken;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;

public class MethodMatcher {

    private static final Map<Class, Class> PRIMITIVES = new HashMap<Class, Class>(){
        {
            put(Boolean.TYPE, Boolean.class);
            put(Byte.TYPE, Byte.class);
            put(Character.TYPE, Character.class);
            put(Short.TYPE, Short.class);
            put(Integer.TYPE, Integer.class);
            put(Long.TYPE, Long.class);
            put(Float.TYPE, Float.class);
            put(Double.TYPE, Double.class);
        }
    };

    public static boolean isStatic(Method method) {
        return Modifier.isStatic(method.getModifiers());
    }

    /**
     * finds method whose parameters most closely match the types of the arguments
     * <p>
     * Let's say you define two methods:
     * <p>
     * - `foo(Map<String>)`
     * - `foo(HashMap<String>)`
     * <p>
     * If you invoke that method with `foo(new LinkedHashMap())` it would call the `foo(HashMap<String>)` method
     * <p>
     * If you invoke that method with `foo(new TreeMap())` it would call the `foo(Map<String>)` method
     * <p>
     * It's not perfect - it works by scoring every parameter separately, and summing the scores.
     * <p>
     * It de-prioritizes parameter types of Object/Object[]
     * <p>
     * If it could match 2 methods, it throws a RuntimeException
     *
     * @param executables a list of Executables
     * @param args       the arguments passed to invoke
     * @return an Optional<Method> (which is empty when no method matches)
     */
    public static Optional<Executable> bestMatch(List<? extends Executable> executables, Object[] args) {
        float highScore = 0;
        HashMap<Float, LinkedList<Executable>> scores = new HashMap<>();

        for (Executable executable : executables) {
            Optional<Float> methodScore = getExecutableScore(executable, args);
            if (methodScore.isPresent()) {
                Float val = methodScore.get();
                if (!scores.containsKey(val)) scores.put(val, new LinkedList<>());
                scores.get(val).add(executable);

                if (val > highScore) highScore = val;
            }
        }

        if (scores.isEmpty()) return Optional.empty();

        if (scores.get(highScore).size() > 1) throw new RuntimeException(String.format(
                "Ambiguous match!  More than one _best_ match for the call to `%s(%s)`",
                executables.get(0).getName(),
                Arrays.stream(args).map(Object::toString).collect(joining(ReflectionUtils.DELIMITER))
        ));

        return Optional.of(scores.get(highScore).getFirst());
    }

    private static Optional<Float> getExecutableScore(Executable executable, Object[] args) {
        float methodScore = 0;
        Parameter[] parameters = executable.getParameters();
        Class[] argTypes = Arrays.stream(args).map(arg -> arg != null ? arg.getClass() : null).toArray(Class[]::new);
        if (parameters.length != argTypes.length) return Optional.empty();

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Class<?> argType = argTypes[i];
            if (argType == null) continue;
            Class<?> rawType = parameter.getType();
            if (rawType.equals(argType)) {
                methodScore += 3f;
            } else if (rawType.isAssignableFrom(argType)) {
                float delta = minDistance(argType, rawType) / 100f;
                float range = rawType.equals(Object.class) || rawType.equals(Object[].class) ? 1 : 2;
                methodScore += range - delta;
            } else if (PRIMITIVES.containsKey(rawType) && PRIMITIVES.get(rawType).equals(argType)) {
                methodScore += 0.5;
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(methodScore);
    }

    /**
     * returns the minimum number of levels between a subtype and a supertype
     *
     * @param lower the subtype
     * @param upper the supertype
     * @return
     */
    public static int minDistance(Class<?> lower, Class<?> upper) {
        if (lower.equals(upper)) return 0;
        if (!upper.isAssignableFrom(lower)) throw new IllegalArgumentException("upper is not a supertype of lower");

        if (upper.isInterface()) {
            Class[] interfaces = lower.getInterfaces();
            Class superclass = lower.getSuperclass();

            int min = Integer.MAX_VALUE;
            if (superclass != null && upper.isAssignableFrom(superclass)) {
                int classDistance = minDistance(superclass, upper) + 1;
                if (classDistance < min) {
                    min = classDistance;
                }
            }
            for (Class _interface : interfaces) {
                if (!upper.isAssignableFrom(_interface)) continue;
                int interfaceDistance = minDistance(_interface, upper) + 1;
                if (interfaceDistance < min) {
                    min = interfaceDistance;
                }
            }
            return min;
        } else {
            int result = 0;
            Class current = lower;
            while (current != null && !current.equals(upper)) {
                result += 1;
                current = current.getSuperclass();
            }
            return result;
        }
    }

    // TODO pab: Write unit tests, particularly for subtypes/supertypes
    public static boolean typesMatch(Type[] actualTypes, TypeToken<?>[] specifiedTypes) {
        if (specifiedTypes.length != actualTypes.length) return false;

        for (int i = 0; i < actualTypes.length; i++) {
            if (specifiedTypes[i] == null) continue;    // null is *wildcard* and matches any type

            TypeToken<?> actualType = TypeToken.of(actualTypes[i]);
            TypeToken<?> expectedType = specifiedTypes[i];
            if (actualType.isSubtypeOf(expectedType)) continue;    // matches same types as well

            return false;
        }
        return true;
    }

    private final MethodCriteria criteria;

    public MethodMatcher(MethodCriteria criteria) {
        this.criteria = criteria;
    }

    public Method find(List<Method> methods) {
        List<Method> candidates = methods.stream()
                .filter(m -> !criteria.getName().isPresent() || criteria.getName().get().equals(m.getName()))
                .filter(m -> {
                    Type[] parameters = m.getGenericParameterTypes();

                    if (criteria.getParameterCount().isPresent()
                            && parameters.length != criteria.getParameterCount().get()) return false;
                    return !criteria.getParameterTypes().isPresent()
                            || typesMatch(parameters, criteria.getParameterTypes().get());

                })
                .filter(m -> !criteria.isStatic().isPresent() || criteria.isStatic().get() == isStatic(m))
                .filter(m -> !criteria.getVisibility().isPresent() || criteria.getVisibility().get() == Visibility.of(m))
                .filter(m -> !criteria.getReturnType().isPresent() || criteria.getReturnType().get().equals(m.getReturnType()))
                .filter(m -> {
                    Class<? extends Throwable>[] specifiedTypes = criteria.getExceptionTypes().orElse(null);
                    if (specifiedTypes == null) return true;

                    List<Type> declaredExceptionTypes = Arrays.asList(m.getGenericExceptionTypes());
                    if (specifiedTypes.length != declaredExceptionTypes.size()) return false;

                    return Arrays.stream(specifiedTypes).allMatch(type -> declaredExceptionTypes.contains(type));
                })
                .collect(Collectors.toList());

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        Optional<Executable> matchedMethod = bestMatch(candidates,
                criteria.getParameterTypes().orElse(new TypeToken[0]));
        return (Method) matchedMethod.orElse(null);
    }
}
