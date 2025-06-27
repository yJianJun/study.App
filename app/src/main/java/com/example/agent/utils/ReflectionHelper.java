package com.example.agent.utils;


import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;


@SuppressWarnings({ "rawtypes", "unchecked" })
public class ReflectionHelper {
	
	private static final HashMap<String, Field> fieldCache = new HashMap<String, Field>();
	private static final HashMap<String, Method> methodCache = new HashMap<String, Method>();
	private static final HashMap<String, Constructor<?>> constructorCache = new HashMap<String, Constructor<?>>();
	
	public static boolean hasField(Class<?> clazz, String fieldName) {
		StringBuilder sb = new StringBuilder(clazz.getName());
		sb.append('#');
		sb.append(fieldName);
		String fullFieldName = sb.toString();

		if (fieldCache.containsKey(fullFieldName)) {
			Field field = fieldCache.get(fullFieldName);
			if (field == null)
				return false;
			return true;
		}

		try {
			Field field = findFieldRecursiveImpl(clazz, fieldName);
			field.setAccessible(true);
			fieldCache.put(fullFieldName, field);
			return true;
		} catch (NoSuchFieldException e) {
			fieldCache.put(fullFieldName, null);
			return false;
		}
	}
	
	public static Field findField(Class<?> clazz, String fieldName) {
		StringBuilder sb = new StringBuilder(clazz.getName());
		sb.append('#');
		sb.append(fieldName);
		String fullFieldName = sb.toString();

		if (fieldCache.containsKey(fullFieldName)) {
			Field field = fieldCache.get(fullFieldName);
			if (field == null)
				throw new NoSuchFieldError(fullFieldName);
			return field;
		}

		try {
			Field field = findFieldRecursiveImpl(clazz, fieldName);
			field.setAccessible(true);
			fieldCache.put(fullFieldName, field);
			return field;
		} catch (NoSuchFieldException e) {
			fieldCache.put(fullFieldName, null);
			throw new NoSuchFieldError(fullFieldName);
		}
	}

	private static Field findFieldRecursiveImpl(Class<?> clazz, String fieldName) throws NoSuchFieldException {
		try {
			return clazz.getDeclaredField(fieldName);
		} catch (NoSuchFieldException e) {
			while (true) {
				clazz = clazz.getSuperclass();
				if (clazz == null || clazz.equals(Object.class))
					break;

				try {
					return clazz.getDeclaredField(fieldName);
				} catch (NoSuchFieldException ignored) {}
			}
			throw e;
		}
	}
	
	public static Field getField(Class clz, String field) {
		try {
			Field fld = findField(clz, field);
			if (fld != null)
				fld.setAccessible(true);
			return fld;
		} catch (Exception e) {
			LogFileUtil.logAndWrite(android.util.Log.ERROR, "ReflectionHelper", "Error",e);
			return null;
		}
	}

