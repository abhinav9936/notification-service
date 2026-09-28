package com.notificationservice.exception;

import com.notificationservice.entity.Channel;

public class ChannelDisabledException extends ConflictException {
    public ChannelDisabledException(Channel channel) {
        super("Channel is not enabled for this tenant: " + channel);
    }
}
