package com.galvanize.util;

import com.google.common.reflect.Invokable;
import com.google.common.reflect.TypeToken;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.galvanize.util.ReflectionUtils.DELIMITER;
import static com.galvanize.util.ReflectionUtils.simpleName;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class MethodBuilder {

    public static final String ANY_METHOD_NAME = "*any name*";

    private final Class declaringClass;
    private boolean isStatic;
    private Object returnType;
    private TypeToken<?>[] parameterTypes;
    private int parameterCount = -1;
    private ReferenceType referenceType = ReferenceType.CLASS;
    private String name = ANY_METHOD_NAME;
    private Visibility visibility;
    private Class<? extends Throwable>[] exceptionTypes;

    public MethodBuilder(Class declaringClass) {
        this.declaringClass = declaringClass;
    }

    public Class getDeclaringClass() {
        return declaringClass;
    }

    public MethodBuilder named(String name) {

        this.name = (name == null || name.trim().isEmpty()) ? ANY_METHOD_NAME : name;
        return this;
    }

    public MethodBuilder isStatic() {
        return isStatic(true);
    }

    public MethodBuilder isStatic(boolean isStatic) {
        this.isStatic = isStatic;
        return this;
    }

    public MethodBuilder isPublic() {
        visibility = Visibility.PUBLIC;
        return this;
    }

    public MethodBuilder isProtected() {
        visibility = Visibility.PROTECTED;
        return this;
    }

    public MethodBuilder isPrivate() {
        visibility = Visibility.PRIVATE;
        return this;
    }

    public MethodBuilder isPackagePrivate() {
        this.visibility = Visibility.PACKAGE_PRIVATE;
        return this;
    }

    public MethodBuilder returns(ClassProxy returnTypeProxy) {
        return returns(returnTypeProxy.getDelegate());
    }

    public MethodBuilder returns(Type returnType) {
        this.returnType = returnType;
        return this;
    }

    public MethodBuilder returns(TypeToken returnType) {
        this.returnType = returnType;
        return this;
    }

    public MethodBuilder withParameterCount(int numberOfParameters) {
        if (parameterTypes != null && parameterTypes.length != numberOfParameters) {
            failFormat("Parameter count, %d, doesn't match the number of previously specified parameters, %d",
                    numberOfParameters, parameterTypes.length);
        }
        if (numberOfParameters < 0) {
            failFormat("Specified parameter count, %d, is invalid", numberOfParameters);
        }
        parameterCount = numberOfParameters;
        return this;
    }

    public MethodBuilder withParameters(Object... parameterTypes) {
        this.parameterTypes = Arrays.stream(parameterTypes).map(type -> {
            if (type instanceof  TypeToken<?>) return type;
            if (type instanceof ClassProxy) return TypeToken.of(((ClassProxy) type).getDelegate());
            if (type instanceof Type) return TypeToken.of((Type) type);
            throw new IllegalArgumentException(String.format(
                    "You must pass a `Type`, `TypeToken` or `ClassProxy` to `withParameters`, but you passed a `%s`",
                    type.getClass().getSimpleName())
            );
        }).toArray(TypeToken<?>[]::new);
        if (parameterCount >= 0 && parameterTypes != null && parameterTypes.length != parameterCount) {
            failFormat("Number of parameters, %d, doesn't match the previously specified parameter count, %d",
                    parameterTypes.length, parameterCount);
        }
        return this;
    }

    // TODO: make a constructor for this?
    public MethodBuilder withReferenceType(String referenceType) {
        return withReferenceType(ReferenceType.valueOf(referenceType.toUpperCase()));
    }

    public MethodBuilder withReferenceType(ReferenceType referenceType) {
        this.referenceType = referenceType;
        return this;
    }

    public Method build() {

        Method rawMethod = Arrays.stream(declaringClass.getDeclaredMethods())
                .filter(m -> {
                    if (name.equals(ANY_METHOD_NAME)) return true;
                    return m.getName().equals(name);
                })
                .filter(m -> {
                    Type[] genericParameterTypes = m.getGenericParameterTypes();

                    if (parameterCount >= 0 && genericParameterTypes.length != parameterCount) return false;
                    if (parameterTypes == null) return true;

                    if (parameterTypes.length != genericParameterTypes.length) return false;

                    for (int i = 0; i < genericParameterTypes.length; i++) {
                        TypeToken<?> actualType = TypeToken.of(genericParameterTypes[i]);
                        TypeToken<?> expectedType = parameterTypes[i];
                        if (actualType.equals(expectedType)) continue; // TODO: change to isAssignableFrom?

                        if (!expectedType.isSupertypeOf(actualType)) return false;
                    }
                    return true;
                })
                .findFirst()
                .orElse(null);

        if (rawMethod == null) {
            failFormat(
                    "Expected the %s `%s` to define a method with the signature `%s`",
                    referenceType.getName(),
                    declaringClass.getSimpleName(),
                    methodSignature()
            );
        }

        Invokable<?, Object> method = Invokable.from(rawMethod);
        verifyVisibility(method);
        verifyStatic(method);
        verifyReturnType(rawMethod);
        verifyExceptions(rawMethod);

        if (name.equals(ANY_METHOD_NAME)) name = rawMethod.getName();

        return rawMethod;
    }

    private void verifyExceptions(Method method) {
        if (exceptionTypes == null) return;
        List<Class> declaredExceptionTypes = Arrays.asList(method.getExceptionTypes());
        boolean matches = true;
        if (exceptionTypes.length != declaredExceptionTypes.size()) matches = false;

        for (Class throwableClass : exceptionTypes) {
            if (!declaredExceptionTypes.contains(throwableClass)) {
                matches = false;
                break;
            }
        }

        if (!matches) {
            failFormat(
                    "Expected `%s` to throw exactly `%s`%s but it %s",
                    simpleName(declaringClass),
                    Arrays.stream(exceptionTypes).map(ReflectionUtils::simpleName).collect(joining(DELIMITER)),
                    exceptionTypes.length > 1 ? " (in any order)" : "",
                    declaredExceptionTypes.isEmpty() ?
                            "doesn't throw anything" :
                            String.format(
                                    "throws `%s`",
                                    declaredExceptionTypes.stream()
                                            .map(ReflectionUtils::simpleName)
                                            .collect(joining(DELIMITER)).replace("<>", ""))

            );
        }
    }

    private void verifyReturnType(Method rawMethod) {
        if (returnType != null) {
            assertEquals(
                    simpleName(returnType),
                    simpleName(rawMethod.getGenericReturnType()),
                    String.format(
                            "Expected `%s.%s` to return an instance of type `%s`",
                            declaringClass.getSimpleName(),
                            name,
                            simpleName(returnType)
                    ));
        }
    }

    private void verifyStatic(Invokable<?, Object> method) {
        if (isStatic && !method.isStatic()) {
            fail(String.format(
                    "Expected `%s.%s` to be static but it is not",
                    declaringClass.getSimpleName(),
                    name
            ));
        }
    }

    private void verifyVisibility(Invokable method) {
        if (visibility == null) return;

        if (!visibilityMatches(visibility, method)) {
            failFormat(
                    "Expected `%s.%s` to be %s but it is not",
                    declaringClass.getSimpleName(),
                    name,
                    visibility.getName()
            );
        }
    }

    private boolean visibilityMatches(Visibility visibility, Invokable method) {
        return !((visibility == Visibility.PUBLIC && !method.isPublic()) ||
                (visibility == Visibility.PROTECTED && !method.isProtected()) ||
                (visibility == Visibility.PACKAGE_PRIVATE && !method.isPackagePrivate()) ||
                (visibility == Visibility.PRIVATE && !method.isPrivate())
        );
    }

    public String methodSignature() {
        String paramString = "";
        if (parameterTypes != null) {
            paramString = Arrays.stream(parameterTypes)
                    .map(ReflectionUtils::simpleName)
                    .collect(joining(DELIMITER));
        } else if (parameterCount > 0) {
            paramString = String.join(", ", Collections.nCopies(parameterCount, "<?>"));
        }

        String exceptionsString = "";
        if (exceptionTypes != null) {
            exceptionsString = " throws " + Arrays.stream(exceptionTypes)
                    .map(ReflectionUtils::simpleName)
                    .collect(joining(DELIMITER));
        }

        return String.format(
                "%s%s%s%s(%s)%s",
                visibility != null ? visibility.toMethodSignatureString() : "",
                isStatic ? "static " : "",
                returnType == null ? "" : simpleName(returnType) + " ",
                name,
                paramString,
                exceptionsString
        );
    }

    private void failFormat(String message, Object... args) {
        fail(String.format(message, args));
    }

    public String getName() {
        return name;
    }

    public MethodBuilder throwsExactly(Class<? extends Throwable>... exceptionTypes) {
        this.exceptionTypes = exceptionTypes;
        return this;
    }

    @SuppressWarnings("unchecked")
    public MethodBuilder throwsExactly(ClassProxy... exceptionTypes) {
        this.exceptionTypes = Arrays.stream(exceptionTypes).map(ClassProxy::getDelegate).toArray(Class[]::new);
        return this;
    }

}
