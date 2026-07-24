package com.feijimiao.xianyuassistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** 销售与数据面板统一使用的业务时区。 */
@Configuration
public class SalesTimeConfig {
    public static final ZoneId SALES_ZONE = ZoneId.of("Asia/Shanghai");

    @Bean
    public Clock salesClock() {
        return Clock.system(SALES_ZONE);
    }
}
