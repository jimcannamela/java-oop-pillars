package com.galvanize.util;

import com.google.common.reflect.AbstractInvocationHandler;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public class ConcreteClassBuilder {

    private final ClassProxy classProxy;
    private final Map<String, FunctionThrows<Object[], Object>> methods = new HashMap<>();

    public ConcreteClassBuilder(ClassProxy classProxy) {
        this.classProxy = classProxy;
    }

    public ConcreteClassBuilder intercept(String methodName, String value) {
        methods.put(methodName, args -> value);
        return this;
    }

    public ConcreteClassBuilder intercept(String methodName, FunctionThrows<Object[], Object> fn) {
        methods.put(methodName, fn);
        return this;
    }

    public InstanceProxy build() {
        Object instance = Proxy.newProxyInstance(
                this.getClass().getClassLoader(),
                new Class[]{classProxy.getDelegate()},
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

        return new InstanceProxy(instance, classProxy);
    }

}