	public static Class getClass(String name, ClassLoader cl) {
		try {
			return Class.forName(name, false, cl);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public static boolean hasClass(String name) {
		try {
			Class.forName(name);
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}
	
	public static Class getClass(String name) {
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public static Class getClass(String name, Class clz) {
		try {
			ClassLoader cl = clz.getClassLoader();
			return Class.forName(name, false, cl);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}
	

	public static Object invokeStaticMethod(Class cls, Class[] type, String methodname, Object[] args) {
		try {
			Method method = cls.getDeclaredMethod(methodname, type);
			if (method == null)
				return null;
			method.setAccessible(true);
			return method.invoke(null, args);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static Object invokeStaticMethod(String clsName, Class[] type, String methodname, Object[] args) {
		Class clzz = null;
		try {
			clzz = Class.forName(clsName);
		} catch (Exception e) {
			clzz = null;
		}
		if (clzz == null)
			return null;
		return invokeStaticMethod(clzz, type, methodname, args);
	}

	public static Object invokeNonStaticMethod(Object obj, Class[] type, String methodname, Object[] args) {
		try {
			Method method = obj.getClass().getDeclaredMethod(methodname, type);
			if (method == null)
				return null;
			method.setAccessible(true);
			return method.invoke(obj, args);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public static Class<?>[] getParameterTypes(Object... args) {
		Class<?>[] clazzes = new Class<?>[args.length];
		for (int i = 0; i < args.length; i++) {
			clazzes[i] = (args[i] != null) ? args[i].getClass() : null;
		}
		return clazzes;
	}
	
	private static String getParametersString(Class<?>... clazzes) {
		StringBuilder sb = new StringBuilder("(");
		boolean first = true;
		for (Class<?> clazz : clazzes) {
			if (first)
				first = false;
			else
				sb.append(",");

			if (clazz != null)
				sb.append(clazz.getCanonicalName());
			else
				sb.append("null");
		}
		sb.append(")");
		return sb.toString();
	}
	
	public static Constructor<?> findConstructorExact(Class<?> clazz, Class<?>... parameterTypes) {
		StringBuilder sb = new StringBuilder(clazz.getName());
		sb.append(getParametersString(parameterTypes));
		sb.append("#exact");
		String fullConstructorName = sb.toString();

		if (constructorCache.containsKey(fullConstructorName)) {
			Constructor<?> constructor = constructorCache.get(fullConstructorName);
			if (constructor == null)
				throw new NoSuchMethodError(fullConstructorName);
			return constructor;
		}

		try {
			Constructor<?> constructor = clazz.getDeclaredConstructor(parameterTypes);
			constructor.setAccessible(true);
			constructorCache.put(fullConstructorName, constructor);
			return constructor;
		} catch (NoSuchMethodException e) {
			constructorCache.put(fullConstructorName, null);
			throw new NoSuchMethodError(fullConstructorName);
		}
	}
	
	public static Constructor<?> findConstructorExactThrowNothing(Class<?> clazz, Class<?>... parameterTypes) {
		StringBuilder sb = new StringBuilder(clazz.getName());
		sb.append(getParametersString(parameterTypes));
		sb.append("#exact");
		String fullConstructorName = sb.toString();

		if (constructorCache.containsKey(fullConstructorName)) {
			Constructor<?> constructor = constructorCache.get(fullConstructorName);
			return constructor;
		}

		try {
			Constructor<?> constructor = clazz.getDeclaredConstructor(parameterTypes);
			constructor.setAccessible(true);
			constructorCache.put(fullConstructorName, constructor);
			return constructor;
		} catch (NoSuchMethodException e) {
			constructorCache.put(fullConstructorName, null);
			return null;
		}
	}
	
	public static Constructor<?> findConstructorBestMatch(Class<?> clazz, Class<?>... parameterTypes) {
		StringBuilder sb = new StringBuilder(clazz.getName());
		sb.append(getParametersString(parameterTypes));
		sb.append("#bestmatch");
		String fullConstructorName = sb.toString();

		if (constructorCache.containsKey(fullConstructorName)) {
			Constructor<?> constructor = constructorCache.get(fullConstructorName);
			if (constructor == null)
				throw new NoSuchMethodError(fullConstructorName);
			return constructor;
		}

		try {
			Constructor<?> constructor = findConstructorExact(clazz, parameterTypes);
			constructorCache.put(fullConstructorName, constructor);
			return constructor;
		} catch (NoSuchMethodError ignored) {}
		
		Constructor<?> bestMatch = null;
		Constructor<?>[] constructors = clazz.getDeclaredConstructors();
		for (Constructor<?> constructor : constructors) {
			// compare name and parameters
			if (ClassUtils.isAssignable(parameterTypes, constructor.getParameterTypes(), true)) {
				// get accessible version of method
				if (bestMatch == null || MemberUtils.compareParameterTypes(
						constructor.getParameterTypes(),
						bestMatch.getParameterTypes(),
						parameterTypes) < 0) {
					bestMatch = constructor;
				}
			}
		}
		if (bestMatch != null) {
			bestMatch.setAccessible(true);
			constructorCache.put(fullConstructorName, bestMatch);
			return bestMatch;
		} else {
			NoSuchMethodError e = new NoSuchMethodError(fullConstructorName);
			constructorCache.put(fullConstructorName, null);
			throw e;
		}
	}
	
	public static Constructor<?> findConstructorBestMatch(Class<?> clazz, Object... args) {
		return findConstructorBestMatch(clazz, getParameterTypes(args));
	}
	
	public static Object newInstance(Class<?> clazz, Object... args) {
		try {
			return findConstructorBestMatch(clazz, args).newInstance(args);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static void setObjectField(Object obj, String fieldName, Object value) {
		try {
			findField(obj.getClass(), fieldName).set(obj, value);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static void setBooleanField(Object obj, String fieldName, boolean value) {
		try {
			findField(obj.getClass(), fieldName).setBoolean(obj, value);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static void setByteField(Object obj, String fieldName, byte value) {
		try {
			findField(obj.getClass(), fieldName).setByte(obj, value);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static void setCharField(Object obj, String fieldName, char value) {
		try {
			findField(obj.getClass(), fieldName).setChar(obj, value);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static void setDoubleField(Object obj, String fieldName, double value) {
		try {
			findField(obj.getClass(), fieldName).setDouble(obj, value);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static void setFloatField(Object obj, String fieldName, float value) {
		try {
			findField(obj.getClass(), fieldName).setFloat(obj, value);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static void setIntField(Object obj, String fieldName, int value) {
		try {
			findField(obj.getClass(), fieldName).setInt(obj, value);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static void setLongField(Object obj, String fieldName, long value) {
		try {
			findField(obj.getClass(), fieldName).setLong(obj, value);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static void setShortField(Object obj, String fieldName, short value) {
		try {
			findField(obj.getClass(), fieldName).setShort(obj, value);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	//#################################################################################################
	public static Object getObjectField(Object obj, String fieldName) {
		try {
			return findField(obj.getClass(), fieldName).get(obj);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	/** For inner classes, return the "this" reference of the surrounding class */
	public static Object getSurroundingThis(Object obj) {
		return getObjectField(obj, "this$0");
	}

	public static boolean getBooleanField(Object obj, String fieldName) {
		try {
			return findField(obj.getClass(), fieldName).getBoolean(obj);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static byte getByteField(Object obj, String fieldName) {
		try {
			return findField(obj.getClass(), fieldName).getByte(obj);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static char getCharField(Object obj, String fieldName) {
		try {
			return findField(obj.getClass(), fieldName).getChar(obj);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static double getDoubleField(Object obj, String fieldName) {
		try {
			return findField(obj.getClass(), fieldName).getDouble(obj);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static float getFloatField(Object obj, String fieldName) {
		try {
			return findField(obj.getClass(), fieldName).getFloat(obj);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}
	
	public static int getStaticIntField(Class clz, String fieldName, int defaultVal) {
		try {
			return findField(clz, fieldName).getInt(null);
		} catch (Exception e) {
			return defaultVal;
		}
	}
	
	public static int getIntField(Object obj, String fieldName, int defaultVal) {
		try {
			return findField(obj.getClass(), fieldName).getInt(obj);
		} catch (Exception e) {
			return defaultVal;
		}
	}
	
	public static int getIntField(Object obj, String fieldName) {
		try {
			return findField(obj.getClass(), fieldName).getInt(obj);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static long getLongField(Object obj, String fieldName) {
		try {
			return findField(obj.getClass(), fieldName).getLong(obj);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static short getShortField(Object obj, String fieldName) {
		try {
			return findField(obj.getClass(), fieldName).getShort(obj);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	//#################################################################################################
	public static void setStaticObjectField(Class<?> clazz, String fieldName, Object value) {
		try {
			findField(clazz, fieldName).set(null, value);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static void setStaticBooleanField(Class<?> clazz, String fieldName, boolean value) {
		try {
			findField(clazz, fieldName).setBoolean(null, value);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static void setStaticByteField(Class<?> clazz, String fieldName, byte value) {
		try {
			findField(clazz, fieldName).setByte(null, value);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static void setStaticCharField(Class<?> clazz, String fieldName, char value) {
		try {
			findField(clazz, fieldName).setChar(null, value);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static void setStaticDoubleField(Class<?> clazz, String fieldName, double value) {
		try {
			findField(clazz, fieldName).setDouble(null, value);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static void setStaticFloatField(Class<?> clazz, String fieldName, float value) {
		try {
			findField(clazz, fieldName).setFloat(null, value);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static void setStaticIntField(Class<?> clazz, String fieldName, int value) {
		try {
			findField(clazz, fieldName).setInt(null, value);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static void setStaticLongField(Class<?> clazz, String fieldName, long value) {
		try {
			findField(clazz, fieldName).setLong(null, value);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static void setStaticShortField(Class<?> clazz, String fieldName, short value) {
		try {
			findField(clazz, fieldName).setShort(null, value);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	//#################################################################################################
	public static Object getStaticObjectField(Class<?> clazz, String fieldName) {
		try {
			return findField(clazz, fieldName).get(null);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static boolean getStaticBooleanField(Class<?> clazz, String fieldName) {
		try {
			return findField(clazz, fieldName).getBoolean(null);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static byte getStaticByteField(Class<?> clazz, String fieldName) {
		try {
			return findField(clazz, fieldName).getByte(null);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static char getStaticCharField(Class<?> clazz, String fieldName) {
		try {
			return findField(clazz, fieldName).getChar(null);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static double getStaticDoubleField(Class<?> clazz, String fieldName) {
		try {
			return findField(clazz, fieldName).getDouble(null);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static float getStaticFloatField(Class<?> clazz, String fieldName) {
		try {
			return findField(clazz, fieldName).getFloat(null);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static int getStaticIntField(Class<?> clazz, String fieldName) {
		try {
			return findField(clazz, fieldName).getInt(null);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static long getStaticLongField(Class<?> clazz, String fieldName) {
		try {
			return findField(clazz, fieldName).getLong(null);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}

	public static short getStaticShortField(Class<?> clazz, String fieldName) {
		try {
			return findField(clazz, fieldName).getShort(null);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		}
	}
	
	public static Method findMethodExact(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
		StringBuilder sb = new StringBuilder(clazz.getName());
		sb.append('#');
		sb.append(methodName);
		sb.append(getParametersString(parameterTypes));
		sb.append("#exact");
		String fullMethodName = sb.toString();

		if (methodCache.containsKey(fullMethodName)) {
			Method method = methodCache.get(fullMethodName);
			if (method == null)
				throw new NoSuchMethodError(fullMethodName);
			return method;
		}

		try {
			Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
			method.setAccessible(true);
			methodCache.put(fullMethodName, method);
			return method;
		} catch (NoSuchMethodException e) {
			methodCache.put(fullMethodName, null);
			throw new NoSuchMethodError(fullMethodName);
		}
	}
	
	public static boolean hasMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
		return findMethodExactThrowNothing(clazz, methodName, parameterTypes) != null;
	}
	
	public static Method findMethodExactThrowNothing(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
		StringBuilder sb = new StringBuilder(clazz.getName());
		sb.append('#');
		sb.append(methodName);
		sb.append(getParametersString(parameterTypes));
		sb.append("#exact");
		String fullMethodName = sb.toString();

		if (methodCache.containsKey(fullMethodName)) {
			Method method = methodCache.get(fullMethodName);
			return method;
		}

		try {
			Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
			method.setAccessible(true);
			methodCache.put(fullMethodName, method);
			return method;
		} catch (NoSuchMethodException e) {
			methodCache.put(fullMethodName, null);
			return null;
		}
	}

	public static Object getInstance(String className, String staticFieldName) throws Exception {
		// 获取目标类的 Class 对象
		Class<?> clazz = Class.forName(className);

		// 获取指定的静态字段
		Field field = clazz.getDeclaredField(staticFieldName);
		field.setAccessible(true); // 如果字段是 private，需要通过反射设置可访问

		// 获取静态字段的值
		return field.get(null); // 静态字段，使用 null 获取值
	}


	public static Method findMethodBestMatch(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
		StringBuilder sb = new StringBuilder(clazz.getName());
		sb.append('#');
		sb.append(methodName);
		sb.append(getParametersString(parameterTypes));
		sb.append("#bestmatch");
		String fullMethodName = sb.toString();

		if (methodCache.containsKey(fullMethodName)) {
			Method method = methodCache.get(fullMethodName);
			if (method == null)
				throw new NoSuchMethodError(fullMethodName);
			return method;
		}

		try {
			Method method = findMethodExact(clazz, methodName, parameterTypes);
			methodCache.put(fullMethodName, method);
			return method;
		} catch (NoSuchMethodError ignored) {}
		
		Method bestMatch = null;
		Class<?> clz = clazz;
		boolean considerPrivateMethods = true;
		do {
			for (Method method : clz.getDeclaredMethods()) {
				// don't consider private methods of superclasses
				if (!considerPrivateMethods && Modifier.isPrivate(method.getModifiers()))
					continue;

				// compare name and parameters
				if (method.getName().equals(methodName) && ClassUtils.isAssignable(parameterTypes, method.getParameterTypes(), true)) {
					// get accessible version of method
					if (bestMatch == null || MemberUtils.compareParameterTypes(
							method.getParameterTypes(),
							bestMatch.getParameterTypes(),
							parameterTypes) < 0) {
						bestMatch = method;
					}
				}
			}
			considerPrivateMethods = false;
		} while ((clz = clz.getSuperclass()) != null);

		if (bestMatch != null) {
			bestMatch.setAccessible(true);
			methodCache.put(fullMethodName, bestMatch);
			return bestMatch;
		} else {
			NoSuchMethodError e = new NoSuchMethodError(fullMethodName);
			methodCache.put(fullMethodName, null);
			throw e;
		}
	}
	
	public static Method findMethodBestMatch(Class<?> clazz, String methodName, Object... args) {
		return findMethodBestMatch(clazz, methodName, getParameterTypes(args));
	}
	
	public static Object callMethod(Object obj, String methodName, Object... args) {
		try {
			return findMethodBestMatch(obj.getClass(), methodName, args).invoke(obj, args);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (InvocationTargetException e) {
			throw new InvocationTargetError(e.getCause());
		}
	}

	/**
	 * Call instance or static method <code>methodName</code> for object <code>obj</code> with the arguments
	 * <code>args</code>. The types for the arguments will be taken from <code>parameterTypes</code>.
	 * This array can have items that are <code>null</code>. In this case, the type for this parameter
	 * is determined from <code>args</code>.
	 */
	public static Object callMethod(Object obj, String methodName, Class<?>[] parameterTypes, Object... args) {
		try {
			return findMethodBestMatch(obj.getClass(), methodName, parameterTypes, args).invoke(obj, args);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (InvocationTargetException e) {
			throw new InvocationTargetError(e.getCause());
		}
	}

	/**
	 * Call static method <code>methodName</code> for class <code>clazz</code> with the arguments
	 * <code>args</code>. The types for the arguments will be determined automaticall from <code>args</code>
	 */
	public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
		try {
			return findMethodBestMatch(clazz, methodName, args).invoke(null, args);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (InvocationTargetException e) {
			throw new InvocationTargetError(e.getCause());
		}
	}

	/**
	 * Call static method <code>methodName</code> for class <code>clazz</code> with the arguments
	 * <code>args</code>. The types for the arguments will be taken from <code>parameterTypes</code>.
	 * This array can have items that are <code>null</code>. In this case, the type for this parameter
	 * is determined from <code>args</code>.
	 */
	public static Object callStaticMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes, Object... args) {
		try {
			return findMethodBestMatch(clazz, methodName, parameterTypes, args).invoke(null, args);
		} catch (IllegalAccessException e) {
			// should not happen
			e.printStackTrace();
			throw new IllegalAccessError(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (InvocationTargetException e) {
			throw new InvocationTargetError(e.getCause());
		}
	}
	
	public static class InvocationTargetError extends Error {
		private static final long serialVersionUID = -1070936889459514628L;
		public InvocationTargetError(Throwable cause) {
			super(cause);
		}
		public InvocationTargetError(String detailMessage, Throwable cause) {
			super(detailMessage, cause);
		}
	}
	
	public static String getMethodParams(Class targetClass, String methodName){
		StringBuffer msg=new StringBuffer();
		Method[] methods=targetClass.getDeclaredMethods();
		
		for(Method method:methods){
			if(methodName.equals(method.getName())){
				msg.append(Arrays.toString(method.getParameterTypes()));
			}
		}
		return msg.toString();
	}
	
	public static String getConstructorParams(Class targetClass){
		StringBuffer msg=new StringBuffer();
		Constructor[] constructors=targetClass.getDeclaredConstructors();
		for(Constructor constructor:constructors){
			msg.append(Arrays.toString(constructor.getParameterTypes()));
		}
		return msg.toString();
	}
	
	public static Class[] getMethodParamsForNoOverloading(Class targetClass, String methodName){
		Class[] result=null;
		Method[] methods=targetClass.getDeclaredMethods();
		for(Method method:methods){
			if(methodName.equals(method.getName())){
				result=method.getParameterTypes();
				break;
			}
		}
		return result;
	}
	
}
