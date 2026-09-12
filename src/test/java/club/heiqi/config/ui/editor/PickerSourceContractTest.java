package club.heiqi.config.ui.editor;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * 候选源 SPI 值类型与 default 方法契约测试。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §1.2（R-03 版本载体 / Z-4 环境载体 /
 * A2 无 signal 依赖）。</p>
 */
public class PickerSourceContractTest {

    /** 三段版本载体是值类型：相等按三段，跨实例不可比的语义由调用方承担。 */
    @Test
    public void sourceVersionHasValueSemantics() {
        assertEquals(PickerSourceVersion.initial(), new PickerSourceVersion(0L, 0L, 0L));
        assertEquals(new PickerSourceVersion(1L, 2L, 3L), new PickerSourceVersion(1L, 2L, 3L));
        assertEquals(new PickerSourceVersion(1L, 2L, 3L).hashCode(), new PickerSourceVersion(1L, 2L, 3L).hashCode());
        assertNotEquals(new PickerSourceVersion(1L, 2L, 3L), new PickerSourceVersion(1L, 2L, 4L));
    }

    /** 环境代际载体是值类型（下行通道的载荷）。 */
    @Test
    public void environmentHasValueSemantics() {
        assertEquals(new PickerEnvironment(7L, 9L), new PickerEnvironment(7L, 9L));
        assertEquals(new PickerEnvironment(7L, 9L).hashCode(), new PickerEnvironment(7L, 9L).hashCode());
        assertNotEquals(new PickerEnvironment(7L, 9L), new PickerEnvironment(8L, 9L));
        assertEquals(7L, new PickerEnvironment(7L, 9L).nameEpoch());
        assertEquals(9L, new PickerEnvironment(7L, 9L).resourceEpoch());
    }

    /** version() 默认由三段访问器合成；未推送环境代际时保持恒等。 */
    @Test
    public void defaultVersionComposesThreeSegments() {
        StubSource source = new StubSource();
        assertEquals(PickerSourceVersion.initial(), source.version());

        source.registry = 3L;
        assertEquals(3L, source.version().registry());
        assertEquals(0L, source.version().name());
        assertEquals(0L, source.version().icon());

        // 默认 onEnvironmentChanged / release 是无副作用的空实现（纯加法兼容面）
        source.onEnvironmentChanged(5L, 6L);
        source.release();
        assertEquals(new PickerSourceVersion(3L, 0L, 0L), source.version());
    }

    /** 未实现新 SPI 的 provider 探测结果必须是 null（旧路径回退的判据）。 */
    @Test
    public void providerWithoutSpiProbesToNullSource() {
        ValueEditorProvider legacy = new ValueEditorProvider() {
            @Override
            public String id() {
                return "test:legacy";
            }

            @Override
            public Codec codec() {
                return null;
            }

            @Override
            public VisualAdapter visualAdapter() {
                return null;
            }

            @Override
            public SearchFunction searchFunction() {
                return (query, maxResults) -> SearchPickerData.SearchResult.empty();
            }
        };
        assertEquals(false, legacy instanceof CandidateSourceValueEditorProvider);

        CandidateSourceValueEditorProvider defaults = new CandidateSourceValueEditorProvider() {
            @Override
            public String id() {
                return "test:defaults";
            }

            @Override
            public Codec codec() {
                return null;
            }

            @Override
            public VisualAdapter visualAdapter() {
                return null;
            }

            @Override
            public SearchFunction searchFunction() {
                return (query, maxResults) -> SearchPickerData.SearchResult.empty();
            }
        };
        assertEquals(null, defaults.candidateSource());
        assertEquals(null, defaults.iconSource());
    }

    private static final class StubSource implements PickerCandidateSource {
        private long registry;

        @Override
        public int size() {
            return 0;
        }

        @Override
        public long registryRevision() {
            return registry;
        }

        @Override
        public long nameRevision() {
            return 0L;
        }

        @Override
        public long iconRevision() {
            return 0L;
        }

        @Override
        public int matchCount(PickerQuery query) {
            return 0;
        }

        @Override
        public List<SearchPickerData.Candidate> page(PickerQuery query, int offset, int limit) {
            return Collections.emptyList();
        }

        @Override
        public SearchPickerData.Candidate exact(String candidateKey) {
            return null;
        }

        @Override
        public List<SearchPickerCategories.Category> categories(int dimension) {
            return Collections.emptyList();
        }
    }
}
