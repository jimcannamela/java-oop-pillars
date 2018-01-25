package com.galvanize.util;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.AbstractInvocationHandler;
import com.google.common.reflect.Invokable;
import com.google.common.reflect.TypeToken;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;

import static com.galvanize.util.ReflectionUtils.*;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClassProxy implements Type {

    private Class<?> delegate = null;
    private ReferenceType referenceType = ReferenceType.CLASS;

    private final HashMap<String, List<Invokable>> methods = new HashMap<>();
    private final List<Invokable> constructors = new ArrayList<>();

    public static ClassProxy of(Class delegate) {
        return new ClassProxy(delegate);
    }

    public ClassProxy(String name) {
        try {
            delegate = Class.forName(name);
        } catch (ClassNotFoundException e) {
            failFormat("Expected to find a type named `%s` but did not", name);
        }
    }

    public ClassProxy(Class delegate) {
        this.delegate = delegate;
    }

    public Class<?> getDelegate() {
        return delegate;
    }

    public HashMap<String, List<Invokable>> getMethods() {
        return methods;
    }

    public static ClassProxy classNamed(String name) {
        return new ClassProxy(name).ensureClass();
    }

    public static ClassProxy interfaceNamed(String name) {
        return new ClassProxy(name).ensureInterface();
    }

    private ClassProxy ensureClass() {
        if (getDelegate().isInterface()) {
            failFormat("Expected `%s` to be a class, but it was an interface", getDelegate().getSimpleName());
        }
        if (getDelegate().isEnum()) {
            failFormat("Expected `%s` to be a class, but it was an enum", getDelegate().getSimpleName());
        }
        this.referenceType = ReferenceType.CLASS;
        return this;
    }

    private ClassProxy ensureInterface() {
        if (!getDelegate().isInterface()) {
            failFormat("Expected `%s` to be an interface, but it is not", getDelegate().getSimpleName());
        }
        this.referenceType = ReferenceType.INTERFACE;
        return this;
    }

    public ClassProxy ensureMethod(Function<MethodBuilder, MethodBuilder> fn) {
        MethodBuilder builder = fn.apply(new MethodBuilder(getDelegate()));
        builder.withReferenceType(referenceType);
        Method method = builder.build();
        Invokable invokable = Invokable.from(method);

        List<Invokable> items = methods.get(builder.getName());
        if (items == null) {
            items = new ArrayList<>();
            methods.put(builder.getName(), items);
        }
        items.add(invokable);
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
                    Arrays.stream(args)
                            .map(ReflectionUtils::simpleName)
                            .collect(joining(","))
            );
        }
        return this;
    }

    public InstanceProxy newInstance(Object... args) {
        if (args.length == 0) {
            try {
                return new InstanceProxy(getDelegate().getDeclaredConstructor().newInstance(), methods);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                failFormat("Could not instantiate `%s` with no args", getDelegate().getSimpleName());
            }
        } else {
            Object[] mappedArgs = Arrays.stream(args)
                    .map(arg -> arg instanceof InstanceProxy ? ((InstanceProxy) arg).getDelegate() : arg)
                    .toArray();

            Optional<Invokable> matchedConstructor = bestMatch(constructors, mappedArgs);
            if (matchedConstructor.isPresent()) {
                try {
                    return new InstanceProxy(matchedConstructor.get().invoke(delegate, mappedArgs), methods);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    failFormat("Could not instantiate `%s` with no args", getDelegate().getSimpleName());
                }
            }
            failFormat(
                    "Could not find a constructor on `%s` that matches `%s`",
                    getDelegate().getSimpleName(),
                    Arrays.stream(mappedArgs).map(Object::toString).collect(joining(DELIMITER))
            );
        }
        return null;
    }

    public Object invoke(String methodName, Object... args) {
        try {
            return ReflectionUtils.invoke(methods, delegate, methodName, args);
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
        return ReflectionUtils.invoke(methods, delegate, methodName, args);
    }

    public Throwable assertInvokeThrows(ClassProxy exceptionProxy, String methodName, Object... args) {
        return assertInvokeThrows(exceptionProxy.delegate, methodName, args);
    }

    public Throwable assertInvokeThrows(Class expectedType, String methodName, Object... args) {
        try {
            invokeExpectingException(methodName, args);
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

    public ClassProxy ensureImplements(ClassProxy parent) {
        ensureImplements(parent.getDelegate());
        methods.putAll(parent.methods); // TODO: reference the parent's methods, and then look them up the chain
        return this;
    }

    public ClassProxy ensureImplements(Class<?> parent) {
        assertEquals(
                true,
                parent.isAssignableFrom(getDelegate()),
                String.format(
                        "Expected the `%s` class to implement the `%s` interface but it does not",
                        getDelegate().getSimpleName(),
                        parent.getSimpleName())
        );

        return this;
    }

    public ClassProxy getSuperclassProxy() {
        if (delegate.getSuperclass() == null) {
            failFormat("Cannot get superclass of `%s`", delegate.getSimpleName());
        }
        return ClassProxy.of(delegate.getSuperclass());
    }

    public Enhancer subclassWith(String methodName, Object result) {
        return subclassWith(methods -> methods.put(methodName, result));
    }

    public Enhancer subclassWith(Function<ImmutableMap.Builder<String, Object>, ImmutableMap.Builder<String, Object>> fn) {
        ImmutableMap<String, Object> simple = fn.apply(ImmutableMap.builder()).build();
        return subclassWithComplexMethods(methods -> {
            simple.forEach((key, value) -> {
                methods.put(key, args -> value);
            });
            return methods;
        });
    }

    public Enhancer subclassWithComplexMethods(Function<ImmutableMap.Builder<String, FunctionThrows<Object[], Object>>, ImmutableMap.Builder<String, FunctionThrows<Object[], Object>>> fn) {
        ImmutableMap<String, FunctionThrows<Object[], Object>> methods = fn.apply(ImmutableMap.builder()).build();

        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(delegate);
        enhancer.setCallback((MethodInterceptor) (obj, method, args, proxy) -> {
            if (method.getDeclaringClass() != Object.class) {
                FunctionThrows<Object[], Object> returnValueFn = methods.getOrDefault(method.getName(), null);
                if (returnValueFn == null)
                    throw new RuntimeException(String.format("Could not call `%s` on `%s`", method.getName(), obj));
                return returnValueFn.apply(args);
            } else {
                return proxy.invokeSuper(obj, args);
            }
        });
        return enhancer;
    }

    public Object instanceImplementing(String methodName, Object result) {
        return instanceImplementing(methods -> methods.put(methodName, result));
    }

    public Object instanceImplementing(Function<ImmutableMap.Builder<String, Object>, ImmutableMap.Builder<String, Object>> fn) {
        ImmutableMap<String, Object> simple = fn.apply(ImmutableMap.builder()).build();
        return instanceImplementingWithComplexMethods(methods -> {
            simple.forEach((key, value) -> {
                methods.put(key, args -> value);
            });
            return methods;
        });
    }

    public Object instanceImplementingWithComplexMethods(Function<ImmutableMap.Builder<String, FunctionThrows<Object[], Object>>, ImmutableMap.Builder<String, FunctionThrows<Object[], Object>>> fn) {
        ImmutableMap<String, FunctionThrows<Object[], Object>> methods = fn.apply(ImmutableMap.builder()).build();

        return Proxy.newProxyInstance(
                this.getClass().getClassLoader(),
                new Class[]{delegate},
                new AbstractInvocationHandler() {
                    @Override
                    protected Object handleInvocation(Object proxy, Method method, Object[] args) throws Throwable {
                        FunctionThrows<Object[], Object> returnValueFn = methods.getOrDefault(method.getName(), null);
                        if (returnValueFn == null) {
                            throw new RuntimeException(String.format("Could not call `%s` on `%s`", method.getName(), proxy));
                        }
                        return returnValueFn.apply(args);
                    }
                });
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
        if (!Exception.class.isAssignableFrom(delegate)) {
            failFormat(
                    "Expected `%s` to inherit from `Exception` but it did not",
                    delegate.getSimpleName()
            );
        }

        return this;
    }

    public Constructor<?>[] getConstructors() {
        return delegate.getConstructors();
    }

}
