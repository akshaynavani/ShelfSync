package com.shelfsync.inventory;

import com.shelfsync.TestFixtures;
import com.shelfsync.config.ShelfSyncProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
@TestPropertySource(properties = "shelfsync.security.api-key=secret")
class InventoryControllerTest {

    @TestConfiguration
    @EnableConfigurationProperties(ShelfSyncProperties.class)
    static class Config {
    }

    @Autowired
    private MockMvc mvc;

    @MockBean
    private InventoryService service;

    @Test
    void rejectsRequestsWithoutApiKey() throws Exception {
        mvc.perform(get("/api/v1/items")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsItems() throws Exception {
        when(service.list(null)).thenReturn(List.of(TestFixtures.item("MUG", "7.50", 1)));

        mvc.perform(get("/api/v1/items").header("X-Api-Key", "secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("MUG"))
                .andExpect(jsonPath("$[0].lowStock").value(true));
    }

    @Test
    void createsItem() throws Exception {
        when(service.create(any())).thenReturn(TestFixtures.item("MUG", "7.50", 10));

        mvc.perform(post("/api/v1/items").header("X-Api-Key", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"MUG","name":"Mug","category":"kitchen","unitPrice":7.50,"initialStock":10,"reorderLevel":2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/items/MUG"))
                .andExpect(jsonPath("$.stock").value(10));
    }

    @Test
    void validatesCreateRequest() throws Exception {
        mvc.perform(post("/api/v1/items").header("X-Api-Key", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"bad sku!","name":"","category":"kitchen","unitPrice":-1,"initialStock":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sku").exists())
                .andExpect(jsonPath("$.fieldErrors.unitPrice").exists());
    }

    @Test
    void mapsInsufficientStockToConflict() throws Exception {
        when(service.adjust("MUG", -50, null)).thenThrow(new InsufficientStockException("MUG", 50, 3));

        mvc.perform(post("/api/v1/items/MUG/adjust").header("X-Api-Key", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":-50}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Insufficient stock for MUG: requested 50, available 3"));
    }
}
