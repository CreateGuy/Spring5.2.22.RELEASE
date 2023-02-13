package com.lzx.demo.AOP.Jdk;

import com.enjoy.jack.test.People;
import com.lzx.demo.AOP.Jdk.test.People;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;

/**
 * 代理对象
 */
public final class $Proxy0 extends Proxy implements People {

	private static Method m1;
	private static Method m2;
	private static Method m3;
	private static Method m0;

	public $Proxy0(InvocationHandler var1) throws {
		super(var1);
	}

	public final boolean equals(Object var1) throws {
		try {
			return (Boolean) super.h.invoke(this, m1, new Object[]{var1});
		} catch (RuntimeException | Error var3) {
			throw var3;
		} catch (Throwable var4) {
			throw new UndeclaredThrowableException(var4);
		}
	}

	public final String toString() throws {
		try {
			return (String) super.h.invoke(this, m2, (Object[]) null);
		} catch (RuntimeException | Error var2) {
			throw var2;
		} catch (Throwable var3) {
			throw new UndeclaredThrowableException(var3);
		}
	}

	// 核心接口在这里，测试类TestProxy调用people.zdx();就是走的这里
	public final void zdx() throws {
		try {
			// 调用InvocationHandler执行
			// 也是InvocationHandler持有了被代理对象实例
			super.h.invoke(this, m3, (Object[]) null);
		} catch (RuntimeException | Error var2) {
			throw var2;
		} catch (Throwable var3) {
			throw new UndeclaredThrowableException(var3);
		}
	}

	public final int hashCode() throws {
		try {
			return (Integer) super.h.invoke(this, m0, (Object[]) null);
		} catch (RuntimeException | Error var2) {
			throw var2;
		} catch (Throwable var3) {
			throw new UndeclaredThrowableException(var3);
		}
	}

	/**
	 * 和Cglib代理一样，收集方法信息
	 */
	static {
		try {
			m1 = Class.forName("java.lang.Object").getMethod("equals", Class.forName("java.lang.Object"));
			m2 = Class.forName("java.lang.Object").getMethod("toString");
			m3 = Class.forName("com.enjoy.jack.test.People").getMethod("zdx");
			m0 = Class.forName("java.lang.Object").getMethod("hashCode");
		} catch (NoSuchMethodException var2) {
			throw new NoSuchMethodError(var2.getMessage());
		} catch (ClassNotFoundException var3) {
			throw new NoClassDefFoundError(var3.getMessage());
		}
	}
}