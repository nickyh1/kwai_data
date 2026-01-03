package com.example.kwai_data;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class KwaiDataApplication {


    public static void main(String[] args) throws Exception{
        SpringApplication.run(KwaiDataApplication.class, args);

    }


}
