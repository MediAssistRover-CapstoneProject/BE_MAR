package com.mediassistrover.backend.storage;

import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.exception.FileTooLargeException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.*;

class MediaFileValidatorTest {
    private final MediaFileValidator validator = new MediaFileValidator();

    @Test void detectsPngAndRejectsSpoofedMime() {
        byte[] png={(byte)0x89,'P','N','G',0x0d,0x0a,0x1a,0x0a,0,0,0,0};
        var valid=validator.imageOrVideo(new MockMultipartFile("file","a.png","image/png",png));
        assertThat(valid.kind()).isEqualTo("IMAGE");assertThat(valid.extension()).isEqualTo("png");
        assertThatThrownBy(()->validator.imageOrVideo(new MockMultipartFile("file","a.jpg","image/jpeg",png)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("does not match");
        assertThatThrownBy(()->validator.imageOrVideo(new MockMultipartFile("file","a.jpg","image/png",png)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("extension");
    }

    @Test void detectsWebmMp4MovAndWav() {
        byte[] webm={(byte)0x1a,0x45,(byte)0xdf,(byte)0xa3};
        assertThat(validator.imageOrVideo(new MockMultipartFile("file","a.webm","video/webm",webm)).extension()).isEqualTo("webm");
        byte[] mp4=new byte[12];System.arraycopy("ftypisom".getBytes(),0,mp4,4,8);
        assertThat(validator.imageOrVideo(new MockMultipartFile("file","a.mp4","video/mp4",mp4)).extension()).isEqualTo("mp4");
        byte[] mov=new byte[12];System.arraycopy("ftypqt  ".getBytes(),0,mov,4,8);
        assertThat(validator.imageOrVideo(new MockMultipartFile("file","a.mov","video/quicktime",mov)).extension()).isEqualTo("mov");
        byte[] wav=new byte[44];System.arraycopy("RIFF".getBytes(),0,wav,0,4);System.arraycopy("WAVE".getBytes(),0,wav,8,4);
        assertThat(validator.wav(new MockMultipartFile("file","a.wav","audio/wav",wav)).kind()).isEqualTo("AUDIO");
    }

    @Test void rejectsUnknownAndEmptyFiles() {
        assertThatThrownBy(()->validator.imageOrVideo(new MockMultipartFile("file","x.bin","application/octet-stream",new byte[]{1,2,3}))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(()->validator.imageOrVideo(new MockMultipartFile("file",new byte[0]))).isInstanceOf(BusinessRuleException.class);
    }

    @Test void rejectsImageAboveTheServiceLimit() {
        byte[] png = new byte[(int) MediaFileValidator.IMAGE_MAX + 1];
        byte[] signature={(byte)0x89,'P','N','G',0x0d,0x0a,0x1a,0x0a};
        System.arraycopy(signature, 0, png, 0, signature.length);
        assertThatThrownBy(() -> validator.imageOrVideo(
                new MockMultipartFile("file", "large.png", "image/png", png)))
                .isInstanceOf(FileTooLargeException.class);
    }
}
