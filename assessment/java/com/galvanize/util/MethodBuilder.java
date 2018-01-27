package com.galvanize.util;

import com.google.common.reflect.Invokable;
import com.google.common.reflect.TypeToken;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

import static com.galvanize.util.ReflectionUtils.DELIMITER;
import static com.galvanize.util.ReflectionUtils.simpleName;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

// TODO: since name is required, make this a constructor parameter
public class MethodBuilder {

    private final Class declaringClass;
    private boolean isStatic;
    private Object returnType;
    private Object[] parameterTypes;
    private ReferenceType referenceType = ReferenceType.CLASS;
    private String name;
    private Visibility visibility;
    private Class<? extends Throwable>[] exceptionTypes;

    public MethodBuilder(Class declaringClass) {
        this.declaringClass = declaringClass;
    }

    public Class getDeclaringClass() {
        return declaringClass;
    }

    public MethodBuilder named(String name) {
        this.name = name;
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

    public MethodBuilder withParameters(Object... parameterTypes) {
        this.parameterTypes = Arrays.stream(parameterTypes).map(type -> {
            if (type instanceof ClassProxy) return ((ClassProxy) type).getDelegate();
            return type;
        }).toArray(Object[]::new);
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
        String methodSignature = methodSignature();

        Method rawMethod = Arrays.stream(declaringClass.getDeclaredMethods())
                .filter(m -> m.getName().equals(name))
                .filter(m -> {
                    Type[] genericParameterTypes = m.getGenericParameterTypes();
                    if (parameterTypes == null) parameterTypes = new Object[]{};
                    if (parameterTypes.length != genericParameterTypes.length) return false;

                    for (int i = 0; i < genericParameterTypes.length; i++) {
                        Object actualType = genericParameterTypes[i];
                        Object expectedType = parameterTypes[i];
                        if (actualType.equals(expectedType)) continue; // TODO: change to isAssignableFrom?

                        TypeToken<?> token = (TypeToken<?>) expectedType;
                        if (!token.isSupertypeOf((Type) actualType)) return false;
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
                    methodSignature
            );
        }

        Invokable<?, Object> method = Invokable.from(rawMethod);
        verifyVisibility(method);
        verifyStatic(method);
        verifyReturnType(rawMethod);
        verifyExceptions(rawMethod);

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
        if (name == null || name.isEmpty()) {
            failFormat("You must specify the name of the method on `%s`.", declaringClass.getSimpleName());
        }
        String paramString = "";
        if (parameterTypes != null) {
            paramString = Arrays.stream(parameterTypes)
                    .map(ReflectionUtils::simpleName)
                    .collect(joining(DELIMITER));
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
