package com.notificationservice.service;

import tools.jackson.databind.ObjectMapper;
import com.notificationservice.dto.DeliveryAttemptResponse;
import com.notificationservice.dto.NotificationDetailResponse;
import com.notificationservice.dto.NotificationResponse;
import com.notificationservice.dto.NotificationSendRequest;
import com.notificationservice.entity.ChannelConfig;
import com.notificationservice.entity.NotificationRequest;
import com.notificationservice.entity.NotificationStatus;
import com.notificationservice.entity.NotificationTemplate;
import com.notificationservice.exception.ChannelDisabledException;
import com.notificationservice.exception.NotificationNotFoundException;
import com.notificationservice.exception.TemplateNotFoundException;
import com.notificationservice.repository.ChannelConfigRepository;
import com.notificationservice.repository.DeliveryAttemptRepository;
import com.notificationservice.repository.NotificationRequestRepository;
import com.notificationservice.repository.NotificationTemplateRepository;
import com.notificationservice.repository.TenantRepository;
import com.notificationservice.util.TemplateRenderer;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationRequestService {

    private final NotificationRequestRepository notificationRequestRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final NotificationTemplateRepository templateRepository;
    private final ChannelConfigRepository channelConfigRepository;
    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public NotificationResponse send(Long tenantId, NotificationSendRequest request) {
        Optional<NotificationRequest> existing =
                notificationRequestRepository.findByTenantIdAndIdempotencyKey(tenantId, request.idempotencyKey());
        if (existing.isPresent()) {
            return NotificationResponse.from(existing.get());
        }

        NotificationTemplate template = templateRepository.findByIdAndTenantId(request.templateId(), tenantId)
                .orElseThrow(() -> new TemplateNotFoundException(request.templateId()));

        ChannelConfig channelConfig = channelConfigRepository.findByTenantIdAndChannel(tenantId, template.getChannel())
                .orElseThrow(() -> new ChannelDisabledException(template.getChannel()));
        if (!channelConfig.isEnabled()) {
            throw new ChannelDisabledException(template.getChannel());
        }

        Map<String, String> variables = request.variables() == null ? Map.of() : request.variables();
        String renderedSubject = TemplateRenderer.render(template.getSubject(), variables);
        String renderedBody = TemplateRenderer.render(template.getBody(), variables);

        NotificationRequest entity = new NotificationRequest();
        entity.setTenant(tenantRepository.getReferenceById(tenantId));
        entity.setTemplate(template);
        entity.setChannel(template.getChannel());
        entity.setRecipient(request.recipient());
        entity.setVariablesJson(writeJson(variables));
        entity.setRenderedSubject(renderedSubject);
        entity.setRenderedBody(renderedBody);
        entity.setIdempotencyKey(request.idempotencyKey());
        entity.setScheduledAt(request.scheduledAt());

        Instant now = Instant.now();
        boolean isFutureSchedule = request.scheduledAt() != null && request.scheduledAt().isAfter(now);
        entity.setStatus(isFutureSchedule ? NotificationStatus.SCHEDULED : NotificationStatus.PENDING);

        return NotificationResponse.from(notificationRequestRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(Long tenantId) {
        return notificationRequestRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationDetailResponse getDetail(Long tenantId, Long id) {
        NotificationRequest request = notificationRequestRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        List<DeliveryAttemptResponse> attempts = deliveryAttemptRepository
                .findByNotificationRequestIdOrderByAttemptNumberAsc(id).stream()
                .map(DeliveryAttemptResponse::from)
                .toList();
        return NotificationDetailResponse.from(request, attempts);
    }

    @SneakyThrows
    private String writeJson(Map<String, String> variables) {
        return objectMapper.writeValueAsString(variables);
    }
}
