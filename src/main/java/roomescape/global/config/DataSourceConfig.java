package roomescape.global.config;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource primaryDataSource() {
        return primaryDataSourceProperties().initializeDataSourceBuilder().build();
    }

    @Bean(name = "distributedDataSourceProperties")
    @ConfigurationProperties("spring.datasource.distributed")
    public DataSourceProperties distributedDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "distributedDataSource")
    public DataSource distributedDataSource() {
        return distributedDataSourceProperties().initializeDataSourceBuilder().build();
    }
}
