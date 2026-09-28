package com.notificationservice.exception;

import com.notificationservice.entity.Channel;

public class ChannelNotConfiguredException extends ResourceNotFoundException {
    public ChannelNotConfiguredException(Channel channel) {
        super("Channel not configured for this tenant: " + channel);
    }
}
