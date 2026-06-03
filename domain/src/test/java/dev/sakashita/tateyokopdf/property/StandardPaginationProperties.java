package dev.sakashita.tateyokopdf.property;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.domain.model.PagePairSpec;
import dev.sakashita.tateyokopdf.domain.strategy.StandardPagination;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

final class StandardPaginationProperties {

  private final StandardPagination strategy = new StandardPagination();

  @Property
  void everyPageAppearsExactlyOnce(@ForAll @IntRange(min = 1, max = 500) int n) {
    List<PagePairSpec> result = strategy.paginate(n);
    Set<Integer> seen = new HashSet<>();
    for (PagePairSpec p : result) {
      switch (p) {
        case PagePairSpec.Pair pair -> {
          assertThat(seen.add(pair.firstIndex())).as("dup %d", pair.firstIndex()).isTrue();
          assertThat(seen.add(pair.secondIndex())).as("dup %d", pair.secondIndex()).isTrue();
        }
        case PagePairSpec.Single single ->
            assertThat(seen.add(single.pageIndex())).as("dup %d", single.pageIndex()).isTrue();
      }
    }
    assertThat(seen).hasSize(n);
  }

  @Property
  void singleOnlyAppearsAsLastElementWhenNIsOdd(@ForAll @IntRange(min = 1, max = 500) int n) {
    List<PagePairSpec> result = strategy.paginate(n);
    for (int i = 0; i < result.size() - 1; i++) {
      assertThat(result.get(i)).isInstanceOf(PagePairSpec.Pair.class);
    }
    if (n % 2 == 0) {
      assertThat(result.get(result.size() - 1)).isInstanceOf(PagePairSpec.Pair.class);
    } else {
      assertThat(result.get(result.size() - 1)).isInstanceOf(PagePairSpec.Single.class);
    }
  }

  @Property
  void resultSizeIsCeilNOver2(@ForAll @IntRange(min = 1, max = 500) int n) {
    List<PagePairSpec> result = strategy.paginate(n);
    assertThat(result).hasSize((n + 1) / 2);
  }

  @Property
  void pageIndicesAreConsecutive(@ForAll @IntRange(min = 1, max = 500) int n) {
    List<PagePairSpec> result = strategy.paginate(n);
    int expected = 0;
    for (PagePairSpec p : result) {
      switch (p) {
        case PagePairSpec.Pair pair -> {
          assertThat(pair.firstIndex()).isEqualTo(expected);
          assertThat(pair.secondIndex()).isEqualTo(expected + 1);
          expected += 2;
        }
        case PagePairSpec.Single single -> {
          assertThat(single.pageIndex()).isEqualTo(expected);
          expected += 1;
        }
      }
    }
  }
}
