package com.mediassistrover.backend.service;

import com.mediassistrover.backend.dto.staff.response.StaffMeResponse;
import com.mediassistrover.backend.dto.staff.response.StaffMeResponse.StaffProfileSummary;
import com.mediassistrover.backend.entity.Account;
import com.mediassistrover.backend.entity.StaffProfile;
import com.mediassistrover.backend.enums.AccountStatus;
import com.mediassistrover.backend.enums.Role;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.mapper.AccountMapper;
import com.mediassistrover.backend.mapper.ShiftMapper;
import com.mediassistrover.backend.repository.AccountRepository;
import com.mediassistrover.backend.repository.ShiftRepository;
import com.mediassistrover.backend.repository.StaffProfileRepository;
import com.mediassistrover.backend.security.AccountPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffMeService {
    private final AccountRepository accountRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final ShiftRepository shiftRepository;
    private final AccountMapper accountMapper;
    private final ShiftMapper shiftMapper;
    private final Clock clock;
    private final MediaService media;
    private final com.mediassistrover.backend.storage.MediaFileValidator fileValidator;
    public record Update(@jakarta.validation.constraints.Size(max=160) String fullName,
        @jakarta.validation.constraints.Email @jakarta.validation.constraints.Size(max=254) String email,
        @jakarta.validation.constraints.Size(max=32) String phone,@jakarta.validation.constraints.Size(max=32) String contactExtension) {}
    @Transactional public StaffMeResponse update(AccountPrincipal principal,Update input){
        get(principal);Account a=requireCurrentStaff(principal);var p=staffProfileRepository.findByAccountAccountId(a.getAccountId()).orElseThrow();
        if(input.fullName()!=null){if(input.fullName().isBlank())throw new BusinessRuleException("Name is required");a.setFullName(input.fullName().trim());}
        if(input.email()!=null){String email=input.email().trim().toLowerCase(java.util.Locale.ROOT);if(email.isBlank())throw new BusinessRuleException("Email is required");
            if(!email.equalsIgnoreCase(a.getEmail())&&accountRepository.existsByEmailIgnoreCase(email))throw new com.mediassistrover.backend.exception.ConflictException("Email already exists");a.setEmail(email);}
        if(input.phone()!=null){String phone=input.phone().trim().replaceAll("[ .-]", "");if(!phone.isBlank()&&!phone.equals(a.getPhone())&&accountRepository.existsByPhone(phone))throw new com.mediassistrover.backend.exception.ConflictException("Phone already exists");a.setPhone(phone.isBlank()?null:phone);}
        if(input.contactExtension()!=null)p.setContactExtension(input.contactExtension().trim());
        return get(principal);
    }
    @Transactional public StaffMeResponse avatar(AccountPrincipal principal,org.springframework.web.multipart.MultipartFile file){
        get(principal);Account a=requireCurrentStaff(principal);var p=staffProfileRepository.findByAccountAccountId(a.getAccountId()).orElseThrow();
        if(file==null)p.setUrl(null);
        else {var validated=fileValidator.imageOrVideo(file);if(!"IMAGE".equals(validated.kind()))throw new BusinessRuleException("Avatar must be an image");p.setUrl(media.upload(file).url());}
        return get(principal);
    }


    public StaffProfile requireActiveProfile(AccountPrincipal principal) {
        Account account=requireCurrentStaff(principal);
        StaffProfile profile=staffProfileRepository.findByAccountAccountId(account.getAccountId())
            .orElseThrow(()->new BusinessRuleException("Staff account does not have a StaffProfile"));
        if(!profile.isActive())throw new BusinessRuleException("StaffProfile is inactive");
        return profile;
    }

    public StaffMeResponse get(AccountPrincipal principal) {
        Account account = requireCurrentStaff(principal);
        StaffProfile profile = staffProfileRepository.findByAccountAccountId(account.getAccountId())
                .orElseThrow(() -> new BusinessRuleException("Staff account does not have a StaffProfile"));
        if (!profile.isActive()) {
            throw new BusinessRuleException("StaffProfile is inactive");
        }

        Instant now = Instant.now(clock);
        return new StaffMeResponse(
                accountMapper.toResponse(account),
                new StaffProfileSummary(
                        profile.getStaffProfileId(),
                        profile.getStaffCode(),
                        profile.getDepartmentCode(),
                        profile.getJobTitle(),
                        profile.getContactExtension(),
                        profile.getUrl(),
                        profile.isActive(),
                        profile.getCreatedAt(),
                        profile.getUpdatedAt()),
                shiftRepository.findEffectiveActiveByAccountId(account.getAccountId(), now).stream()
                        .map(shiftMapper::toResponse)
                        .toList());
    }

    private Account requireCurrentStaff(AccountPrincipal principal) {
        if (principal == null || principal.role() != Role.STAFF) {
            throw new AccessDeniedException("A current Staff session is required");
        }
        Account account = accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new AccessDeniedException("Staff account is unavailable"));
        if (account.getRole() != Role.STAFF
                || account.getStatus() != AccountStatus.ACTIVE
                || account.isMustChangePassword()
                || account.getTokenVersion() != principal.tokenVersion()) {
            throw new AccessDeniedException("A current, unrestricted Staff session is required");
        }
        return account;
    }
}
