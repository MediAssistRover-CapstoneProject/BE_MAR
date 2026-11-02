package com.mediassistrover.backend.storage;

import com.mediassistrover.backend.config.S3Properties;
import com.mediassistrover.backend.exception.ConflictException;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.service.MediaService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaServiceTest {
    record Fixture(MediaService service,StaffProfileRepository staff,S3ObjectStorageService storage){}
    Fixture fixture(){S3Properties p=new S3Properties();p.setEnabled(false);p.setRegion("ap-southeast-1");p.setBucket("mediassistrover-media-2026");var storage=new S3ObjectStorageService(mock(S3Client.class),p);var staff=mock(StaffProfileRepository.class);return new Fixture(new MediaService(storage,new MediaFileValidator(),staff,mock(HeatmapRecordRepository.class),mock(DetectionEventRepository.class),mock(CrowdDensityRecordRepository.class),mock(AuditLogRepository.class)),staff,storage);}

    @Test void uploadUsesUuidKeyAndReturnsFixedUrl(){Fixture f=fixture();byte[] png={(byte)0x89,'P','N','G',0x0d,0x0a,0x1a,0x0a,0,0,0,0};var response=f.service.upload(new MockMultipartFile("file","avatar.png","image/png",png));
        assertThat(response.objectKey()).matches("images/[0-9a-f-]{36}\\.png");assertThat(response.url()).isEqualTo(f.storage.publicUrl(response.objectKey()));assertThat(response.sha256()).hasSize(64);
    }

    @Test void deleteRejectsReferencedUrl(){Fixture f=fixture();byte[] jpg={(byte)0xff,(byte)0xd8,(byte)0xff,0};var uploaded=f.service.upload(new MockMultipartFile("file","a.jpg","image/jpeg",jpg));when(f.staff.existsByUrl(uploaded.url())).thenReturn(true);
        assertThatThrownBy(()->f.service.delete(uploaded.objectKey())).isInstanceOf(ConflictException.class);
        assertThat(f.service.get(uploaded.objectKey()).url()).isEqualTo(uploaded.url());
    }
}
