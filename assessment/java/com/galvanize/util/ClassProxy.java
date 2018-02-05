package com.galvanize.util;

import com.google.common.reflect.Invokable;
import com.google.common.reflect.TypeToken;
import org.opentest4j.AssertionFailedError;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.function.Function;

import static com.galvanize.util.ReflectionUtils.*;
import static java.util.stream.Collectors.joining;

public class ClassProxy implements Type {

    private Class<?> delegate = null;
    private ReferenceType referenceType = ReferenceType.CLASS;

    private final VerifiedInvokables methods;
    private final VerifiedInvokables constructors;

    public static ClassProxy of(Class delegate) {
        return new ClassProxy(delegate);
    }

    public static ClassProxy of(TypeToken token) {
        return new ClassProxy(token.getRawType());
    }

    public static ClassProxy classNamed(String name) {
        return new ClassProxy(name).ensureClass();
    }

    public static ClassProxy interfaceNamed(String name) {
        return new ClassProxy(name).ensureInterface();
    }

    public ClassProxy(String name) {
        try {
            delegate = Class.forName(name);
        } catch (ClassNotFoundException e) {
            failFormat("Expected to find a type named `%s` but did not", name);
        }
        constructors = new VerifiedInvokables(delegate);
        methods = new VerifiedInvokables(delegate);
    }

    public ClassProxy(Class<?> delegate) {
        this.delegate = delegate;
        constructors = new VerifiedInvokables(delegate);
        methods = new VerifiedInvokables(delegate);
    }

    public Class<?> getDelegate() {
        return delegate;
    }

    public VerifiedInvokables getVerifiedMethods() {
        return methods;
    }

    public ClassProxy getSuperclassProxy() {
        if (delegate.getSuperclass() == null) {
            failFormat("Cannot get superclass of `%s`", delegate.getSimpleName());
        }
        return ClassProxy.of(delegate.getSuperclass());
    }

    public ClassProxy ensureClass() {
        if (getDelegate().isInterface()) {
            failFormat("Expected `%s` to be a class, but it was an interface", getDelegate().getSimpleName());
        }
        if (getDelegate().isEnum()) {
            failFormat("Expected `%s` to be a class, but it was an enum", getDelegate().getSimpleName());
        }
        this.referenceType = ReferenceType.CLASS;
        return this;
    }

    public ClassProxy ensureInterface() {
        if (!getDelegate().isInterface()) {
            failFormat("Expected `%s` to be an interface, but it is not", getDelegate().getSimpleName());
        }
        this.referenceType = ReferenceType.INTERFACE;
        return this;
    }

    public ClassProxy ensureCheckedException() {
        if (RuntimeException.class.isAssignableFrom(delegate)) {
            failFormat(
                    "Expected `%s` to be a checked exception, but it inherits from `RuntimeException`",
                    delegate.getSimpleName()
            );
        }
        if (Error.class.isAssignableFrom(delegate)) {
            failFormat(
                    "Expected `%s` to be a checked exception, but it inherits from `Error`",
                    delegate.getSimpleName()
            );
        }
        if (!Throwable.class.isAssignableFrom(delegate)) {
            failFormat(
                    "Expected `%s` to inherit from `Throwable` but it did not",
                    delegate.getSimpleName()
            );
        }

        return this;
    }

    public ClassProxy ensureMethod(Method method) {
        if (! Arrays.asList(getDelegate().getDeclaredMethods()).contains(method)) {
            failFormat(
                    "Expected the %s `%s` to define a method with the signature `%s`",
                    referenceType.getName(),
                    delegate.getSimpleName(),
                    method.toGenericString()
            );
        }
        methods.add(method);
        return this;
    }

    public ClassProxy ensureMethod(String methodName, Object... parameterTypes) {
        return ensureMethod(m -> m.named(methodName).withParameters(parameterTypes));
    }

    public ClassProxy ensureMethod(Function<MethodBuilder, MethodBuilder> fn) {
        MethodBuilder builder = fn.apply(new MethodBuilder(getDelegate()));
        builder.withReferenceType(referenceType);
        methods.add(builder.build());
        return this;
    }

    public ClassProxy ensureMainMethod() {
        return this.ensureMethod(method -> method
                .isPublic()
                .isStatic()
                .returns(Void.TYPE)
                .named("main")
                .withParameters(String[].class));
    }

    public ClassProxy ensureGetter(Class property) {
        return ensureGetter(property.getSimpleName(), property);
    }

    public ClassProxy ensureGetter(String name, Type type) {
        String initialCap = name.substring(0, 1).toUpperCase() + name.substring(1);
        String getterName = "get" + initialCap;
        return this
                .ensureMethod(method -> method
                        .isPublic()
                        .named(getterName)
                        .returns(type));
    }

    public ClassProxy ensureGetter(String name, TypeToken<?> type) {
        String initialCap = name.substring(0, 1).toUpperCase() + name.substring(1);
        String getterName = "get" + initialCap;
        return this
                .ensureMethod(method -> method
                        .isPublic()
                        .named(getterName)
                        .returns(type));
    }

