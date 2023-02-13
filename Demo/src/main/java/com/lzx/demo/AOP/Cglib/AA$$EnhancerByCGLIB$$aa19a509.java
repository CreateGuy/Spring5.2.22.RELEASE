//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.lzx.demo.AOP.Cglib;

import java.lang.reflect.Method;
import org.springframework.cglib.core.ReflectUtils;
import org.springframework.cglib.core.Signature;
import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.Factory;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

/**
 * 代理对象
 */
public class AA$$EnhancerByCGLIB$$aa19a509 extends AA implements Factory {
	/**
	 * 标识拦截器是否已经绑定
	 */
    private boolean CGLIB$BOUND;

    public static Object CGLIB$FACTORY_DATA;
	/**
	 * 下面的两个变量用来保存回调类，也就是我们设置所有的拦截器，CGLIB会将回调先设置到这两个变量上，然后再进行绑定
	 */
	private static final ThreadLocal CGLIB$THREAD_CALLBACKS;
    private static final Callback[] CGLIB$STATIC_CALLBACKS;

	/**
	 * 绑定的拦截器，注册了两个拦截器
	 */
	private MethodInterceptor CGLIB$CALLBACK_0;
    private MethodInterceptor CGLIB$CALLBACK_1;

    private static Object CGLIB$CALLBACK_FILTER;

	/**
	 * 空参数，在被代理方法没有入参的时候被使用
	 */
	private static final Object[] CGLIB$emptyArgs;

	/**
	 * 下面分别是被代理类的bb方法和Object的四大方法的方法的和代理方法
	 * <li>注意：代理方法中的存在是为了执行FastClass机制</li>
	 */
	private static final Method CGLIB$bb$0$Method;
    private static final MethodProxy CGLIB$bb$0$Proxy;
    private static final Method CGLIB$equals$1$Method;
    private static final MethodProxy CGLIB$equals$1$Proxy;
    private static final Method CGLIB$toString$2$Method;
    private static final MethodProxy CGLIB$toString$2$Proxy;
    private static final Method CGLIB$hashCode$3$Method;
    private static final MethodProxy CGLIB$hashCode$3$Proxy;
    private static final Method CGLIB$clone$4$Method;
    private static final MethodProxy CGLIB$clone$4$Proxy;

	/**
	 * 该方法会在静态代码块中被调用，对上面的变量进行初始化
	 */
	static void CGLIB$STATICHOOK1() {
        CGLIB$THREAD_CALLBACKS = new ThreadLocal();
        CGLIB$emptyArgs = new Object[0];
        Class var0 = Class.forName("org.lzx.springBootDemo.controller.AA$$EnhancerByCGLIB$$aa19a509");
        Class var1;
        Method[] var10000 = ReflectUtils.findMethods(new String[]{"equals", "(Ljava/lang/Object;)Z", "toString", "()Ljava/lang/String;", "hashCode", "()I", "clone", "()Ljava/lang/Object;"}, (var1 = Class.forName("java.lang.Object")).getDeclaredMethods());
        CGLIB$equals$1$Method = var10000[0];
        CGLIB$equals$1$Proxy = MethodProxy.create(var1, var0, "(Ljava/lang/Object;)Z", "equals", "CGLIB$equals$1");
        CGLIB$toString$2$Method = var10000[1];
        CGLIB$toString$2$Proxy = MethodProxy.create(var1, var0, "()Ljava/lang/String;", "toString", "CGLIB$toString$2");
        CGLIB$hashCode$3$Method = var10000[2];
        CGLIB$hashCode$3$Proxy = MethodProxy.create(var1, var0, "()I", "hashCode", "CGLIB$hashCode$3");
        CGLIB$clone$4$Method = var10000[3];
        CGLIB$clone$4$Proxy = MethodProxy.create(var1, var0, "()Ljava/lang/Object;", "clone", "CGLIB$clone$4");
        CGLIB$bb$0$Method = ReflectUtils.findMethods(new String[]{"bb", "()Ljava/lang/Integer;"}, (var1 = Class.forName("org.lzx.springBootDemo.controller.AA")).getDeclaredMethods())[0];
        CGLIB$bb$0$Proxy = MethodProxy.create(var1, var0, "()Ljava/lang/Integer;", "bb", "CGLIB$bb$0");
    }

