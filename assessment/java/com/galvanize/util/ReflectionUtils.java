package com.galvanize.util;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.Invokable;
import com.google.common.reflect.Parameter;
import com.google.common.reflect.TypeToken;
import org.junit.jupiter.api.Assertions;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

import static java.util.stream.Collectors.joining;

public class ReflectionUtils {

    public static final String DELIMITER = ", ";

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

    public static String simpleName(Object instance) {
        if (instance instanceof ClassProxy) return simpleName(((ClassProxy) instance).getDelegate());
        if (instance instanceof TypeToken<?>) return simpleName((TypeToken) instance);
        if (instance instanceof Class) return simpleName((Class) instance);
        if (instance instanceof Type) return simpleName((Type) instance);
        return instance.getClass().getSimpleName();
    }

    public static String simpleName(Class _class) {
        return _class.getSimpleName();
    }

    public static String simpleName(ClassProxy proxy) {
        return simpleName(proxy.getDelegate());
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

    public static Throwable assertInvokeThrows(
            Map<String, List<Invokable>> methods,
            Object delegate,
            Class<?> expectedType,
            String methodName,
            Object... args) {
        try {
            invoke(methods, delegate, methodName, args);
        } catch (Throwable actualException) {
            if (expectedType.isInstance(actualException)) {
                return actualException;
            } else {
                failFormat(
                        "Expected `%s` to throw a `%s` but it threw `%s`",
                        delegate.getClass(),
                        expectedType.getSimpleName(),
                        actualException.getClass().getSimpleName()
                );
            }
        }
        failFormat(
                "Expected `%s` to throw a %s but it threw nothing",
                delegate.getClass(),
                expectedType.getSimpleName()
        );
        return null;
    }

    public static Object invoke(Map<String, List<Invokable>> methods, Object delegate, String methodName, Object... args) throws Throwable {
        Object[] mappedArgs = Arrays.stream(args).map(arg -> {
            if (arg instanceof InstanceProxy) return ((InstanceProxy) arg).getDelegate();
            return arg;
        }).toArray(Object[]::new);

        if (!methods.containsKey(methodName)) {
            failFormat(
                    "Error! You attempted to call the method `%s` on `%s` before calling `ensureMethod`",
                    methodName,
                    simpleName(delegate));
        }

        List<Invokable> possibleInvokables = methods.get(methodName);
        if (possibleInvokables.isEmpty()) {
            failFormat(
                    "Error! You attempted to call the method `%s` on `%s` before calling `ensureMethod`",
                    methodName,
                    simpleName(delegate));
        }

        Invokable invokable = bestMatch(possibleInvokables, mappedArgs).orElse(null);
        if (invokable == null) {
            failFormat(
                    "Error! Couldn't find a method matching `%s` on `%s` for args `%s`",
                    methodName,
                    simpleName(delegate),
                    Arrays.stream(args).map(Object::toString).collect(joining(DELIMITER)));
        }

        Object result = null;
        try {
            invokable.setAccessible(true);
            result = invokable.invoke(delegate, mappedArgs);
        } catch (IllegalAccessException e) {
            Assertions.fail(exceptionToString(e.getCause() != null ? e.getCause() : e));
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
     * @param invokables a list of Invokables
     * @param args       the arguments passed to invoke
     * @return an Optional<Method> (which is empty when no method matches)
     */
    public static Optional<Invokable> bestMatch(List<Invokable> invokables, Object[] args) {
        float highScore = 0;
        HashMap<Float, LinkedList<Invokable>> scores = new HashMap<>();

        for (Invokable invokable : invokables) {
            Optional<Float> methodScore = getInvokableScore(invokable, args);
            if (methodScore.isPresent()) {
                Float val = methodScore.get();
                if (!scores.containsKey(val)) scores.put(val, new LinkedList<>());
                scores.get(val).add(invokable);

                if (val > highScore) highScore = val;
            }
        }

        if (scores.isEmpty()) return Optional.empty();

        if (scores.get(highScore).size() > 1) throw new RuntimeException(String.format(
                "Ambiguous match!  More than one _best_ match for the call to `%s(%s)`",
                invokables.get(0).getName(),
                Arrays.stream(args).map(Object::toString).collect(joining(DELIMITER))
        ));

        return Optional.of(scores.get(highScore).getFirst());
    }

    private static Optional<Float> getInvokableScore(Invokable invokable, Object[] args) {
        float methodScore = 0;
        ImmutableList<Parameter> parameters = invokable.getParameters();
        Class[] argTypes = Arrays.stream(args).map(Object::getClass).toArray(Class[]::new);
        if (parameters.size() != argTypes.length) return Optional.empty();

        for (int i = 0; i < parameters.size(); i++) {
            Parameter parameter = parameters.get(i);
            Class<?> argType = argTypes[i];
            Class<?> rawType = parameter.getType().getRawType();
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

    public static String exceptionToString(Throwable t) {
        StringWriter output = new StringWriter();
        t.printStackTrace(new PrintWriter(output));
        return output.toString();
    }

}
