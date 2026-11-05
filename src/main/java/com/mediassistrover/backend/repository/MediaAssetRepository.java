package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.MediaAsset;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface MediaAssetRepository extends JpaRepository<MediaAsset,UUID> {
    Optional<MediaAsset> findByObjectKey(String objectKey);
    Optional<MediaAsset> findByObjectKeyAndActiveTrue(String objectKey);
    Page<MediaAsset> findByMediaType(String mediaType,Pageable pageable);
    Page<MediaAsset> findByActiveTrue(Pageable pageable);
    Page<MediaAsset> findByMediaTypeAndActiveTrue(String mediaType,Pageable pageable);
    void deleteByObjectKey(String objectKey);
}
