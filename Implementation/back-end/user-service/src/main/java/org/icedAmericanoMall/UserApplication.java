package org.icedAmericanoMall;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @ClassName: UserApplication
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/13 15:15
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedAmericanoMall
 */
@SpringBootApplication
@MapperScan("org.icedAmericanoMall.mapper")
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}


