package com.angel.flexbuddy.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

/**
 * Chooses the mailer. With an empty {@code spring.mail.host} Spring Boot still creates a {@link JavaMailSender}, so the
 * choice is made on whether a host is set, not on whether the bean exists.
 */
@Configuration
public class MailConfig {

    @Bean
    PasswordResetMailer passwordResetMailer(
            @Value("${spring.mail.host:}") String host,
            ObjectProvider<JavaMailSender> senderProvider,
            @Value("${flexbuddy.mail.from}") String from,
            @Value("${flexbuddy.mail.log-links:false}") boolean logLinks) {
        if (StringUtils.hasText(host)) {
            return new SmtpPasswordResetMailer(senderProvider.getObject(), from);
        }
        return new LoggingPasswordResetMailer(logLinks);
    }

    @Bean
    EmailCodeMailer emailCodeMailer(
            @Value("${spring.mail.host:}") String host,
            ObjectProvider<JavaMailSender> senderProvider,
            @Value("${flexbuddy.mail.from}") String from,
            @Value("${flexbuddy.mail.log-links:false}") boolean logCodes) {
        if (StringUtils.hasText(host)) {
            return new SmtpEmailCodeMailer(senderProvider.getObject(), from);
        }
        return new LoggingEmailCodeMailer(logCodes);
    }

    /** Operator alerts follow the same host rule: with no mail server they are logged, not sent. */
    @Bean
    ErrorAlertMailer errorAlertMailer(
            @Value("${spring.mail.host:}") String host,
            ObjectProvider<JavaMailSender> senderProvider,
            @Value("${flexbuddy.mail.from}") String from,
            @Value("${flexbuddy.alerts.to:flexbuddysupport@gmail.com}") String to) {
        if (StringUtils.hasText(host)) {
            return new SmtpErrorAlertMailer(senderProvider.getObject(), from, to);
        }
        return new LoggingErrorAlertMailer();
    }
}
