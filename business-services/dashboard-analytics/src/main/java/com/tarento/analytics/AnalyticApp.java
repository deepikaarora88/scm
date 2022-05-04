package com.tarento.analytics;

import org.cache2k.extra.spring.SpringCache2kCacheManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import com.tarento.analytics.constant.Constants;

import java.util.concurrent.TimeUnit;


@SpringBootApplication
@EnableCaching
public class AnalyticApp {
	 public static void main( String[] args ) {
	        SpringApplication.run(AnalyticApp.class, args);
	    }

	    @Bean
	    public RestTemplate restTemplate() {
	        return new RestTemplate();
	    }

	    @Bean
	    public WebMvcConfigurer corsConfigurer() {
	        return new WebMvcConfigurerAdapter() {
	            @Override
	            public void addCorsMappings(CorsRegistry registry) {
	                registry.addMapping("/**").allowedMethods(Constants.GET, Constants.POST,Constants.PUT, Constants.DELETE, Constants.OPTIONS).allowedOrigins("*")
	                        .allowedHeaders("*");
	            }
	        };
	    }


	private int workflowExpiry = 1;
	@Bean
	@Profile("!test")
	public CacheManager cacheManager(){
		return new SpringCache2kCacheManager().addCaches(b->b.name("tarentoResponse").expireAfterWrite(workflowExpiry, TimeUnit.MINUTES)
				.entryCapacity(10));
	}
}
