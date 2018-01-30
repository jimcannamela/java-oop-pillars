package com.galvanize.util;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;

import java.util.HashMap;
import java.util.Map;

public class SubclassBuilder {

    private final ClassProxy classProxy;
    private final Map<String, FunctionThrows<Object[], Object>> methods = new HashMap<>();

    public SubclassBuilder(ClassProxy classProxy) {
        this.classProxy = classProxy;
    }

    public SubclassBuilder intercept(String methodName, Object value) {
        methods.put(methodName, args -> value);
        return this;
    }

    public SubclassBuilder intercept(String methodName, FunctionThrows<Object[], Object> fn) {
        methods.put(methodName, fn);
        return this;
    }

    public InstanceProxy build() {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(classProxy.getDelegate());
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
        return new InstanceProxy(enhancer.create(), classProxy);
    }

}