    public ClassProxy ensureConstructorCount(int numberOfConstructors) {
        if (numberOfConstructors < 0) {
            throw new IllegalArgumentException(String.format("Specified constructor count, %d, is invalid",
            numberOfConstructors));
        }
        Constructor<?>[] constructors = getDelegate().getConstructors();
        if (constructors.length != numberOfConstructors) {
            if (numberOfConstructors == 1) {
                failFormat("Expected `%s` to have exactly one constructor, but found %d",
                        getDelegate().getSimpleName(),
                        constructors.length
                );
            } else {
                failFormat("Expected `%s` to have exactly %d constructors, but found %d",
                        getDelegate().getSimpleName(),
                        numberOfConstructors,
                        constructors.length
                );
            }
        }
        return this;
    }

    public ClassProxy ensureConstructor(Object... args) {
        Class[] classArgs = Arrays.stream(args).map(object -> {
            if (object instanceof Class) {
                return (Class) object;
            } else if (object instanceof ClassProxy) {
                return ((ClassProxy) object).getDelegate();
            } else if (object instanceof TypeToken<?>) {
                return ((TypeToken<?>) object).getRawType();
            } else {
                throw new IllegalArgumentException(String.format(
                        "You must pass a `Class`, `TypeToken` or `ClassProxy` to `ensureConstructor`, but you passed `(%s) %s`",
                        object.getClass().getSimpleName(),
                        object.toString()
                ));
            }
        }).toArray(Class[]::new);

        try {
            Constructor constructor = getDelegate().getConstructor(classArgs);
            Invokable invokable = Invokable.from(constructor);
            constructors.add(invokable);
        } catch (NoSuchMethodException e) {
            failFormat(
                    "Expected `%s` to define a constructor with the signature `%s(%s)`",
                    getDelegate().getSimpleName(),
                    getDelegate().getSimpleName(),
                    joinSimpleNames(Arrays.stream(args))
            );
        }
        return this;
    }

    public ClassProxy ensureImplements(ClassProxy parent) {
        ensureImplements(parent.getDelegate());
        methods.addAll(parent.methods);
        return this;
    }

    public ClassProxy ensureImplements(Class<?> parent) {
        if (!parent.isAssignableFrom(getDelegate())) {
            failFormat(
                    "Expected the `%s` class to implement the `%s` interface but it does not",
                    getDelegate().getSimpleName(),
                    parent.getSimpleName());
        }

        return this;
    }

    public InstanceProxy newInstance(Object... args) {
        if (args.length == 0) {
            try {
                return new InstanceProxy(getDelegate().getDeclaredConstructor().newInstance(), this);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                failFormat("Could not instantiate `%s` with no args", getDelegate().getSimpleName());
            }
        } else {
            Object[] mappedArgs = ReflectionUtils.resolveInstanceProxies(args);

            try {
                return new InstanceProxy(constructors.newInstance(mappedArgs), this);
            } catch (AssertionFailedError e) {
                throw e;
            } catch (Throwable throwable) {
                failFormat(
                        "Could not invoke constructor on `%s` for args `%s`\n%s: %s\n",
                        getDelegate().getSimpleName(),
                        Arrays.stream(mappedArgs).map(o -> o != null ? o.toString() : "null").collect(joining(DELIMITER)),
                        throwable.getClass().getSimpleName(),
                        throwable.getMessage()
                );
            }
        }
        return null;
    }

    public SubclassBuilder subclass() {
        return new SubclassBuilder(this);
    }

    public ConcreteClassBuilder concreteClass() {
        return new ConcreteClassBuilder(this);
    }

    public Object invoke(Method method, Object... args) {
        try {
            return methods.invoke(delegate, Invokable.from(method), args);
        } catch (Throwable throwable) {
            failFormat(
                    "Expected `%s.%s` to not throw an exception, but it threw `%s`",
                    delegate.getSimpleName(),
                    method.getName(),
                    throwable.toString()
            );
            return null;
        }
    }

    public Object invokeExpectingException(Method method, Object... args) throws Throwable {
        return methods.invoke(delegate, Invokable.from(method), args);
    }

    public Throwable assertInvokeThrows(ClassProxy exceptionProxy, Method method, Object... args) {
        return assertInvokeThrows(exceptionProxy.delegate, method, args);
    }

    public Throwable assertInvokeThrows(Class expectedType, Method method, Object... args) {
        return ReflectionUtils.assertInvokeThrows(methods, delegate, expectedType, Invokable.from(method), args);
    }

    public Object invoke(String methodName, Object... args) {
        try {
            return methods.invoke(delegate, methodName, args);
        } catch (Throwable throwable) {
            failFormat(
                    "Expected `%s.%s` to not throw an exception, but it threw `%s`",
                    delegate.getSimpleName(),
                    methodName,
                    throwable.toString()
            );
            return null;
        }
    }

    public Object invokeExpectingException(String methodName, Object... args) throws Throwable {
        return methods.invoke(delegate, methodName, args);
    }

    public Throwable assertInvokeThrows(ClassProxy exceptionProxy, String methodName, Object... args) {
        return assertInvokeThrows(exceptionProxy.delegate, methodName, args);
    }

    public Throwable assertInvokeThrows(Class expectedType, String methodName, Object... args) {
        return ReflectionUtils.assertInvokeThrows(methods, delegate, expectedType, methodName, args);
    }

}
