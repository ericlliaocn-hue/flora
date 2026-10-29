package art.pilipala.api.job.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import art.pilipala.api.job.domain.ModelProduct;
import art.pilipala.api.job.domain.ModelProductGroup;
import art.pilipala.api.job.infra.JobException;
import art.pilipala.api.job.support.InMemoryModelCatalogRepository;
import org.junit.jupiter.api.Test;

class ModelCatalogTest {

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID C4 = UUID.randomUUID();
    private static final UUID C7 = UUID.randomUUID();

    private final ModelProduct product = new ModelProduct(PRODUCT_ID, "seedance-2-720p-stable",
            "video.generate", "Seedance 2 · 720p 稳定", "test", 5000, "{}", true, 10);
    private final ModelProductGroup c4 = group(C4, "default", "sd-2-c4", 10, true);
    private final ModelProductGroup c7 = group(C7, "default", "sd-2-c7", 20, true);

    @Test
    void resolvesTheLowestPriorityEnabledGroup() {
        InMemoryModelCatalogRepository repository = new InMemoryModelCatalogRepository()
                .product(product)
                .group(c7)
                .group(c4);

        ModelCatalog.ResolvedGroup resolved = new ModelCatalog(repository).resolveGroup(product);

        assertThat(resolved.group().groupCode()).isEqualTo("default");
        assertThat(resolved.group().modelCode()).isEqualTo("sd-2-c4");
        assertThat(resolved.group().timeoutSeconds()).isEqualTo(900);
    }

    @Test
    void trimsTheProductCode() {
        InMemoryModelCatalogRepository repository = new InMemoryModelCatalogRepository().product(product);

        assertThat(new ModelCatalog(repository).findProduct("  seedance-2-720p-stable  "))
                .get().extracting(ModelProduct::code).isEqualTo("seedance-2-720p-stable");
    }

    @Test
    void skipsDisabledGroups() {
        InMemoryModelCatalogRepository repository = new InMemoryModelCatalogRepository()
                .product(product)
                .group(group(C4, "default", "sd-2-c4", 10, false))
                .group(c7);

        assertThat(new ModelCatalog(repository).resolveGroup(product).group().modelCode()).isEqualTo("sd-2-c7");
    }

    @Test
    void noEnabledGroupIsNoAvailableChannel() {
        InMemoryModelCatalogRepository repository = new InMemoryModelCatalogRepository().product(product);

        assertThatThrownBy(() -> new ModelCatalog(repository).resolveGroup(product))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("NO_AVAILABLE_CHANNEL"));
    }

    private ModelProductGroup group(UUID id, String groupCode, String modelCode, int priority, boolean enabled) {
        return new ModelProductGroup(id, PRODUCT_ID, groupCode, modelCode, priority, 900,
                new BigDecimal("2.0"), enabled);
    }
}
