package com.mediassistrover.backend.service;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.mapper.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.storage.MediaFileValidator;
import com.mediassistrover.backend.dto.media.response.MediaObjectResponse;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StaffMeUpdateTest {
 final AccountRepository accounts=mock(AccountRepository.class);final StaffProfileRepository profiles=mock(StaffProfileRepository.class);final ShiftRepository shifts=mock(ShiftRepository.class);
 final MediaService media=mock(MediaService.class);final MediaFileValidator validator=mock(MediaFileValidator.class);
 final StaffMeService service=new StaffMeService(accounts,profiles,shifts,mock(AccountMapper.class),mock(ShiftMapper.class),Clock.systemUTC(),media,validator);
 Account a;StaffProfile p;AccountPrincipal actor;
 @BeforeEach void setup(){a=Account.builder().accountId(UUID.randomUUID()).role(Role.STAFF).status(AccountStatus.ACTIVE).fullName("Staff").email("a@example.com").build();actor=AccountPrincipal.fromAccount(a);
 p=StaffProfile.builder().staffProfileId(UUID.randomUUID()).account(a).active(true).url("https://bucket/images/old.png").build();when(accounts.findById(a.getAccountId())).thenReturn(Optional.of(a));when(profiles.findByAccountAccountId(a.getAccountId())).thenReturn(Optional.of(p));when(shifts.findEffectiveActiveByAccountId(any(),any())).thenReturn(List.of());}
 @Test void updatesOnlyPersonalFields(){service.update(actor,new StaffMeService.Update("New Name",null,"012 345","123"));assertEquals("New Name",a.getFullName());assertEquals("012345",a.getPhone());assertEquals("123",p.getContactExtension());assertEquals(Role.STAFF,a.getRole());}
 @Test void rejectsDuplicateEmail(){when(accounts.existsByEmailIgnoreCase("b@example.com")).thenReturn(true);assertThrows(ConflictException.class,()->service.update(actor,new StaffMeService.Update(null,"b@example.com",null,null)));}
 @Test void clearAvatarDoesNotDeleteSharedObject(){service.avatar(actor,null);assertNull(p.getUrl());verifyNoInteractions(media);}
 @Test void failedUploadRetainsOldImage(){var file=new MockMultipartFile("file","a.png","image/png",new byte[]{1});
 when(validator.imageOrVideo(file)).thenReturn(new MediaFileValidator.Validated("IMAGE","image/png","png",1));when(media.upload(file)).thenThrow(new BusinessRuleException("S3 unavailable"));
 assertThrows(BusinessRuleException.class,()->service.avatar(actor,file));assertEquals("https://bucket/images/old.png",p.getUrl());}
 @Test void successfulUploadReplacesReference(){var file=new MockMultipartFile("file","a.png","image/png",new byte[]{1});when(validator.imageOrVideo(file)).thenReturn(new MediaFileValidator.Validated("IMAGE","image/png","png",1));when(media.upload(file)).thenReturn(new MediaObjectResponse("images/new.png","https://bucket/images/new.png","IMAGE","image/png",1,"hash"));service.avatar(actor,file);assertEquals("https://bucket/images/new.png",p.getUrl());}
 @Test void videoCannotBecomeAvatar(){var file=new MockMultipartFile("file","a.mp4","video/mp4",new byte[]{1});when(validator.imageOrVideo(file)).thenReturn(new MediaFileValidator.Validated("VIDEO","video/mp4","mp4",1));assertThrows(BusinessRuleException.class,()->service.avatar(actor,file));verifyNoInteractions(media);}
 @Test void inactiveAccountCannotEdit(){a.setStatus(AccountStatus.INACTIVE);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.update(actor,new StaffMeService.Update("x",null,null,null)));}
 @Test void invalidFileSignatureNeverReachesS3(){
 org.springframework.test.util.ReflectionTestUtils.setField(service,"fileValidator",new MediaFileValidator());
 var file=new MockMultipartFile("file","fake.png","image/png",new byte[]{1,2,3});
 assertThrows(BusinessRuleException.class,()->service.avatar(actor,file));verifyNoInteractions(media);}
 @Test void oversizedImageNeverReachesS3(){
 org.springframework.test.util.ReflectionTestUtils.setField(service,"fileValidator",new MediaFileValidator());
 byte[] bytes=new byte[(int)MediaFileValidator.IMAGE_MAX+1];byte[] header={(byte)137,80,78,71,13,10,26,10};System.arraycopy(header,0,bytes,0,header.length);
 var file=new MockMultipartFile("file","large.png","image/png",bytes);
 assertThrows(com.mediassistrover.backend.exception.FileTooLargeException.class,()->service.avatar(actor,file));verifyNoInteractions(media);}
}
