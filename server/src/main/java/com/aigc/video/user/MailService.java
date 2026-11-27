package com.aigc.video.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** 邮件发送：默认 log 模式，配置 SMTP 后真实发送。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${aigc.mail.type:log}")
    private String type;

    @Value("${aigc.mail.from:no-reply@local}")
    private String from;

    @Value("${spring.mail.host:}")
    private String host;

    /** 未配置 SMTP 时降级为日志输出。 */
    public boolean logMode() {
        return !"smtp".equalsIgnoreCase(type) || host == null || host.isBlank();
    }

    /** 发送邮件；日志模式只打印内容，便于本地联调。 */
    public void send(String to, String subject, String content) {
        if (logMode()) {
            log.info("[邮件-log] to={} subject={}\n{}", to, subject, content);
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("未配置邮件发送器，降级为日志输出 to={}", to);
            log.info("[邮件-log] to={} subject={}\n{}", to, subject, content);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        sender.send(message);
    }
}
