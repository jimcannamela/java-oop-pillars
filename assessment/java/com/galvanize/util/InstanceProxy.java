package com.galvanize.util;

import com.google.common.reflect.Invokable;

import java.util.HashMap;
import java.util.List;

import static com.galvanize.util.ReflectionUtils.failFormat;

public class InstanceProxy {

    private final HashMap<String, List<Invokable>> methods;
    private final Object delegate;

    public static InstanceProxy wrap(Object instance, ClassProxy classProxy) {
        return new InstanceProxy(instance, classProxy.getMethods());
    }

    public InstanceProxy(Object delegate, HashMap<String, List<Invokable>> methods) {
        this.delegate = delegate;
        this.methods = methods;
    }

    public Object getDelegate() {
        return delegate;
    }

    public Object invoke(String methodName, Object... args) {
        try {
            return ReflectionUtils.invoke(methods, delegate, methodName, args);
        } catch (Throwable throwable) {
            failFormat(
                    "Expected `%s.%s` to not throw an exception, but it threw `%s`",
                    delegate.getClass().getSimpleName(),
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
        Class expectedType = exceptionProxy.getDelegate();
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

}
