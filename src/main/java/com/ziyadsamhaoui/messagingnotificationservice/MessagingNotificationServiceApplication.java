package com.ziyadsamhaoui.messagingnotificationservice;

import com.ziyadsamhaoui.messagingnotificationservice.config.NotificationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(NotificationProperties.class)
public class MessagingNotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessagingNotificationServiceApplication.class, args);
    }

}
