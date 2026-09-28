package com.notificationservice.service;

import com.notificationservice.dto.TemplateCreateRequest;
import com.notificationservice.dto.TemplateResponse;
import com.notificationservice.dto.TemplateUpdateRequest;
import com.notificationservice.entity.NotificationTemplate;
import com.notificationservice.exception.DuplicateTemplateNameException;
import com.notificationservice.exception.TemplateNotFoundException;
import com.notificationservice.repository.NotificationTemplateRepository;
import com.notificationservice.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationTemplateService {

    private final NotificationTemplateRepository templateRepository;
    private final TenantRepository tenantRepository;

    @Transactional
    public TemplateResponse create(Long tenantId, TemplateCreateRequest request) {
        if (templateRepository.existsByTenantIdAndName(tenantId, request.name())) {
            throw new DuplicateTemplateNameException(request.name());
        }
        NotificationTemplate template = new NotificationTemplate();
        template.setTenant(tenantRepository.getReferenceById(tenantId));
        template.setName(request.name());
        template.setChannel(request.channel());
        template.setSubject(request.subject());
        template.setBody(request.body());
        return TemplateResponse.from(templateRepository.save(template));
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> list(Long tenantId) {
        return templateRepository.findByTenantId(tenantId).stream()
                .map(TemplateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse get(Long tenantId, Long id) {
        return TemplateResponse.from(findOwned(tenantId, id));
    }

    @Transactional
    public TemplateResponse update(Long tenantId, Long id, TemplateUpdateRequest request) {
        NotificationTemplate template = findOwned(tenantId, id);
        template.setSubject(request.subject());
        template.setBody(request.body());
        return TemplateResponse.from(template);
    }

    @Transactional
    public void delete(Long tenantId, Long id) {
        templateRepository.delete(findOwned(tenantId, id));
    }

    private NotificationTemplate findOwned(Long tenantId, Long id) {
        return templateRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new TemplateNotFoundException(id));
    }
}
