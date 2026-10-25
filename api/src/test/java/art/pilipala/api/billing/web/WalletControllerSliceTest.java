package art.pilipala.api.billing.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.auth.infra.SaTokenConfig;
import art.pilipala.api.billing.application.GetWallet;
import art.pilipala.api.billing.application.WalletView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * C 端钱包接口的切片测试：信封、snake_case 字段。
 *
 * <p>鉴权闸门（SaTokenConfig）在这个切片里被排除掉：会话由 mock 的
 * {@link AuthSessionService} 提供，只测控制器与信封这一层。
 */
@WebMvcTest(controllers = WalletController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SaTokenConfig.class))
class WalletControllerSliceTest {

    private static final UUID USER = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthSessionService sessions;

    @MockitoBean
    private GetWallet getWallet;

    @Test
    void walletReturnsTheSnapshot() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(getWallet.get(USER))
                .thenReturn(new WalletView(USER.toString(), 90, 10, Instant.parse("2026-10-24T04:30:00Z")));

        mockMvc.perform(get("/api/v1/wallet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.error_code").doesNotExist())
                .andExpect(jsonPath("$.data.user_id").value(USER.toString()))
                .andExpect(jsonPath("$.data.available").value(90))
                .andExpect(jsonPath("$.data.frozen").value(10))
                .andExpect(jsonPath("$.data.updated_at").value("2026-10-24T04:30:00Z"));
    }
}
