package win.ac.x.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ReflectionUtils {

    public static Class<?> getClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static Method getMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        try {
            return clazz.getMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    public static Object invokeMethod(Object instance, String name, Object... args) {
        try {
            Class<?>[] paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = args[i].getClass();
            }
            Method method = instance.getClass().getMethod(name, paramTypes);
            return method.invoke(instance, args);
        } catch (Exception e) {
            return null;
        }
    }

    public static Object invokeStaticMethod(Class<?> clazz, String name, Object... args) {
        try {
            Class<?>[] paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = args[i].getClass();
            }
            Method method = clazz.getMethod(name, paramTypes);
            return method.invoke(null, args);
        } catch (Exception e) {
            return null;
        }
    }

    public static Object newInstance(String className, Object... args) {
        try {
            Class<?> clazz = Class.forName(className);
            Class<?>[] paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = args[i].getClass();
            }
            Constructor<?> constructor = clazz.getConstructor(paramTypes);
            return constructor.newInstance(args);
        } catch (Exception e) {
            return null;
        }
    }

    public static Object getFieldValue(Object instance, String name) {
        try {
            Field field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance);
        } catch (Exception e) {
            return null;
        }
    }
}