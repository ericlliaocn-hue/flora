package com.flora.api.infra.mybatis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.flora.api")
public class MybatisConfiguration {
}
