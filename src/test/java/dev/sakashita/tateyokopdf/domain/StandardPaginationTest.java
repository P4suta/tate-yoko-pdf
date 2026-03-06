package dev.sakashita.tateyokopdf.domain;

import dev.sakashita.tateyokopdf.domain.model.PagePairSpec;
import dev.sakashita.tateyokopdf.domain.strategy.StandardPagination;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StandardPaginationTest {

    private final StandardPagination strategy = new StandardPagination();

    @Test
    void evenPages_allPairs() {
        List<PagePairSpec> result = strategy.paginate(6);

        assertThat(result).containsExactly(
            new PagePairSpec.Pair(0, 1),
            new PagePairSpec.Pair(2, 3),
            new PagePairSpec.Pair(4, 5)
        );
    }

    @Test
    void oddPages_lastIsSingle() {
        List<PagePairSpec> result = strategy.paginate(5);

        assertThat(result).hasSize(3);
        assertThat(result.getLast()).isEqualTo(new PagePairSpec.Single(4));
    }

    @Test
    void singlePage_oneSingle() {
        List<PagePairSpec> result = strategy.paginate(1);

        assertThat(result).containsExactly(new PagePairSpec.Single(0));
    }

    @Test
    void zeroPages_throws() {
        assertThatThrownBy(() -> strategy.paginate(0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
