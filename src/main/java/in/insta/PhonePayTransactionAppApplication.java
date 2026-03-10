package in.insta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;

@SpringBootApplication
public class PhonePayTransactionAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(PhonePayTransactionAppApplication.class, args);
		System.out.println("\n🚀 PhonePay Transaction App Started!");
		System.out.println("📊 Open browser: http://localhost:8080/upload.html");
		System.out.println("📁 Upload your PDF statement to get started!\n");
	}

	@Bean
	public WebMvcConfigurer webMvcConfigurer() {
		return new WebMvcConfigurer() {
			@Override
			public void addViewControllers(ViewControllerRegistry registry) {
				registry.addViewController("/").setViewName("redirect:/upload.html");
			}
		};
	}

}
