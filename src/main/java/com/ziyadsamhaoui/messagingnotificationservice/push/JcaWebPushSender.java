package com.ziyadsamhaoui.messagingnotificationservice.push;

import com.ziyadsamhaoui.messagingnotificationservice.config.NotificationProperties;
import com.ziyadsamhaoui.messagingnotificationservice.model.PushSubscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class JcaWebPushSender implements WebPushSender {

    private final NotificationProperties properties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public WebPushResult send(PushSubscription subscription, String payload) {
        NotificationProperties.Push push = properties.push();

        if (!StringUtils.hasText(push.vapidPublicKey()) || !StringUtils.hasText(push.vapidPrivateKey())) {
            log.warn("web push skipped for subscription {}: VAPID keys are not configured", subscription.getId());
            return WebPushResult.failure("vapid keys not configured");
        }

        try {
            byte[] body = WebPushCrypto.encrypt(payload, subscription.getP256dhKey(), subscription.getAuthKey());
            String token = WebPushCrypto.vapidToken(push.vapidPublicKey(), push.vapidPrivateKey(),
                    push.vapidSubject(), subscription.getEndpoint());

            HttpRequest request = HttpRequest.newBuilder(URI.create(subscription.getEndpoint()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", String.valueOf(push.ttlSeconds()))
                    .header("Authorization", "vapid t=" + token + ", k=" + push.vapidPublicKey().trim())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();

            if (status == 410) {
                return WebPushResult.subscriptionGone();
            }

            return new WebPushResult(status, false, null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return WebPushResult.failure("interrupted");
        } catch (IOException | RuntimeException exception) {
            log.warn("web push delivery failed for subscription {}", subscription.getId(), exception);
            return WebPushResult.failure(exception.getMessage());
        }
    }
}
