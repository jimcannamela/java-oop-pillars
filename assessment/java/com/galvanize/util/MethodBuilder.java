package com.galvanize.util;

import com.google.common.reflect.TypeToken;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

import static com.galvanize.util.ReflectionUtils.joinSimpleNames;
import static com.galvanize.util.ReflectionUtils.simpleName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class MethodBuilder {

    private final Class declaringClass;
    private final MethodCriteria criteria = new MethodCriteria();
    private ReferenceType referenceType = ReferenceType.CLASS;

    public MethodBuilder(Class declaringClass) {
        this.declaringClass = declaringClass;
    }

    public Class getDeclaringClass() {
        return declaringClass;
    }

    public MethodBuilder named(String name) {

        this.criteria.setName(name);
        return this;
    }

    public MethodBuilder isStatic() {
        return isStatic(true);
    }

    public MethodBuilder isStatic(boolean isStatic) {
        this.criteria.setStatic(isStatic);
        return this;
    }

    public MethodBuilder isPublic() {
        criteria.setVisibility(Visibility.PUBLIC);
        return this;
    }

    public MethodBuilder isProtected() {
        criteria.setVisibility(Visibility.PROTECTED);
        return this;
    }

    public MethodBuilder isPrivate() {
        criteria.setVisibility(Visibility.PRIVATE);
        return this;
    }

    public MethodBuilder isPackagePrivate() {
        this.criteria.setVisibility(Visibility.PACKAGE_PRIVATE);
        return this;
    }

    public MethodBuilder returns(ClassProxy returnTypeProxy) {
        return returns(returnTypeProxy.getDelegate());
    }

    public MethodBuilder returns(Type returnType) {
        this.criteria.setReturnType(returnType);
        return this;
    }

    public MethodBuilder returns(TypeToken returnType) {
        this.criteria.setReturnType(returnType);
        return this;
    }

    public MethodBuilder withParameterCount(int numberOfParameters) {
        criteria.setParameterCount(numberOfParameters);
        return this;
    }

    public MethodBuilder withParameters(Object... parameterTypes) {
        this.criteria.setParameterTypes(parameterTypes);
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

        List<Method> methods = Arrays.asList(declaringClass.getDeclaredMethods());
        Method rawMethod;
        if (criteria.getName().isPresent()) {
            /*
             * Returns the first method matching the specified name and parameter types.
             * If no parameters are specified then it matches only methods with no arguments.
             */
            rawMethod = methods.stream()
                    .filter(m -> m.getName().equals(criteria.getName().get()))
                    .filter(m -> {
                        Type[] actualParameterTypes = m.getGenericParameterTypes();
                        TypeToken<?>[] specifiedParameterTypes= criteria.getParameterTypes()
                                .orElse(new TypeToken[criteria.getParameterCount().orElse(0)]);
                        return MethodMatcher.typesMatch(actualParameterTypes, specifiedParameterTypes);
                    })
                    .findFirst()
                    .orElse(null);

            if (rawMethod != null) {
                verifyVisibility(rawMethod);
                verifyStatic(rawMethod);
                verifyReturnType(rawMethod);
                verifyExceptions(rawMethod);
            }
        } else {
            rawMethod = new MethodMatcher(criteria).find(methods);
        }

        if (rawMethod == null) {
            failFormat(
                    "Expected the %s `%s` to define a method with the signature `%s`",
                    referenceType.getName(),
                    declaringClass.getSimpleName(),
                    criteria.methodSignature()
            );
        }

        return rawMethod;
    }

    private void verifyExceptions(Method method) {
        criteria.getExceptionTypes().ifPresent(specifiedTypes -> {
            List<Class> declaredExceptionTypes = Arrays.asList(method.getExceptionTypes());
            boolean matches = specifiedTypes.length == declaredExceptionTypes.size();
            if (matches) {
                for (Class throwableClass : specifiedTypes) {
                    if (!declaredExceptionTypes.contains(throwableClass)) {
                        matches = false;
                        break;
                    }
                }
            }

            if (!matches) {
                failFormat(
                        "Expected `%s` to throw exactly `%s`%s but it %s",
                        simpleName(declaringClass),
                        joinSimpleNames(Arrays.stream(specifiedTypes)),
                        specifiedTypes.length > 1 ? " (in any order)" : "",
                        declaredExceptionTypes.isEmpty()
                                ? "doesn't throw anything"
                                : String.format("throws `%s`", joinSimpleNames(declaredExceptionTypes.stream()))

                );
            }
        });
    }

    private void verifyReturnType(Method rawMethod) {
        criteria.getReturnType().ifPresent(specifiedReturnType -> {
            assertEquals(
                    simpleName(specifiedReturnType),
                    simpleName(rawMethod.getGenericReturnType()),
                    String.format(
                            "Expected `%s.%s` to return an instance of type `%s`",
                            declaringClass.getSimpleName(),
                            rawMethod.getName(),
                            simpleName(specifiedReturnType)
                    ));
        });
    }

    private void verifyStatic(Method method) {
        criteria.isStatic().ifPresent(specifiedStatic -> {
            if (!specifiedStatic.equals(MethodMatcher.isStatic(method))) {
                failFormat(
                        "Expected `%s.%s` to be static but it is not",
                        declaringClass.getSimpleName(),
                        method.getName()
                );
            }
        });
    }

    private void verifyVisibility(Method method) {
        criteria.getVisibility().ifPresent(specifiedVisibility -> {
            if (Visibility.of(method) != specifiedVisibility) {
                failFormat(
                        "Expected `%s.%s` to be %s but it is not",
                        declaringClass.getSimpleName(),
                        method.getName(),
                        specifiedVisibility.getName()
                );
            }
        });
    }

    public String methodSignature() {
        return criteria.methodSignature();
    }

    private void failFormat(String message, Object... args) {
        fail(String.format(message, args));
    }

    public String getName() {
        return criteria.getName().orElse(MethodCriteria.ANY_METHOD_NAME);
    }

    public MethodBuilder throwsExactly(Class<? extends Throwable>... exceptionTypes) {
        criteria.setExceptionTypes(exceptionTypes);
        return this;
    }

    @SuppressWarnings("unchecked")
    public MethodBuilder throwsExactly(ClassProxy... exceptionTypes) {
        criteria.setExceptionTypes(Arrays.stream(exceptionTypes).map(ClassProxy::getDelegate).toArray(Class[]::new));
        return this;
    }

}
