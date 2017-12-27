package com.galvanize.util;

@FunctionalInterface
public interface FunctionThrows<T, R> {
    R apply(T t) throws Exception;
}