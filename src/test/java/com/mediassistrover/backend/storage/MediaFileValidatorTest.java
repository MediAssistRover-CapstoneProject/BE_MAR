package com.mediassistrover.backend.storage;
import com.mediassistrover.backend.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
class MediaFileValidatorTest {
    private final MediaFileValidator validator=new MediaFileValidator();
    @Test void detectsPngAndRejectsSpoofing(){byte[] png={(byte)0x89,'P','N','G',0x0d,0x0a,0x1a,0x0a,0,0,0,0};assertThat(validator.imageOrVideo(new MockMultipartFile("file","a.png","image/png",png)).extension()).isEqualTo("png");assertThatThrownBy(()->validator.imageOrVideo(new MockMultipartFile("file","a.jpg","image/png",png))).isInstanceOf(BusinessRuleException.class).hasMessageContaining("extension");}
    @Test void detectsVideoAndWav(){byte[] mp4=new byte[12];System.arraycopy("ftypisom".getBytes(),0,mp4,4,8);assertThat(validator.imageOrVideo(new MockMultipartFile("file","a.mp4","video/mp4",mp4)).kind()).isEqualTo("VIDEO");byte[] wav=new byte[44];System.arraycopy("RIFF".getBytes(),0,wav,0,4);System.arraycopy("WAVE".getBytes(),0,wav,8,4);assertThat(validator.wav(new MockMultipartFile("file","a.wav","audio/wav",wav)).kind()).isEqualTo("AUDIO");}
}
