package com.lzx.demo.AOP.Jdk.test;

import sun.misc.ProxyGenerator;
 
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
 
public class TestProxy {
    public static void main(String[] args) {
 
         //proxy对象就是nandao这个对象的一个代理实例，nandao这个对象就是一个被代理，参数 ClassLoader 是类加载器
         //new Class<?>[]{People.class} 这是接口数组
         //new ParentInvocationHandler(new Nandao())  通用接口实现类持有被代理对象的引用
        People people = (People)Proxy.newProxyInstance(TestProxy.class.getClassLoader(),new Class<?>[]{People.class},
                new ParentInvocationHandler(new Nandao()));
 
        people.zdx();
        //是否要将生成代理类的字节码.class文件保存到磁盘中,和项目文件在相同目录
        createProxyClassFile();
    }
 
    public static void createProxyClassFile() {
        //$Proxy0 执行后此class文件生成到项目得我根目录里，比如：D:\spring-source\$Proxy0.class，然后把这个文件直接拉到IDEA中就看到相应的java代码，这个类就是代理类。
        byte[] $Proxy0s = ProxyGenerator.generateProxyClass("$Proxy0", new Class[]{People.class});
 
        try {
            FileOutputStream fileOutputStream = new FileOutputStream("$Proxy0.class");
            fileOutputStream.write($Proxy0s);
            fileOutputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
 