	/**
	 * 调用父类的原始方法
	 * CGLIB生成代理利用的是继承，而不是JDK动态代理的利用接口的形式
	 * 这样就有一个区别就出现了，JDK动态代理中必须要有一个被代理类的实例
	 * 但是CGLIB实现的动态代理就不需要，因为是继承，所以就包含了被代理类的全部方法
	 * 但是我们调用生成的代理类实例的toString()方法时，调用的就是CGLIB代理时候的方法
	 * 如果调用被代理类的原始方法呢，就是靠下面的这个方法
	 * CGLIB生成的代理类中每个原始方法都会有这两种类型的方法
	 */
    final Integer CGLIB$bb$0() {
        return super.bb();
    }

	/**
	 * 生成的代理方法，在该方法中会调用我们设置的Callback
	 */
    public final Integer bb() {
        MethodInterceptor var10000 = this.CGLIB$CALLBACK_0;
        if (var10000 == null) {
        	// 构建拦截器
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_0;
        }
        // 有拦截器就调用，没有就执行原始方法
        return var10000 != null ? (Integer)var10000.intercept(this, CGLIB$bb$0$Method, CGLIB$emptyArgs, CGLIB$bb$0$Proxy) : super.bb();
    }

    final boolean CGLIB$equals$1(Object var1) {
        return super.equals(var1);
    }

    public final boolean equals(Object var1) {
        MethodInterceptor var10000 = this.CGLIB$CALLBACK_0;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_0;
        }

