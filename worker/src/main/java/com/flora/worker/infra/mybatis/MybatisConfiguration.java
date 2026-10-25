package com.flora.worker.infra.mybatis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(value = "com.flora.worker", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class MybatisConfiguration {
}
