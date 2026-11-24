package com.mediassistrover.backend.knowledge;

import com.mediassistrover.backend.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KnowledgeSearchTest {
    private JdbcTemplate jdbc;
    private KnowledgeSearchService service;

    @BeforeEach void setup() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+
            ";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(source);
        jdbc.execute("""
            CREATE TABLE KnowledgeDocument (
              document_id VARCHAR(36) PRIMARY KEY,vector_store_id VARCHAR(100),filename VARCHAR(255),
              status VARCHAR(40),active BOOLEAN,extracted_text CLOB,created_at TIMESTAMP,updated_at TIMESTAMP)
            """);
        jdbc.execute("""
            CREATE TABLE KnowledgeDocumentChunk (
              chunk_id VARCHAR(36) PRIMARY KEY,document_id VARCHAR(36),ordinal_no INT,
              content CLOB,status VARCHAR(40),active BOOLEAN)
            """);
        service=new KnowledgeSearchService(jdbc,"vs_test");
    }

    private UUID document(String name,String text,String status,boolean active,String store) {
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO KnowledgeDocument VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """,id.toString(),store,name,status,active,text);
        return id;
    }

    private UUID chunk(UUID document,int ordinal,String text,String status,boolean active) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO KnowledgeDocumentChunk VALUES(?,?,?,?,?,?)",
            id.toString(),document.toString(),ordinal,text,status,active);
        return id;
    }

    @Test void searchesReadyOcrTextAndReadyChunksWithLiteralWildcards() {
        UUID current=document("guide.pdf","Tổng đài 1900_1234; phí 20%.","COMPLETED",true,"vs_test");
        UUID activeChunk=chunk(current,0,"Tổng đài 1900_1234","COMPLETED",true);
        UUID inactiveChunk=chunk(current,1,"Phí 20%", "DETACHED",false);
        document("pending.pdf","Tổng đài 1900_1234","PROCESSING",true,"vs_test");
        document("old.pdf","Tổng đài 1900_1234","COMPLETED",false,"vs_test");
        document("other.pdf","Tổng đài 1900_1234","COMPLETED",true,"vs_other");

        var docs=service.documents("1900_1234",0,20);
        assertEquals(1,docs.totalElements());
        assertEquals(current,docs.content().getFirst().id());
        assertTrue(docs.content().getFirst().excerpt().contains("1900_1234"));
        assertEquals(0,service.documents("1900X1234",0,20).totalElements());

        var chunks=service.chunks("1900_1234",null,0,20);
        assertEquals(1,chunks.totalElements());
        assertEquals(activeChunk,chunks.content().getFirst().id());
        assertEquals(current,chunks.content().getFirst().documentId());
        assertEquals(1,service.chunks("20%",current,0,20).totalElements());
        assertEquals(inactiveChunk,service.chunks("20%",current,0,20).content().getFirst().id());
        assertFalse(service.chunks("20%",current,0,20).content().getFirst().active());
        assertEquals(0,service.chunks("20%",UUID.randomUUID(),0,20).totalElements());
    }

    @Test void validatesQueryAndPagination() {
        assertThrows(BusinessRuleException.class,()->service.documents(" ",0,20));
        assertThrows(BusinessRuleException.class,()->service.chunks("x",null,0,20));
        assertThrows(BusinessRuleException.class,()->service.documents("valid",-1,20));
        assertThrows(BusinessRuleException.class,()->service.chunks("valid",null,0,51));
    }

    @Test void pagesDocumentsAndChunksWithoutRepeatingResults() {
        UUID first=document("first.txt","Lịch khám thứ Hai","COMPLETED",true,"vs_test");
        UUID second=document("second.txt","Lịch khám thứ Ba","COMPLETED",true,"vs_test");
        chunk(first,0,"Lịch khám thứ Hai","COMPLETED",true);
        chunk(second,0,"Lịch khám thứ Ba","COMPLETED",true);
        var firstPage=service.documents("lịch khám",0,1);
        var secondPage=service.documents("lịch khám",1,1);
        assertEquals(2,firstPage.totalElements());
        assertEquals(2,secondPage.totalElements());
        assertEquals(1,firstPage.content().size());
        assertEquals(1,secondPage.content().size());
        assertNotEquals(firstPage.content().getFirst().id(),secondPage.content().getFirst().id());
        var chunkPage=service.chunks("LỊCH KHÁM",null,0,1);
        assertEquals(2,chunkPage.totalElements());
        assertEquals(1,chunkPage.content().size());
    }

    @Test void documentSearchRouteTakesPrecedenceOverDocumentIdRoute() throws Exception {
        var search=mock(KnowledgeSearchService.class);
        var documents=mock(KnowledgeDocumentService.class);
        when(search.documents("giờ khám",0,20)).thenReturn(new KnowledgeSearchService.SearchPage<>(
            java.util.List.of(),0,20,0));
        var mvc=MockMvcBuilders.standaloneSetup(new KnowledgeSearchController(search),
            new KnowledgeDocumentController(documents)).build();
        mvc.perform(get("/api/v1/knowledge/documents/search").param("q","giờ khám"))
            .andExpect(status().isOk());
        verify(search).documents("giờ khám",0,20);
        verifyNoInteractions(documents);
    }
}
