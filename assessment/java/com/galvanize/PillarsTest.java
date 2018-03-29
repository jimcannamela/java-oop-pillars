package com.galvanize;

import static com.galvanize.util.ReflectionUtils.failFormat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.galvanize.util.ClassProxy;
import com.galvanize.util.InstanceProxy;
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
import java.util.List;

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
                    failFormat("Expected `%s.%s` to be private or protected, but it is public", className, field.getName());
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
                failFormat("Expected `%s` to inherit from some other class, but it inherits from `Object`", className);
            } else if (!Modifier.isAbstract(superclass.getModifiers())) {
                failFormat("Expected `%s` to be abstract", superclass.getName());
            }
        }
    }

    @Nested
    public class Polymorphism {

        @Test
        @DisplayName("Order.addItem should be polymorphic")
        public void addShouldBePolymorphic() throws Throwable {

            Method[] declaredMethods = Order.class.getDeclaredMethods();
            Method addItemMethod = Arrays.stream(declaredMethods)
                    .filter(method -> method.getName().equals("addItem"))
                    .findFirst().orElseThrow(() -> fail("Expected `Order` to have a method named `addItem`"));

            Class<?>[] parameterTypes = addItemMethod.getParameterTypes();
            assertEquals(1, parameterTypes.length, "Expected `Order.addItem` to take one parameter, but got " + parameterTypes.length);

            Class<?> itemClass = parameterTypes[0];
            if (itemClass == Object.class) {
                fail("Expected the parameter type of `Order.addItem` to be something other than `Object`");
            }

            ClassProxy _Item = ClassProxy.of(itemClass);
            String methodName = "totalPrice";
            _Item.ensureMethod(m -> m.named(methodName).returns(BigDecimal.class));

            BigDecimal result = new BigDecimal("999.99");
            InstanceProxy item = _Item.subclass()
                    .intercept(methodName, result)
                    .build();

            ClassProxy _Order = ClassProxy.of(Order.class)
                    .ensureConstructor()
                    .ensureMethod(addItemMethod)
                    .ensureMethod(m -> m.named("getTotal").returns(BigDecimal.class))
                    .ensureMethod(m -> m.named("getItems"));

            InstanceProxy order = _Order.newInstance();
            order.invoke("addItem", item);

            BigDecimal totalAmount = (BigDecimal) order.invoke("getTotal");
            List<?> items = (List<?>) order.invoke("getItems");

            if (items.size() != 1 || !items.get(0).equals(item.getDelegate())) {
                fail("Expected `Order.addItem` to add the specified item to the list");
            }

            if (result.compareTo(totalAmount) != 0) {
                failFormat(
                        "Expected `Order` to call `%s` for any implementation of the `%s` base class",
                        methodName, _Item.getDelegate().getSimpleName());
            }
        }
    }

}
