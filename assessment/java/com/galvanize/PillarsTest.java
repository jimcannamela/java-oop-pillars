package com.galvanize;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.galvanize.util.ClassProxy;
import com.galvanize.util.InstanceProxy;
import net.sf.cglib.proxy.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class PillarsTest {

    @Nested
    public class Encapsulation {

        @ParameterizedTest(name = "{arguments}")
        @DisplayName("All Fields Should Be Encapsulated")
        @ValueSource(strings = {"Lease", "Purchase", "Rental", "Order"})
        public void allFieldsAreEncapsulated(String className) {
            ClassProxy classProxy = ClassProxy.classNamed("com.galvanize." + className);

            Field[] fields = classProxy.getDelegate().getDeclaredFields();
            for (Field field : fields) {
                if (Modifier.isPublic(field.getModifiers())) {
                    fail(String.format("Expected %s.%s to be private or protected, but it is public", className, field.getName()));
                }
            }
        }

    }

    @Nested
    public class Inheritance {

        @ParameterizedTest(name = "{arguments}")
        @DisplayName("Item classes should inherit from a base class")
        @ValueSource(strings = {"Lease", "Purchase", "Rental"})
        public void itemClassesShouldInheritFromABaseClass(String className) {
            ClassProxy classProxy = ClassProxy.classNamed("com.galvanize." + className);

            Class<?> superclass = classProxy.getDelegate().getSuperclass();
            if (superclass == Object.class) {
                fail(String.format("Expected %s to inherit from some other class, but it inherits from Object", className));
            } else if (!Modifier.isAbstract(superclass.getModifiers())) {
                fail(String.format("Expected %s to be abstract", superclass.getName()));
            }
        }

        @Test()
        @DisplayName("Item classes should inherit from a base class")
        public void itemClassesShouldInheritFromABaseClass() {
            ClassProxy Lease = ClassProxy.classNamed("com.galvanize.Lease");

            ClassProxy Item = Lease.getSuperclassProxy();

            String methodName = "totalPrice";
            BigDecimal result = new BigDecimal("999.99");

            Object item = Item.subclassWith(methodName, result).create();

            ClassProxy Order = ClassProxy.classNamed("com.galvanize.Order")
                    .ensureConstructor()
                    .ensureMethod(m -> m.named("addItem").withParameters(Item))
                    .ensureMethod(m -> m.named("getTotal").returns(BigDecimal.class))
                    .ensureMethod(m -> m.named("getItems"));

            InstanceProxy order = Order.newInstance();
            order.invoke("addItem", item);

            BigDecimal totalAmount = (BigDecimal) order.invoke("getTotal");
            List<?> items = (List<?>) order.invoke("getItems");

            assertEquals(new BigDecimal("999.99"), totalAmount);
            assertEquals(singletonList(item), items);
        }

    }

    @Nested
    public class Polymorphism {

        @Test
        @DisplayName("Order.addItem should be polymorphic")
        public void addShouldBePolymorphic() throws Throwable {
            ClassProxy Order = ClassProxy.classNamed("com.galvanize.Order");

            Method[] declaredMethods = Order.getDelegate().getDeclaredMethods();
            Optional<Method> addItemMethod = Arrays.stream(declaredMethods)
                    .filter(method -> method.getName().equals("addItem"))
                    .findFirst();

            addItemMethod.ifPresent(method -> {
                Class<?>[] parameterTypes = method.getParameterTypes();
                assertEquals(1, parameterTypes.length, "Expected Order#addItems to take one parameter, but got " + parameterTypes.length);

                if (parameterTypes[0] == Object.class) {
                    fail("Expected the parameter type of Order#addItems to be something other than Object");
                }
            });

            addItemMethod.orElseThrow(() -> fail("Expected Order to have a method named `addItem`"));
        }

    }

}
