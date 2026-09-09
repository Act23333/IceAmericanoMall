package org.icedamericanomall;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "org.icedamericanomall.client")
@MapperScan("org.icedamericanomall.mapper")
public class MarketingApplication {
    public static void main(String[] args) {
        SpringApplication.run(MarketingApplication.class, args);
    }
}
