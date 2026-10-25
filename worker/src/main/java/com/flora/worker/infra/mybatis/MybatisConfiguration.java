package com.flora.worker.infra.mybatis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.flora.worker")
public class MybatisConfiguration {
}
