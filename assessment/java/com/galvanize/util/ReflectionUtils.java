package com.galvanize.util;

import com.google.common.reflect.TypeToken;
import org.junit.jupiter.api.Assertions;

import java.lang.reflect.*;
import java.util.*;

import static java.util.stream.Collectors.joining;

public class ReflectionUtils {

    public static final String DELIMITER = ", ";

    public static String simpleName(Object instance) {
        if (instance instanceof TypeToken<?>) return simpleName((TypeToken) instance);
        if (instance instanceof Class) return simpleName((Class) instance);
        if (instance instanceof Type) return simpleName((Type) instance);
        return instance.getClass().getSimpleName();
    }

    public static String simpleName(Class _class) {
        return _class.getSimpleName();
    }

    public static String simpleName(Type type) {
        if (type instanceof ParameterizedType) {
            Type[] actualTypeArguments = ((ParameterizedType) type).getActualTypeArguments();
            return String.format(
                    "%s<%s>",
                    ((Class) ((ParameterizedType) type).getRawType()).getSimpleName(),
                    Arrays.stream(actualTypeArguments)
                            .map(genericType -> {
                                if (genericType instanceof ParameterizedType) {
                                    return simpleName(genericType);
                                }
                                return ((Class) genericType).getSimpleName();
                            })
                            .collect(joining(DELIMITER))
            );
        } else if (type instanceof Class) {
            return ((Class) type).getSimpleName();
        } else {
            return type.getTypeName();
        }
    }

    public static String simpleName(TypeToken token) {
        return String.format(
                "%s<%s>",
                token.getRawType().getSimpleName(),
                Arrays.stream(token.getRawType().getTypeParameters()).map(f -> {
                    TypeToken<?> resolved = token.resolveType(f);
                    boolean isParameterized = resolved.getType() instanceof ParameterizedType;
                    if (isParameterized) {
                        return simpleName(resolved);
                    } else {
                        return resolved.getRawType().getSimpleName();
                    }
                }).collect(joining(DELIMITER))
        );
    }

    public static void failFormat(String pattern, Object... args) {
        Assertions.fail(String.format(pattern, args));
    }

    public static String parameterTypeNames(Type[] types) {
        return Arrays.stream(types)
                .map(ReflectionUtils::simpleName)
                .collect(joining(DELIMITER));
    }

    public static Object invoke(Map<String, List<Method>> methods, Object delegate, String methodName, Object... args) throws Throwable {
        if (!methods.containsKey(methodName)) {
            failFormat(
                    "Error! You attempted to call the method `%s` on `%s` before calling `ensureMethod`",
                    methodName,
                    simpleName(delegate));
        }

        List<Method> possibleMethods = methods.get(methodName);
        if (possibleMethods.isEmpty()) {
            failFormat(
                    "Error! You attempted to call the method `%s` on `%s` before calling `ensureMethod`",
                    methodName,
                    simpleName(delegate));
        }

        Method method = bestMatch(possibleMethods, args).orElse(null);
        if (method == null) {
            failFormat(
                    "Error! Couldn't find a method matching `%s` on `%s` for args `%s`",
                    methodName,
                    simpleName(delegate),
                    Arrays.stream(args).map(Object::toString).collect(joining(DELIMITER)));
        }

        Object result = null;
        try {
            method.setAccessible(true);
            result = method.invoke(delegate, args);
        } catch (IllegalAccessException e) {
            (e.getCause() != null ? e.getCause() : e).printStackTrace();
            Assertions.fail("");
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
        return result;

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
     * @param methods a list of methods
     * @param args the arguments passed to invoke
     * @return an Optional<Method> (which is empty when no method matches)
     */
    public static Optional<Method> bestMatch(List<Method> methods, Object[] args) {
        float highScore = 0;
        HashMap<Float, LinkedList<Method>> scores = new HashMap<>();

        for (Method method : methods) {
            Optional<Float> methodScore = getMethodScore(method, args);
            if (methodScore.isPresent()) {
                Float val = methodScore.get();
                if (!scores.containsKey(val)) scores.put(val, new LinkedList<>());
                scores.get(val).add(method);

                if (val > highScore) highScore = val;
            }
        }

        if (scores.isEmpty()) return Optional.empty();

        if (scores.get(highScore).size() > 1) throw new RuntimeException(String.format(
                "Ambiguous match!  More than one method matched the call to `%s(%s)`",
                methods.get(0).getName(),
                Arrays.stream(args).map(Object::toString).collect(joining(DELIMITER))
        ));

        return Optional.of(scores.get(highScore).getFirst());
    }

    public static Optional<Constructor> bestMatchConstructor(List<Constructor> constructors, Object[] args) {
        float highScore = 0;
        HashMap<Float, LinkedList<Constructor>> scores = new HashMap<>();

        for (Constructor constructor : constructors) {
            Optional<Float> methodScore = getConstructorScore(constructor, args);
            if (methodScore.isPresent()) {
                Float val = methodScore.get();
                if (!scores.containsKey(val)) scores.put(val, new LinkedList<>());
                scores.get(val).add(constructor);

                if (val > highScore) highScore = val;
            }
        }

        if (scores.isEmpty()) return Optional.empty();

        if (scores.get(highScore).size() > 1) throw new RuntimeException(String.format(
                "Ambiguous match!  More than one method matched the call to `%s(%s)`",
                constructors.get(0).getName(),
                Arrays.stream(args).map(Object::toString).collect(joining(DELIMITER))
        ));

        return Optional.of(scores.get(highScore).getFirst());
    }

    private static Optional<Float> getMethodScore(Method method, Object[] args) {
        float methodScore = 0;
        Class[] parameterTypes = method.getParameterTypes();
        Class[] argTypes = Arrays.stream(args).map(Object::getClass).toArray(Class[]::new);
        if (parameterTypes.length != argTypes.length) return Optional.empty();

        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            Class<?> argType = argTypes[i];

            if (parameterType.equals(argType)) {
                methodScore += 3f;
            } else if (parameterType.isAssignableFrom(argType)) {
                float delta = minDistance(argType, parameterType) / 100f;
                float range = parameterType.equals(Object.class) || parameterType.equals(Object[].class) ? 1 : 2;
                methodScore += range - delta;
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(methodScore);
    }

    private static Optional<Float> getConstructorScore(Constructor constructor, Object[] args) {
        float methodScore = 0;
        Class[] parameterTypes = constructor.getParameterTypes();
        Class[] argTypes = Arrays.stream(args).map(Object::getClass).toArray(Class[]::new);
        if (parameterTypes.length != argTypes.length) return Optional.empty();

        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            Class<?> argType = argTypes[i];

            if (parameterType.equals(argType)) {
                methodScore += 3f;
            } else if (parameterType.isAssignableFrom(argType)) {
                float delta = minDistance(argType, parameterType) / 100f;
                float range = parameterType.equals(Object.class) || parameterType.equals(Object[].class) ? 1 : 2;
                methodScore += range - delta;
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


}
