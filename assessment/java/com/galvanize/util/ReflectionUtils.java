package com.galvanize.util;

import com.google.common.reflect.Invokable;
import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import org.junit.jupiter.api.Assertions;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public class ReflectionUtils {

    public static final String DELIMITER = ", ";

    static String joinSimpleNames(Stream<?> classStream) {
        return classStream
                .map(ReflectionUtils::simpleName)
                .collect(joining(DELIMITER));
    }

    public static String simpleName(Object instance) {
        if (instance instanceof ClassProxy) return simpleName(((ClassProxy) instance).getDelegate());
        if (instance instanceof TypeToken<?>) return simpleName((TypeToken) instance);
        if (instance instanceof Class) return simpleName((Class) instance);
        if (instance instanceof Type) return simpleName((Type) instance);
        return simpleName(TypeToken.of(instance.getClass()));
    }

    public static String simpleName(Class clazz) {
        return simpleName(TypeToken.of(clazz));
    }

    public static String simpleName(ClassProxy proxy) {
        return simpleName(TypeToken.of(proxy.getDelegate()));
    }

    public static String simpleName(Type type) {
        return simpleName(TypeToken.of(type));
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
        ).replaceAll("<>", "");
    }

    public static void failFormat(String pattern, Object... args) {
        Assertions.fail(String.format(pattern, args));
    }

    public static Throwable assertInvokeThrows(
            VerifiedInvokables methods,
            Object delegate,
            Class<?> expectedType,
            String methodName,
            Object... args) {
        try {
            methods.invoke(delegate, methodName, args);
        } catch (Throwable actualException) {
            verifyExpectedException(delegate, expectedType, actualException);
            return actualException;
        }
        failFormat(
                "Expected `%s` to throw a %s but it threw nothing",
                delegate.getClass(),
                expectedType.getSimpleName()
        );
        return null;
    }

    public static Throwable assertInvokeThrows(
            VerifiedInvokables methods,
            Object delegate,
            Class<?> expectedType,
            Invokable method,
            Object... args) {
        try {
            methods.invoke(delegate, method, args);
        } catch (Throwable actualException) {
            verifyExpectedException(delegate, expectedType, actualException);
            return actualException;
        }
        failFormat(
                "Expected `%s` to throw a %s but it threw nothing",
                delegate.getClass(),
                expectedType.getSimpleName()
        );
        return null;
    }

    private static void verifyExpectedException(Object delegate, Class<?> expectedType, Throwable actualException) {
        if (expectedType.isInstance(actualException)) return;
        failFormat(
                "Expected `%s` to throw a `%s` but it threw `%s`",
                delegate.getClass(),
                expectedType.getSimpleName(),
                actualException.getClass().getSimpleName()
        );
    }

    static Object[] resolveInstanceProxies(Object[] args) {
        return Arrays.stream(args).map(arg -> {
                if (arg instanceof InstanceProxy) return ((InstanceProxy) arg).getDelegate();
                return arg;
            }).toArray(Object[]::new);
    }

    public static String exceptionToString(Throwable t) {
        StringWriter output = new StringWriter();
        t.printStackTrace(new PrintWriter(output));
        return output.toString();
    }

    public static <T> TypeToken<List<T>> listOf(ClassProxy proxy) {
        return listOf((TypeToken<T>) TypeToken.of(proxy.getDelegate()));
    }

    public static <T> TypeToken<List<T>> listOf(TypeToken<T> token) {
        return new TypeToken<List<T>>() {}.where(new TypeParameter<T>() {}, token);
    }
}