        if (var10000 != null) {
            Object var2 = var10000.intercept(this, CGLIB$equals$1$Method, new Object[]{var1}, CGLIB$equals$1$Proxy);
            return var2 == null ? false : (Boolean)var2;
        } else {
            return super.equals(var1);
        }
    }

    final String CGLIB$toString$2() {
        return super.toString();
    }

    public final String toString() {
        MethodInterceptor var10000 = this.CGLIB$CALLBACK_0;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_0;
        }

        return var10000 != null ? (String)var10000.intercept(this, CGLIB$toString$2$Method, CGLIB$emptyArgs, CGLIB$toString$2$Proxy) : super.toString();
    }

    final int CGLIB$hashCode$3() {
        return super.hashCode();
    }

    public final int hashCode() {
        MethodInterceptor var10000 = this.CGLIB$CALLBACK_0;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_0;
        }

        if (var10000 != null) {
            Object var1 = var10000.intercept(this, CGLIB$hashCode$3$Method, CGLIB$emptyArgs, CGLIB$hashCode$3$Proxy);
            return var1 == null ? 0 : ((Number)var1).intValue();
        } else {
            return super.hashCode();
        }
    }

    final Object CGLIB$clone$4() throws CloneNotSupportedException {
        return super.clone();
    }

    protected final Object clone() throws CloneNotSupportedException {
        MethodInterceptor var10000 = this.CGLIB$CALLBACK_0;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_0;
        }

        return var10000 != null ? var10000.intercept(this, CGLIB$clone$4$Method, CGLIB$emptyArgs, CGLIB$clone$4$Proxy) : super.clone();
    }

	/**
	 * 利用FastClass机制返回代理方法
	 * @param var0
	 * @return
	 */
	public static MethodProxy CGLIB$findMethodProxy(Signature var0) {
        String var10000 = var0.toString();
        // 这个hashCode就已经和某个MethodProxy建立起了关系
        switch(var10000.hashCode()) {
        case -1556200676:
        	// 想要执行bb方法，然后返回bb的代理方法
            if (var10000.equals("bb()Ljava/lang/Integer;")) {
                return CGLIB$bb$0$Proxy;
            }
            break;
        case -508378822:
            if (var10000.equals("clone()Ljava/lang/Object;")) {
                return CGLIB$clone$4$Proxy;
            }
            break;
        case 1826985398:
            if (var10000.equals("equals(Ljava/lang/Object;)Z")) {
                return CGLIB$equals$1$Proxy;
            }
            break;
        case 1913648695:
            if (var10000.equals("toString()Ljava/lang/String;")) {
                return CGLIB$toString$2$Proxy;
            }
            break;
        case 1984935277:
            if (var10000.equals("hashCode()I")) {
                return CGLIB$hashCode$3$Proxy;
            }
        }

        return null;
    }

    public AA$$EnhancerByCGLIB$$aa19a509() {
        CGLIB$BIND_CALLBACKS(this);
    }

    public static void CGLIB$SET_THREAD_CALLBACKS(Callback[] var0) {
        CGLIB$THREAD_CALLBACKS.set(var0);
    }

    public static void CGLIB$SET_STATIC_CALLBACKS(Callback[] var0) {
        CGLIB$STATIC_CALLBACKS = var0;
    }

	/**
	 * 构建拦截器链：
	 * <ul>
	 *     <li>从EnhancerByCGLIB中获取拦截器链</li>
	 *     <li>在Jdk动态代理中拦截对象是在实例化代理类时由构造函数传入的，在cglib中我们使用Enhancers生成代理类时。是调用Enhancer的firstInstance方法来生成代理类实例并设置回调。</li>
	 * </ul>
	 */
    private static final void CGLIB$BIND_CALLBACKS(Object var0) {
        AA$$EnhancerByCGLIB$$aa19a509 var1 = (AA$$EnhancerByCGLIB$$aa19a509)var0;
        if (!var1.CGLIB$BOUND) {
            var1.CGLIB$BOUND = true;
            Object var10000 = CGLIB$THREAD_CALLBACKS.get();
            if (var10000 == null) {
                var10000 = CGLIB$STATIC_CALLBACKS;
                if (var10000 == null) {
                    return;
                }
            }

            Callback[] var10001 = (Callback[])var10000;
            var1.CGLIB$CALLBACK_1 = (MethodInterceptor)((Callback[])var10000)[1];
            var1.CGLIB$CALLBACK_0 = (MethodInterceptor)var10001[0];
        }

    }

	/**
	 * 实例化代理对象
	 * @param var1
	 * @return
	 */
	public Object newInstance(Callback[] var1) {
        CGLIB$SET_THREAD_CALLBACKS(var1);
        AA$$EnhancerByCGLIB$$aa19a509 var10000 = new AA$$EnhancerByCGLIB$$aa19a509();
        CGLIB$SET_THREAD_CALLBACKS((Callback[])null);
        return var10000;
    }

    public Object newInstance(Callback var1) {
        throw new IllegalStateException("More than one callback object required");
    }

    public Object newInstance(Class[] var1, Object[] var2, Callback[] var3) {
        CGLIB$SET_THREAD_CALLBACKS(var3);
        AA$$EnhancerByCGLIB$$aa19a509 var10000 = new AA$$EnhancerByCGLIB$$aa19a509();
        switch(var1.length) {
        case 0:
            var10000.<init>();
            CGLIB$SET_THREAD_CALLBACKS((Callback[])null);
            return var10000;
        default:
            throw new IllegalArgumentException("Constructor not found");
        }
    }

	/**
	 * 返回拦截器链
	 * @param var1
	 * @return
	 */
	public Callback getCallback(int var1) {
        CGLIB$BIND_CALLBACKS(this);
        MethodInterceptor var10000;
        switch(var1) {
        case 0:
            var10000 = this.CGLIB$CALLBACK_0;
            break;
        case 1:
            var10000 = this.CGLIB$CALLBACK_1;
            break;
        default:
            var10000 = null;
        }

        return var10000;
    }

    public void setCallback(int var1, Callback var2) {
        switch(var1) {
        case 0:
            this.CGLIB$CALLBACK_0 = (MethodInterceptor)var2;
            break;
        case 1:
            this.CGLIB$CALLBACK_1 = (MethodInterceptor)var2;
        }

    }

    public Callback[] getCallbacks() {
        CGLIB$BIND_CALLBACKS(this);
        return new Callback[]{this.CGLIB$CALLBACK_0, this.CGLIB$CALLBACK_1};
    }

    public void setCallbacks(Callback[] var1) {
        this.CGLIB$CALLBACK_0 = (MethodInterceptor)var1[0];
        this.CGLIB$CALLBACK_1 = (MethodInterceptor)var1[1];
    }

	/**
	 * 静态方法
	 */
	static {
        CGLIB$STATICHOOK1();
    }
}
