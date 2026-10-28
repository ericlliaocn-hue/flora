package art.pilipala.api.job.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import art.pilipala.api.job.domain.ModelProduct;
import art.pilipala.api.job.domain.ModelRoute;
import art.pilipala.api.job.domain.ModelTarget;
import art.pilipala.api.job.infra.JobException;
import art.pilipala.api.job.support.InMemoryModelCatalogRepository;
import org.junit.jupiter.api.Test;

class ModelCatalogTest {

    private static final UUID PRODUCT_ID = UUID.randomUUID();

    private final ModelProduct product = new ModelProduct(PRODUCT_ID, "seedance-2-720p-stable",
            "video.generate", "Seedance 2 · 720p 稳定", "test", 1000, "{}", true, 10);
    private final ModelTarget c4 = target("newapi/sd-2-c4", "sd-2-c4", true);
    private final ModelTarget c7 = target("newapi/sd-2-c7", "sd-2-c7", true);
    private final ModelTarget c8 = target("newapi/sd-2-c8", "sd-2-c8", true);

    @Test
    void resolvesEnabledTargetsInPriorityOrder() {
        InMemoryModelCatalogRepository repository = new InMemoryModelCatalogRepository()
                .product(product)
                .target(c4)
                .target(c7)
                .route(route(c7, 20, true))
                .route(route(c4, 10, true));

        ModelCatalog.ResolvedProduct resolved = new ModelCatalog(repository).resolve("seedance-2-720p-stable");

        assertThat(resolved.product().code()).isEqualTo("seedance-2-720p-stable");
        assertThat(resolved.product().priceCredits()).isEqualTo(1000);
        assertThat(resolved.targets()).extracting(t -> t.target().code())
                .containsExactly("newapi/sd-2-c4", "newapi/sd-2-c7");
        assertThat(resolved.primary().target().upstreamModel()).isEqualTo("sd-2-c4");
        assertThat(resolved.primary().maxCostAmount()).isEqualByComparingTo("2.0");
    }

    @Test
    void trimsTheProductCode() {
        InMemoryModelCatalogRepository repository = new InMemoryModelCatalogRepository()
                .product(product)
                .target(c4)
                .route(route(c4, 10, true));

        assertThat(new ModelCatalog(repository).resolve("  seedance-2-720p-stable  ").product().code())
                .isEqualTo("seedance-2-720p-stable");
    }

    @Test
    void skipsDisabledRouteAndDisabledTarget() {
        ModelTarget c8Disabled = target("newapi/sd-2-c8", "sd-2-c8", false);
        InMemoryModelCatalogRepository repository = new InMemoryModelCatalogRepository()
                .product(product)
                .target(c4)
                .target(c7)
                .target(c8Disabled)
                .route(route(c4, 10, true))
                .route(route(c7, 20, false))
                .route(route(c8Disabled, 30, true));

        ModelCatalog.ResolvedProduct resolved = new ModelCatalog(repository).resolve("seedance-2-720p-stable");

        assertThat(resolved.targets()).extracting(t -> t.target().code())
                .containsExactly("newapi/sd-2-c4");
    }

    @Test
    void targetDisabledIsSkippedEvenIfRouteIsEnabled() {
        ModelTarget disabled = target("newapi/sd-2-c4", "sd-2-c4", false);
        InMemoryModelCatalogRepository repository = new InMemoryModelCatalogRepository()
                .product(product)
                .target(disabled)
                .target(c7)
                .route(route(disabled, 10, true))
                .route(route(c7, 20, true));

        assertThat(new ModelCatalog(repository).resolve("seedance-2-720p-stable").targets())
                .extracting(t -> t.target().code())
                .containsExactly("newapi/sd-2-c7");
    }

    @Test
    void unknownProductIsValidationError() {
        InMemoryModelCatalogRepository repository = new InMemoryModelCatalogRepository();

        assertThatThrownBy(() -> new ModelCatalog(repository).resolve("missing"))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("VALIDATION_ERROR"));
    }

    @Test
    void disabledProductIsValidationError() {
        ModelProduct disabled = new ModelProduct(PRODUCT_ID, product.code(), product.capability(),
                product.displayName(), product.description(), product.priceCredits(), product.paramSchemaJson(),
                false, product.sortOrder());
        InMemoryModelCatalogRepository repository = new InMemoryModelCatalogRepository()
                .product(disabled)
                .target(c4)
                .route(route(c4, 10, true));

        assertThatThrownBy(() -> new ModelCatalog(repository).resolve(disabled.code()))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("VALIDATION_ERROR"));
    }

    @Test
    void noEnabledTargetIsNoAvailableChannel() {
        InMemoryModelCatalogRepository repository = new InMemoryModelCatalogRepository()
                .product(product)
                .target(c4)
                .route(route(c4, 10, false));

        assertThatThrownBy(() -> new ModelCatalog(repository).resolve(product.code()))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("NO_AVAILABLE_CHANNEL"));
    }

    private ModelTarget target(String code, String upstreamModel, boolean enabled) {
        return new ModelTarget(UUID.randomUUID(), code, "newapi", upstreamModel, "{}", "per_call",
                new BigDecimal("1.8"), "USD", 900, enabled);
    }

    private ModelRoute route(ModelTarget target, int priority, boolean enabled) {
        return new ModelRoute(UUID.randomUUID(), PRODUCT_ID, target.id(), priority, new BigDecimal("2.0"), enabled);
    }
}
