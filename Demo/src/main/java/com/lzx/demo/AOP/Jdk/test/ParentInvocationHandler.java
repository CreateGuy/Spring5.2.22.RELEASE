package com.lzx.demo.AOP.Jdk.test;
 
 
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
 
/*
 * 这个是一个增强类，实现InvocationHandler通用接口，是对目标对象的一个方法增强
 * */
public class ParentInvocationHandler implements InvocationHandler {

	/**
	 * 被代理类的实例
	 */
    private People people;

    //持有people接口的引用
    public ParentInvocationHandler(People people) {
        this.people = people;
    }
 
    /*
     * 找到对象以后，帮助南道操持婚礼等
     * */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
 
        //帮他找到对象
        before();
 
        //这个invoke就会掉到被代理类中的method
        method.invoke(people,args);
 
        after();
 
        return null;
    }
 
    /*
     *  这个方法是南道在找到对象之前，父母帮助他做得事情
     * */
    private void before() {
        System.out.println("我是南道父母，我需要帮助他找对象！！");
    }
 
    /*
     * 找到对象之前，父母帮助他操持婚礼，带小孩
     * */
    private void after() {
        System.out.println("我是南道的父母，我们需要帮助ta操持婚礼，帮他带小孩等等");
    }
}