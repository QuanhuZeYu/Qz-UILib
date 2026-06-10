package club.heiqi.uilib.ui.control;

import java.util.List;

/**
 * 自动完成输入框候选提供器。
 */
public interface DocumentAutocompleteSuggestionProvider {

    /**
     * 根据当前查询文本返回候选项。
     *
     * @param query 当前输入文本；不会为 null
     * @return 候选项列表；返回 null 时按空列表处理
     */
    List<String> getSuggestions(String query);
}
