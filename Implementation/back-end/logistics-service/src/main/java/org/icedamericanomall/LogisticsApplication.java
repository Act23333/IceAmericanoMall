package org.icedamericanomall;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("org.icedamericanomall.mapper")
public class LogisticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogisticsApplication.class, args);
    }
}
