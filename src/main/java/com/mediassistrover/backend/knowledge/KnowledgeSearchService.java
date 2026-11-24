package com.mediassistrover.backend.knowledge;

import com.mediassistrover.backend.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class KnowledgeSearchService {
    public record SearchPage<T>(List<T> content,int page,int size,long totalElements) { }
    public record DocumentHit(UUID id,String filename,String excerpt,Instant updatedAt) { }
    public record ChunkHit(UUID id,UUID documentId,String filename,int ordinal,String content,
                           String status,boolean active) { }

    private final JdbcTemplate jdbc;
    private final String vectorStoreId;

    public KnowledgeSearchService(JdbcTemplate jdbc,
            @Value("${app.knowledge.vector-store-id}") String vectorStoreId) {
        this.jdbc=jdbc;
        this.vectorStoreId=vectorStoreId;
    }

    public SearchPage<DocumentHit> documents(String query,int page,int size) {
        String term=term(query), pattern=pattern(term);
        paging(page,size);
        String where="""
            FROM KnowledgeDocument d
            WHERE d.vector_store_id=? AND d.active=TRUE AND d.status='COMPLETED'
              AND d.extracted_text IS NOT NULL AND LOWER(d.extracted_text) LIKE ? ESCAPE '!'
            """;
        Long total=jdbc.queryForObject("SELECT COUNT(*) "+where,Long.class,vectorStoreId,pattern);
        List<DocumentHit> rows=jdbc.query("""
            SELECT d.document_id,d.filename,
                   SUBSTRING(d.extracted_text,
                     GREATEST(1,LOCATE(?,LOWER(d.extracted_text))-80),240) AS excerpt,
                   d.updated_at
            """+where+" ORDER BY d.created_at DESC,d.document_id DESC LIMIT ? OFFSET ?",
            (rs,n)->new DocumentHit(UUID.fromString(rs.getString("document_id")),rs.getString("filename"),
                rs.getString("excerpt"),rs.getTimestamp("updated_at").toInstant()),
            term,vectorStoreId,pattern,size,(long)page*size);
        return new SearchPage<>(rows,page,size,total==null?0:total);
    }

    public SearchPage<ChunkHit> chunks(String query,UUID documentId,int page,int size) {
        String pattern=pattern(term(query));
        paging(page,size);
        String where="""
            FROM KnowledgeDocumentChunk c
            JOIN KnowledgeDocument d ON d.document_id=c.document_id
            WHERE d.vector_store_id=? AND d.active=TRUE AND d.status='COMPLETED'
              AND c.status IN ('COMPLETED','DETACHED')
              AND LOWER(c.content) LIKE ? ESCAPE '!'
            """+(documentId==null?"":" AND d.document_id=?");
        Object[] args=documentId==null ? new Object[]{vectorStoreId,pattern}
            : new Object[]{vectorStoreId,pattern,documentId.toString()};
        Long total=jdbc.queryForObject("SELECT COUNT(*) "+where,Long.class,args);
        Object[] pageArgs=java.util.Arrays.copyOf(args,args.length+2);
        pageArgs[args.length]=size;
        pageArgs[args.length+1]=(long)page*size;
        List<ChunkHit> rows=jdbc.query("""
            SELECT c.chunk_id,c.document_id,d.filename,c.ordinal_no,c.content,c.status,c.active
            """+where+" ORDER BY d.created_at DESC,d.document_id DESC,c.ordinal_no LIMIT ? OFFSET ?",
            (rs,n)->new ChunkHit(UUID.fromString(rs.getString("chunk_id")),
                UUID.fromString(rs.getString("document_id")),rs.getString("filename"),
                rs.getInt("ordinal_no"),rs.getString("content"),rs.getString("status"),rs.getBoolean("active")),
            pageArgs);
        return new SearchPage<>(rows,page,size,total==null?0:total);
    }

    private static String term(String query) {
        if (query==null) throw new BusinessRuleException("KNOWLEDGE_SEARCH_QUERY_INVALID");
        String term=query.trim();
        if (term.length()<2 || term.length()>200 || term.chars().anyMatch(Character::isISOControl))
            throw new BusinessRuleException("KNOWLEDGE_SEARCH_QUERY_INVALID");
        return term.toLowerCase(Locale.ROOT);
    }

    private static String pattern(String term) {
        return "%"+term.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
    }

    private static void paging(int page,int size) {
        if (page<0 || size<1 || size>50)
            throw new BusinessRuleException("KNOWLEDGE_SEARCH_PAGE_INVALID");
    }
}
