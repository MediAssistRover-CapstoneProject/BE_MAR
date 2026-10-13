package com.mediassistrover.backend.mapper;

import com.mediassistrover.backend.dto.account.response.AccountResponse;
import com.mediassistrover.backend.dto.account.response.StaffResponse;
import com.mediassistrover.backend.entity.StaffProfile;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-10-13T11:40:03+0700",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.12.1 (Eclipse Adoptium)"
)
@Component
public class StaffMapperImpl implements StaffMapper {

    @Autowired
    private AccountMapper accountMapper;

    @Override
    public StaffResponse toResponse(StaffProfile profile) {
        if ( profile == null ) {
            return null;
        }

        UUID staffProfileId = null;
        AccountResponse account = null;
        String staffCode = null;
        String departmentCode = null;
        String jobTitle = null;
        String contactExtension = null;
        boolean active = false;

        staffProfileId = profile.getStaffProfileId();
        account = accountMapper.toResponse( profile.getAccount() );
        staffCode = profile.getStaffCode();
        departmentCode = profile.getDepartmentCode();
        jobTitle = profile.getJobTitle();
        contactExtension = profile.getContactExtension();
        active = profile.isActive();

        StaffResponse staffResponse = new StaffResponse( staffProfileId, account, staffCode, departmentCode, jobTitle, contactExtension, active );

        return staffResponse;
    }
}
