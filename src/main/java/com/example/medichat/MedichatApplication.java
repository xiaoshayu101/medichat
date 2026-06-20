package com.example.medichat;

import jakarta.annotation.PostConstruct;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;
@EnableScheduling
@SpringBootApplication
@MapperScan("com.example.medichat.mapper")//告诉 Spring："去这个包下面找所有的 Mapper 接口，自动注册成 Bean"
public class MedichatApplication {

	public static void main(String[] args) {
		SpringApplication.run(MedichatApplication.class, args);
	}

}

