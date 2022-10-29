
package com.lzx.demo;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.TypeDescriptor;

import java.util.Arrays;

/**
 * @author ber
 * @version 1.0
 * @date 21/11/9 13:18
 */
@ComponentScan({"com.lzx.demo"})
public class Application {
	public static void main(String[] args) {
		System.out.println("编译环境的代建：https://blog.csdn.net/Ber_Bai/article/details/121223572?ops_request_misc=&request_id=&biz_id=102&utm_term=springframework%20%E7%BC%96%E8%AF%91%E8%BF%90%E8%A1%8C&utm_medium=distribute.pc_search_result.none-task-blog-2~all~sobaiduweb~default-5-121223572.nonecase&spm=1018.2226.3001.4187");
		System.out.println("解决target目录没有提交的问题：https://blog.csdn.net/qq_35443962/article/details/121870667");

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

