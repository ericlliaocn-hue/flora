package art.pilipala.api.infra.mybatis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(value = "art.pilipala.api", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class MybatisConfiguration {
}
