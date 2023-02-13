package com.lzx.demo.AOP.Jdk.test;
 
/*
* 被代理实例
* */
public class Nandao implements People {
 
     //1、nandao找到对象的业务场景
    @Override
    public void zdx() {
        System.out.println("我在北京工作，没有时间找对象！");
    }
    //2、如果父母帮助南道找到了对象等等
}