package art.pilipala.api.auth.infra;

import java.util.Properties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
@EnableConfigurationProperties(AuthChannelProperties.class)
public class MailSenderConfig {

    @Bean
    JavaMailSender javaMailSender(AuthChannelProperties properties) {
        AuthChannelProperties.MailSmtp mail = properties.mail();
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        if (mail == null) {
            sender.setHost("localhost");
            sender.setPort(587);
            return sender;
        }
        sender.setHost(mail.host() != null ? mail.host() : "localhost");
        sender.setPort(mail.port() > 0 ? mail.port() : 587);
        sender.setUsername(mail.username());
        sender.setPassword(mail.password());
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", String.valueOf(mail.starttls()));
        return sender;
    }
}
