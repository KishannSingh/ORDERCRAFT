package com.ordercraft.ordercraft;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SalesOrderItemApiSecurityTest {

    @Autowired MockMvc mockMvc;
    private String accessToken;

    @BeforeEach
    void registerAndAuthenticateTestUser() throws Exception {
        String email = "workflow-" + System.nanoTime() + "@example.test";
        String password = "WorkflowTestPassword123!";
        apiAuth(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());
        String loginResponse = apiAuth(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        accessToken = responseValue(loginResponse, "token");
    }

    @Test
    void protectedModuleRejectsMissingOrInvalidTokens() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/customers"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/customers")
                        .header("Authorization", "Bearer malformed.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedInvalidQuantityReachesServiceAndReturnsBadRequest() throws Exception {
        api(post("/order-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":11,\"productId\":4,\"quantity\":0,\"unitPrice\":19.95}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Quantity must be greater than 0"));
    }

    @Test
    void customerOrderProductAndOrderItemPersistAndRecalculateTotal() throws Exception {
        String customerResponse = api(post("/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Customer\",\"email\":\"" + System.nanoTime()
                                + "@example.test\",\"phone\":\"9876543210\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long customerId = responseId(customerResponse, "customerId");

        String productResponse = api(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\":\"TEST-" + System.nanoTime()
                                + "\",\"name\":\"Test Product\",\"price\":12.5}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long productId = responseId(productResponse, "productId");

        String bomResponse = api(post("/boms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId
                                + ",\"materialName\":\"Steel\",\"quantity\":2.5,\"unit\":\"kg\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long bomId = responseId(bomResponse, "bomId");

        api(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/boms/calculate/" + productId + "/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].materialName").value("Steel"))
                .andExpect(jsonPath("$[0].quantity").value(5.0));

        String orderResponse = api(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":" + customerId
                                + ",\"orderDate\":\"" + java.time.LocalDate.now()
                                + "\",\"status\":\"PENDING\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long orderId = responseId(orderResponse, "orderId");

        api(post("/order-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":0,\"productId\":" + productId
                                + ",\"quantity\":1,\"unitPrice\":12.5}"))
                .andExpect(status().isBadRequest());

        api(post("/order-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"productId\":" + productId
                                + ",\"quantity\":1,\"unitPrice\":0}"))
                .andExpect(status().isBadRequest());

        api(post("/order-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId
                                + ",\"productId\":999999,\"quantity\":1,\"unitPrice\":12.5}"))
                .andExpect(status().isNotFound());

        api(post("/order-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":999999,\"productId\":" + productId
                                + ",\"quantity\":1,\"unitPrice\":12.5}"))
                .andExpect(status().isNotFound());

        String itemResponse = api(post("/order-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"productId\":" + productId
                                + ",\"quantity\":2,\"unitPrice\":12.5}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long itemId = responseId(itemResponse, "itemId");

        api(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(25.0));
        api(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/order-items/" + itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(2));

        api(post("/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":999999,\"bomId\":" + bomId
                                + ",\"quantity\":1,\"status\":\"PENDING\"}"))
                .andExpect(status().isNotFound());
        api(post("/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId
                                + ",\"bomId\":999999,\"quantity\":5,\"status\":\"PENDING\"}"))
                .andExpect(status().isNotFound());
        api(post("/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId
                                + ",\"bomId\":" + bomId + ",\"quantity\":5.01,\"status\":\"PENDING\"}"))
                .andExpect(status().isBadRequest());

        String purchaseOrderResponse = api(post("/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId
                                + ",\"bomId\":" + bomId + ",\"quantity\":5,\"status\":\"PENDING\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long purchaseOrderId = responseId(purchaseOrderResponse, "poId");
        api(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/purchase-orders/" + purchaseOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"bomId\":" + bomId
                                + ",\"quantity\":5,\"status\":\"ORDERED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bomId").value(bomId));
        api(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/purchase-orders/" + purchaseOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.bomId").value(bomId))
                .andExpect(jsonPath("$.materialName").value("Steel"))
                .andExpect(jsonPath("$.quantity").value(5.0))
                .andExpect(jsonPath("$.status").value("ORDERED"));

        api(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/order-items/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"productId\":" + productId
                                + ",\"quantity\":1,\"unitPrice\":12.5}"))
                .andExpect(status().isConflict());

        api(post("/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId
                                + ",\"bomId\":" + bomId + ",\"quantity\":0,\"status\":\"PENDING\"}"))
                .andExpect(status().isBadRequest());

        api(post("/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":999999,\"invoiceDate\":\"" + java.time.LocalDate.now()
                                + "\",\"amount\":25,\"status\":\"UNPAID\"}"))
                .andExpect(status().isNotFound());
        api(post("/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId
                                + ",\"invoiceDate\":\"" + java.time.LocalDate.now()
                                + "\",\"amount\":24.99,\"status\":\"UNPAID\"}"))
                .andExpect(status().isBadRequest());

        String invoiceResponse = api(post("/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId
                                + ",\"invoiceDate\":\"" + java.time.LocalDate.now()
                                + "\",\"amount\":25,\"status\":\"PAID\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long invoiceId = responseId(invoiceResponse, "invoiceId");
        api(post("/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId
                                + ",\"invoiceDate\":\"" + java.time.LocalDate.now()
                                + "\",\"amount\":25,\"status\":\"UNPAID\"}"))
                .andExpect(status().isConflict());
        api(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/invoices/" + invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNPAID"));

        api(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceId\":999999,\"paymentDate\":\"" + java.time.LocalDate.now()
                                + "\",\"amount\":1,\"mode\":\"UPI\"}"))
                .andExpect(status().isNotFound());
        api(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceId\":" + invoiceId
                                + ",\"paymentDate\":\"" + java.time.LocalDate.now()
                                + "\",\"amount\":0,\"mode\":\"UPI\"}"))
                .andExpect(status().isBadRequest());

        String firstPaymentResponse = api(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceId\":" + invoiceId
                                + ",\"paymentDate\":\"" + java.time.LocalDate.now()
                                + "\",\"amount\":10,\"mode\":\"UPI\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long firstPaymentId = responseId(firstPaymentResponse, "paymentId");
        api(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/invoices/" + invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY PAID"));

        api(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceId\":" + invoiceId
                                + ",\"paymentDate\":\"" + java.time.LocalDate.now()
                                + "\",\"amount\":15.01,\"mode\":\"UPI\"}"))
                .andExpect(status().isBadRequest());

        String finalPaymentResponse = api(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceId\":" + invoiceId
                                + ",\"paymentDate\":\"" + java.time.LocalDate.now()
                                + "\",\"amount\":15,\"mode\":\"UPI\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long finalPaymentId = responseId(finalPaymentResponse, "paymentId");
        api(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/invoices/" + invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        api(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/payments/" + finalPaymentId))
                .andExpect(status().isNoContent());
        api(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/invoices/" + invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY PAID"));
        api(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/payments/" + firstPaymentId))
                .andExpect(status().isNoContent());
        api(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/invoices/" + invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNPAID"));

        api(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/order-items/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"productId\":" + productId
                                + ",\"quantity\":2,\"unitPrice\":12.5}"))
                .andExpect(status().isConflict());

        api(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/invoices/" + invoiceId))
                .andExpect(status().isNoContent());

        api(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/purchase-orders/" + purchaseOrderId))
                .andExpect(status().isNoContent());
        api(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/order-items/" + itemId))
                .andExpect(status().isNoContent());
        api(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(0.0));

        api(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/boms/" + bomId))
                .andExpect(status().isNoContent());
    }

    private long responseId(String response, String property) {
        return Long.parseLong(responseValue(response, property));
    }

    private String responseValue(String response, String property) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\\"" + property + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
                .matcher(response);
        if (!matcher.find()) {
            java.util.regex.Matcher numberMatcher = java.util.regex.Pattern
                    .compile("\\\"" + property + "\\\"\\s*:\\s*(\\d+)")
                    .matcher(response);
            if (numberMatcher.find()) return numberMatcher.group(1);
            throw new AssertionError("Response did not contain " + property + ": " + response);
        }
        return matcher.group(1);
    }

    private ResultActions api(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + accessToken));
    }

    private ResultActions apiAuth(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request);
    }
}
