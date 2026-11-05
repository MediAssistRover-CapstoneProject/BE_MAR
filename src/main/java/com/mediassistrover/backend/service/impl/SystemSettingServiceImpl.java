package com.mediassistrover.backend.service.impl;

import com.mediassistrover.backend.dto.setting.request.SystemSettingRequests.Create;
import com.mediassistrover.backend.dto.setting.request.SystemSettingRequests.Update;
import com.mediassistrover.backend.dto.setting.response.SystemSettingResponse;
import com.mediassistrover.backend.entity.Account;
import com.mediassistrover.backend.entity.SystemSetting;
import com.mediassistrover.backend.enums.SystemSettingDataType;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.exception.ConflictException;
import com.mediassistrover.backend.exception.ResourceNotFoundException;
import com.mediassistrover.backend.repository.AccountRepository;
import com.mediassistrover.backend.repository.SystemSettingRepository;
import com.mediassistrover.backend.service.AuditService;
import com.mediassistrover.backend.service.RequestMetadata;
import com.mediassistrover.backend.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemSettingServiceImpl implements SystemSettingService {
    private final SystemSettingRepository repository;
    private final AccountRepository accountRepository;
    private final AuditService auditService;
    private final Clock clock;

    @Override
    @Transactional
    public SystemSettingResponse create(Create request, UUID actorId, RequestMetadata metadata) {
        if (repository.existsBySettingKey(request.settingKey())) throw new ConflictException("Setting key already exists");
        validateValue(request.dataType(), request.settingValue());
        Account actor = account(actorId);
        SystemSetting setting = repository.save(SystemSetting.builder()
                .settingKey(request.settingKey()).settingValue(request.settingValue()).dataType(request.dataType())
                .category(request.category().trim().toUpperCase()).description(request.description())
                .publicSetting(request.publicSetting()).active(true).updatedBy(actor).updatedAt(Instant.now(clock)).build());
        auditService.record(actor, "SYSTEM_SETTING_CREATED", "SystemSetting", setting.getSystemSettingId(),
                null, auditData(setting), metadata);
        return toResponse(setting);
    }

    @Override
    @Transactional
    public SystemSettingResponse update(UUID id, Update request, UUID actorId, RequestMetadata metadata) {
        validateValue(request.dataType(), request.settingValue());
        SystemSetting setting = setting(id);
        Map<String, Object> before = auditData(setting);
        Account actor = account(actorId);
        setting.setSettingValue(request.settingValue());
        setting.setDataType(request.dataType());
        setting.setCategory(request.category().trim().toUpperCase());
        setting.setDescription(request.description());
        setting.setPublicSetting(request.publicSetting());
        setting.setUpdatedBy(actor);
        setting.setUpdatedAt(Instant.now(clock));
        auditService.record(actor, "SYSTEM_SETTING_UPDATED", "SystemSetting", id, before, auditData(setting), metadata);
        return toResponse(setting);
    }

    @Override public SystemSettingResponse get(UUID id,boolean includeDeleted) {SystemSetting s=setting(id);if(!includeDeleted&&!s.isActive())throw new ResourceNotFoundException("SystemSetting not found: "+id);return toResponse(s);}

    @Override
    public Page<SystemSettingResponse> list(String category,boolean includeDeleted, Pageable pageable) {
        return (category == null || category.isBlank() ? (includeDeleted?repository.findAll(pageable):repository.findByActiveTrue(pageable))
                : (includeDeleted?repository.findByCategoryIgnoreCase(category.trim(),pageable):repository.findByCategoryIgnoreCaseAndActiveTrue(category.trim(), pageable))).map(this::toResponse);
    }

    @Override
    @Transactional
    public SystemSettingResponse delete(UUID id,String reason, UUID actorId, RequestMetadata metadata) {
        SystemSetting setting = setting(id);
        Account actor = account(actorId);
        Map<String, Object> before = auditData(setting);
        setting.setActive(false);setting.setUpdatedBy(actor);setting.setUpdatedAt(Instant.now(clock));
        auditService.record(actor, "SYSTEM_SETTING_RETIRED", "SystemSetting", id, before, Map.of("active",false,"reason",reason), metadata);return toResponse(setting);
    }
    @Override @Transactional public SystemSettingResponse restore(UUID id,String reason,UUID actorId,RequestMetadata metadata){SystemSetting s=setting(id);if(s.isActive())throw new BusinessRuleException("SystemSetting is already active");Account actor=account(actorId);s.setActive(true);s.setUpdatedBy(actor);s.setUpdatedAt(Instant.now(clock));auditService.record(actor,"SYSTEM_SETTING_RESTORED","SystemSetting",id,Map.of("active",false),Map.of("active",true,"reason",reason),metadata);return toResponse(s);}
    @Override public com.mediassistrover.backend.dto.common.DeletionContracts.DeletionImpactResponse deletionImpact(UUID id){setting(id);return new com.mediassistrover.backend.dto.common.DeletionContracts.DeletionImpactResponse(id,"SystemSetting",true,java.util.List.of(),Map.of());}
    @Override @Transactional public com.mediassistrover.backend.dto.common.DeletionContracts.HardDeleteResponse forceDelete(UUID id,String reason,UUID actorId,RequestMetadata metadata){SystemSetting s=setting(id);Account actor=account(actorId);repository.delete(s);var result=new com.mediassistrover.backend.dto.common.DeletionContracts.HardDeleteResponse(id,"SystemSetting",Map.of("SystemSetting",1L));auditService.record(actor,"SYSTEM_SETTING_FORCE_DELETED","SystemSetting",id,auditData(s),Map.of("reason",reason),metadata);return result;}

    private void validateValue(SystemSettingDataType type, Map<String, Object> value) {
        Object scalar = value.get("value");
        boolean valid = switch (type) {
            case NUMBER -> scalar instanceof Number;
            case BOOLEAN -> scalar instanceof Boolean;
            case STRING -> scalar instanceof String;
            case OBJECT -> scalar instanceof Map<?, ?>;
            case ARRAY -> scalar instanceof Iterable<?>;
        };
        if (!valid) throw new BusinessRuleException("settingValue.value does not match dataType " + type);
    }

    private SystemSetting setting(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("SystemSetting not found: " + id));
    }
    private Account account(UUID id) {
        return accountRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
    }
    private Map<String, Object> auditData(SystemSetting setting) {
        return Map.of("settingKey", setting.getSettingKey(), "category", setting.getCategory(),
                "dataType", setting.getDataType().name(), "publicSetting", setting.isPublicSetting());
    }
    private SystemSettingResponse toResponse(SystemSetting setting) {
        return new SystemSettingResponse(setting.getSystemSettingId(), setting.getSettingKey(), setting.getSettingValue(),
                setting.getDataType(), setting.getCategory(), setting.getDescription(), setting.isPublicSetting(),setting.isActive(),
                setting.getUpdatedBy() == null ? null : setting.getUpdatedBy().getAccountId(), setting.getUpdatedAt());
    }
}
