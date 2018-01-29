package com.galvanize.util;

import com.google.common.reflect.Invokable;

import java.util.HashMap;
import java.util.List;

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

    public Object invoke(String methodName, Object... args) {
        try {
            return ReflectionUtils.invoke(classProxy.getMethods(), delegate, methodName, args);
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
        return ReflectionUtils.invoke(classProxy.getMethods(), delegate, methodName, args);
    }

    public Throwable assertInvokeThrows(ClassProxy exceptionProxy, String methodName, Object... args) {
        return assertInvokeThrows(exceptionProxy.getDelegate(), methodName, args);
    }

    public Throwable assertInvokeThrows(Class<?> expectedType, String methodName, Object... args) {
        return ReflectionUtils.assertInvokeThrows(classProxy.getMethods(), delegate, expectedType, methodName, args);
    }

}
