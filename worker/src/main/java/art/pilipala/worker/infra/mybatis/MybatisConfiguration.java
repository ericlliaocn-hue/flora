package art.pilipala.worker.infra.mybatis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(value = "art.pilipala.worker", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class MybatisConfiguration {
}
