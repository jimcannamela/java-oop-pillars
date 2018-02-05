package com.galvanize.util;

import com.google.common.reflect.Invokable;

import java.lang.reflect.Method;

import static com.galvanize.util.ReflectionUtils.failFormat;

public class InstanceProxy {

    private final Object delegate;
    private final ClassProxy classProxy;

    public InstanceProxy(Object instance, ClassProxy classProxy) {
        this.delegate = instance;
        this.classProxy = classProxy;
    }

    public Object getDelegate() {
        return delegate;
    }

    public Object invoke(Method method, Object... args) {
        try {
            return getMethods().invoke(delegate, Invokable.from(method), args);
        } catch (Throwable throwable) {
            failFormat(
                    "Expected `%s.%s` to not throw an exception, but it threw `%s`",
                    classProxy.getDelegate().getSimpleName(),
                    method.getName(),
                    throwable.toString()
            );
            return null;
        }
    }

    private VerifiedInvokables getMethods() {
        return classProxy.getVerifiedMethods();
    }

    public Object invokeExpectingException(Method method, Object... args) throws Throwable {
        return getMethods().invoke(delegate, Invokable.from(method), args);
    }

    public Throwable assertInvokeThrows(ClassProxy exceptionProxy, Method method, Object... args) {
        return assertInvokeThrows(exceptionProxy.getDelegate(), method, args);
    }

    public Throwable assertInvokeThrows(Class<?> expectedType, Method method, Object... args) {
        return ReflectionUtils.assertInvokeThrows(getMethods(), delegate, expectedType, Invokable.from(method), args);
    }

    public Object invoke(String methodName, Object... args) {
        try {
            return getMethods().invoke(delegate, methodName, args);
        } catch (Throwable throwable) {
            failFormat(
                    "Expected `%s.%s` to not throw an exception, but it threw `%s`",
                    classProxy.getDelegate().getSimpleName(),
                    methodName,
                    throwable.toString()
            );
            return null;

        }
    }

    public Object invokeExpectingException(String methodName, Object... args) throws Throwable {
        return getMethods().invoke(delegate, methodName, args);
    }

    public Throwable assertInvokeThrows(ClassProxy exceptionProxy, String methodName, Object... args) {
        return assertInvokeThrows(exceptionProxy.getDelegate(), methodName, args);
    }

    public Throwable assertInvokeThrows(Class<?> expectedType, String methodName, Object... args) {
        return ReflectionUtils.assertInvokeThrows(getMethods(), delegate, expectedType, methodName, args);
    }

}
