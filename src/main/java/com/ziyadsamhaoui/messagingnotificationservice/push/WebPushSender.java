package com.ziyadsamhaoui.messagingnotificationservice.push;

import com.ziyadsamhaoui.messagingnotificationservice.model.PushSubscription;

public interface WebPushSender {

    WebPushResult send(PushSubscription subscription, String payload);
}
