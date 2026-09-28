package com.notificationservice.dispatch;

public interface ChannelSender {
    void send(String recipient, String subject, String body);
}
