package com.mediassistrover.backend.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component @RequiredArgsConstructor
public class MediaReferenceValidator {
    private final S3ObjectStorageService storage;
    public void requireImageUrl(String value,String field){if(value==null||value.isBlank())return;require(value,Set.of("images"),field);}
    public void validateEvidenceReference(String value,String field){if(value==null||value.isBlank())return;if(value.regionMatches(true,0,"data:",0,5))throw new BusinessRuleException(field+" must be a reference, not embedded data");if(http(value))require(value,Set.of("images","videos"),field);}
    public void validateEvidenceJson(JsonNode node,String field){if(node==null||node.isNull())return;if(node.isTextual()){String v=node.asText();if(v.regionMatches(true,0,"data:",0,5))throw new BusinessRuleException(field+" must not contain embedded data");if(http(v))require(v,Set.of("images","videos"),field);return;}if(node.isContainerNode())node.forEach(c->validateEvidenceJson(c,field));}
    private void require(String value,Set<String> roots,String field){try{storage.keyFromPublicUrl(value.trim(),roots);}catch(IllegalArgumentException e){throw new BusinessRuleException(field+" must use a fixed URL from the configured S3 bucket");}}
    private boolean http(String value){return value.regionMatches(true,0,"http://",0,7)||value.regionMatches(true,0,"https://",0,8);}
}
