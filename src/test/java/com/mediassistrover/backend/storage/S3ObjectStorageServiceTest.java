package com.mediassistrover.backend.storage;
import com.mediassistrover.backend.config.S3Properties;
import com.mediassistrover.backend.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import java.io.ByteArrayInputStream;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
class S3ObjectStorageServiceTest {
    private S3ObjectStorageService service(){S3Properties p=new S3Properties();p.setEnabled(false);p.setRegion("ap-southeast-1");p.setBucket("mediassistrover-media-2026");return new S3ObjectStorageService(mock(S3Client.class),p);}
    @Test void fixedUrlRoundTripAndDelete(){var s=service();byte[] b={1,2,3};String key="images/00000000-0000-0000-0000-000000000001.png";var o=s.put(key,new ByteArrayInputStream(b),3,"image/png","abc",true);assertThat(o.url()).isEqualTo("https://mediassistrover-media-2026.s3.ap-southeast-1.amazonaws.com/"+key);assertThat(s.keyFromPublicUrl(o.url(),Set.of("images"))).isEqualTo(key);s.delete(key);assertThatThrownBy(()->s.head(key,true)).isInstanceOf(ResourceNotFoundException.class);}
    @Test void rejectsTraversalAndWrongBucket(){var s=service();assertThatThrownBy(()->s.publicUrl("images/../secret")).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->s.keyFromPublicUrl("https://other.s3.ap-southeast-1.amazonaws.com/images/a.png",Set.of("images"))).isInstanceOf(IllegalArgumentException.class);}
}
