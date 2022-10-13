
package com.lzx.demo;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import java.util.Arrays;

/**
 * @author ber
 * @version 1.0
 * @date 21/11/9 13:18
 */
@ComponentScan({"com.lzx.demo"})
public class Application {
	public static void main(String[] args) {
		ApplicationContext context = new AnnotationConfigApplicationContext(Application.class);
		MsgService msgService = (MsgServiceImpl) context.getBean("msg");
		System.out.println(msgService.getMsg());
		System.out.println(context.getBeanDefinitionCount());
		Arrays.stream(context.getBeanDefinitionNames()).forEach(System.out::println);

		Application application = (Application) context.getBean("application");
		System.out.println(application.getMsg());
	}

	public String getMsg() {
		return "Hello Ber!!!";
	}
}

