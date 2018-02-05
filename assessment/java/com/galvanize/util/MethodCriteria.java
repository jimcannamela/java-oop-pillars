package com.galvanize.util;

import com.google.common.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static com.galvanize.util.ReflectionUtils.joinSimpleNames;

public class MethodCriteria {

    public static final String ANY_METHOD_NAME = "*any name*";

    private Optional<Visibility> visibility = Optional.empty();
    private Optional<Boolean> isStatic = Optional.empty();
    // FIXME pab: returnType should be an Optional<TypeToken> to support void
    private Optional<Object> returnType = Optional.empty();
    private Optional<String> name = Optional.empty();
    // FIXME pab: change to List from array
    private Optional<TypeToken<?>[]> parameterTypes = Optional.empty();
    private Optional<Integer> parameterCount = Optional.empty();
    // FIXME pab: change to List<TypeToken<? extends Throwable>>
    private Optional<Class<? extends Throwable>[]> exceptionTypes = Optional.empty();

    public MethodCriteria() {
    }

    public Optional<Visibility> getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility == null ? Optional.empty() : Optional.of(visibility);
    }

    public Optional<Boolean> isStatic() {
        return isStatic;
    }

    public void setStatic(Boolean isStatic) {
        this.isStatic = isStatic == null ? Optional.empty() : Optional.of(isStatic);
    }

    public Optional<Object> getReturnType() {
        return returnType;
    }

    public void setReturnType(Object returnType) {
        this.returnType = returnType == null ? Optional.empty() : Optional.of(returnType);
    }

    public Optional<String> getName() {
        return name;
    }

    public void setName(String name) {
        this.name = (name == null || name.trim().isEmpty()) ? Optional.empty() : Optional.of(name);
    }

    public Optional<TypeToken<?>[]> getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(Object... parameterTypes) {
        setParameterTypes(Arrays.stream(parameterTypes).map(type -> {
            if (type instanceof TypeToken<?>) return type;
            if (type instanceof ClassProxy) return TypeToken.of(((ClassProxy) type).getDelegate());
            if (type instanceof Type) return TypeToken.of((Type) type);
            throw new IllegalArgumentException(String.format(
                    "You must pass a `Type`, `TypeToken` or `ClassProxy` to `withParameters`, but you passed a `%s`",
                    type.getClass().getSimpleName())
            );
        }).toArray(TypeToken<?>[]::new));
    }

    public void setParameterTypes(TypeToken<?>[] parameterTypes) {
        if (parameterTypes == null) {
            this.parameterTypes = Optional.empty();
        } else if (parameterCount.isPresent() && parameterTypes.length != parameterCount.get()) {
            throw new IllegalArgumentException(String.format(
                    "Number of parameters, %d, doesn't match the previously specified parameter count, %d",
                    parameterTypes.length, parameterCount.get()));
        }
        this.parameterTypes = Optional.of(parameterTypes);
    }

    public Optional<Integer> getParameterCount() {
        return parameterCount;
    }

    public void setParameterCount(Integer numberOfParameters) {
        if (numberOfParameters == null) {
            this.parameterCount = Optional.empty();

        } else if (numberOfParameters < 0) {
            throw new IllegalArgumentException(String.format(
                    "Specified parameter count, %d, is invalid",
                    numberOfParameters)
            );

        } else if (parameterTypes.isPresent() && parameterTypes.get().length != numberOfParameters) {
            throw new IllegalArgumentException(String.format(
                    "Parameter count, %d, doesn't match the number of previously specified parameters, %d",
                    numberOfParameters, parameterTypes.get().length)
            );
        }
        this.parameterCount = Optional.of(numberOfParameters);
    }

    public Optional<Class<? extends Throwable>[]> getExceptionTypes() {
        return exceptionTypes;
    }

    public void setExceptionTypes(Class<? extends Throwable>[] exceptionTypes) {
        this.exceptionTypes = exceptionTypes == null ? Optional.empty() : Optional.of(exceptionTypes);
    }

    public String methodSignature() {
        String paramString = "";
        if (parameterTypes.isPresent()) {
            paramString = joinSimpleNames(Arrays.stream(parameterTypes.get()));
        } else if (parameterCount.isPresent()) {
            paramString = String.join(", ", Collections.nCopies(parameterCount.get(), "<?>"));
        }

        String exceptionsString = "";
        if (exceptionTypes.isPresent()) {
            exceptionsString = " throws " + joinSimpleNames(Arrays.stream(exceptionTypes.get()));
        }

        return String.format(
                "%s%s%s%s(%s)%s",
                visibility.isPresent() ? visibility.get().toMethodSignatureString() : "",
                isStatic.orElse(false) ? "static " : "",
                returnType.isPresent() ? ReflectionUtils.simpleName(returnType.get()) + " " : "",
                name.isPresent() ? name.get() : ANY_METHOD_NAME,
                paramString,
                exceptionsString
        );
    }
}