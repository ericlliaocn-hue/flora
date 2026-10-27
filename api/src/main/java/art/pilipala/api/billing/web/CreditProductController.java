package art.pilipala.api.billing.web;

import java.util.List;

import art.pilipala.api.billing.application.CreditProductView;
import art.pilipala.api.billing.application.ListCreditProducts;
import art.pilipala.api.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing/credit-products")
public class CreditProductController {

    private final ListCreditProducts products;

    public CreditProductController(ListCreditProducts products) {
        this.products = products;
    }

    @GetMapping
    public ApiResponse<List<CreditProductView>> list(@RequestParam(value = "tab", required = false) String tab) {
        return ApiResponse.ok(products.listEnabled(tab));
    }
}
