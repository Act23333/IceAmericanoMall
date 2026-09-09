package org.icedamericanomall;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.icedamericanomall.config.DefaultFeignConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        MybatisPlusAutoConfiguration.class
})
@EnableFeignClients(basePackages = {"org.icedamericanomall.client"}, defaultConfiguration = DefaultFeignConfig.class)
public class AuthorizationApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthorizationApplication.class, args);
    }
}  