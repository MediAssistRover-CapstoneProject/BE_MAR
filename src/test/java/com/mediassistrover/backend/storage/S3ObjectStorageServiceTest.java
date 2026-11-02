package com.mediassistrover.backend.storage;

import com.mediassistrover.backend.config.S3Properties;
import com.mediassistrover.backend.exception.ResourceNotFoundException;
import com.mediassistrover.backend.exception.StorageUnavailableException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.*;

class S3ObjectStorageServiceTest {
    private S3ObjectStorageService service(){S3Properties p=new S3Properties();p.setEnabled(false);p.setRegion("ap-southeast-1");p.setBucket("mediassistrover-media-2026");return new S3ObjectStorageService(mock(S3Client.class),p);}

    @Test void storesResolvesAndDeletesWithFixedUrl(){var service=service();byte[] bytes={1,2,3};String key="images/00000000-0000-0000-0000-000000000001.png";
        var stored=service.put(key,new ByteArrayInputStream(bytes),bytes.length,"image/png","abc",true);
        assertThat(stored.url()).isEqualTo("https://mediassistrover-media-2026.s3.ap-southeast-1.amazonaws.com/"+key);
        assertThat(service.keyFromPublicUrl(stored.url(),Set.of("images"))).isEqualTo(key);
        assertThat(service.get(key,10)).isEqualTo(bytes);service.delete(key);
        assertThatThrownBy(()->service.head(key,true)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void rejectsTraversalWrongBucketAndPrivateDocumentUrl(){var service=service();
        assertThatThrownBy(()->service.publicUrl("images/../secret")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.keyFromPublicUrl("https://other.s3.ap-southeast-1.amazonaws.com/images/a.png",Set.of("images"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.s3Uri("documents/00000000-0000-0000-0000-000000000001.pdf")).startsWith("s3://");
    }

    @Test void mapsS3NotFoundAndAvailabilityFailures() {
        S3Properties properties=new S3Properties();properties.setRegion("ap-southeast-1");properties.setBucket("mediassistrover-media-2026");
        S3Client client=mock(S3Client.class);var service=new S3ObjectStorageService(client,properties);
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());
        assertThatThrownBy(()->service.head("images/00000000-0000-0000-0000-000000000001.png",true))
                .isInstanceOf(ResourceNotFoundException.class);
        reset(client);when(client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(503).build());
        assertThatThrownBy(()->service.head("images/00000000-0000-0000-0000-000000000001.png",true))
                .isInstanceOf(StorageUnavailableException.class);
    }
}
