package club.heiqi.uilib.ui.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * CSS-like 选择器。
 *
 * <p>支持标签、类、ID、通配符、复合选择器、后代选择器、子代选择器，以及常用伪类。
 * 特异性（specificity）按 CSS 标准计算：(id数, class/伪类数, tag数)。</p>
 */
public final class UiSelector {

    private final List<SelectorStep> steps;
    private final SimpleSelector rightMostSelector;
    private final int specificityId;
    private final int specificityClass;
    private final int specificityTag;

    private UiSelector(List<SelectorStep> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("Selector must contain at least one step");
        }
        this.steps = Collections.unmodifiableList(new ArrayList<SelectorStep>(steps));
        this.rightMostSelector = this.steps.get(this.steps.size() - 1).selector;
        int idCount = 0;
        int classCount = 0;
        int tagCount = 0;
        for (SelectorStep step : this.steps) {
            idCount += step.selector.specificityId;
            classCount += step.selector.specificityClass;
            tagCount += step.selector.specificityTag;
        }
        this.specificityId = idCount;
        this.specificityClass = classCount;
        this.specificityTag = tagCount;
    }

    private UiSelector(String tagName, List<String> classNames, String id, boolean universal,
            List<PseudoCondition> pseudoConditions) {
        this(singleStep(new SimpleSelector(tagName, classNames, id, universal, pseudoConditions)));
    }

    private UiSelector(String tagName, List<String> classNames, String id, boolean universal,
            UiPseudoClass pseudoClass) {
        this(tagName, classNames, id, universal, pseudoClass == null ? null
                : Collections.singletonList(PseudoCondition.stateOrStructural(pseudoClass, null)));
    }

    private UiSelector(String tagName, List<String> classNames, String id, boolean universal) {
        this(tagName, classNames, id, universal, (List<PseudoCondition>) null);
    }

    /**
     * 创建标签选择器。
     *
     * @param tagName 标签名
     * @return 选择器
     */
    public static UiSelector tag(String tagName) {
        Objects.requireNonNull(tagName, "tagName");
        return new UiSelector(tagName.trim().toLowerCase(java.util.Locale.ROOT), null, null, false);
    }

    /**
     * 创建类选择器。
     *
     * @param className 类名（不含前导点号）
     * @return 选择器
     */
    public static UiSelector className(String className) {
        Objects.requireNonNull(className, "className");
        List<String> list = new ArrayList<String>(1);
        list.add(className.trim());
        return new UiSelector(null, list, null, false);
    }

    /**
     * 创建 ID 选择器。
     *
     * @param id ID 值（不含前导井号）
     * @return 选择器
     */
    public static UiSelector id(String id) {
        Objects.requireNonNull(id, "id");
        return new UiSelector(null, null, id.trim(), false);
    }

    /**
     * 创建通配符选择器（匹配所有元素）。
     *
     * @return 通配符选择器
     */
    public static UiSelector universal() {
        return new UiSelector(null, null, null, true);
    }

    /**
     * 从 CSS-like 选择器字符串解析。
     *
     * <p>支持格式包括 {@code tag.class#id:pseudo}、{@code A B}、{@code A > B}、
     * {@code :first-child}、{@code :last-child}、{@code :nth-child(2n+1)}。
     * 当前不支持逗号分组、兄弟组合器、属性选择器或伪元素。</p>
     *
     * @param selectorText 选择器文本
     * @return 解析后的选择器
     * @throws IllegalArgumentException 格式无效时
     */
    public static UiSelector parse(String selectorText) {
        Objects.requireNonNull(selectorText, "selectorText");
        String text = selectorText.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Selector text cannot be empty");
        }

        List<SelectorStep> parsedSteps = new ArrayList<SelectorStep>();
        Combinator pendingCombinator = null;
        boolean expectingSimpleSelector = true;
        int index = 0;
        while (index < text.length()) {
            int beforeWhitespace = index;
            index = skipWhitespace(text, index);
            if (index > beforeWhitespace && !expectingSimpleSelector && pendingCombinator == null) {
                pendingCombinator = Combinator.DESCENDANT;
                expectingSimpleSelector = true;
            }
            if (index >= text.length()) {
                break;
            }

            char ch = text.charAt(index);
            if (ch == '>') {
                if (expectingSimpleSelector && parsedSteps.isEmpty()) {
                    throw new IllegalArgumentException("Selector cannot start with child combinator: " + selectorText);
                }
                if (pendingCombinator == Combinator.CHILD) {
                    throw new IllegalArgumentException("Duplicate child combinator in selector: " + selectorText);
                }
                pendingCombinator = Combinator.CHILD;
                expectingSimpleSelector = true;
                index++;
                continue;
            }

            ParseResult result = parseSimpleSelector(text, index, selectorText);
            Combinator combinator = parsedSteps.isEmpty() ? null
                    : (pendingCombinator == null ? Combinator.DESCENDANT : pendingCombinator);
            parsedSteps.add(new SelectorStep(combinator, result.selector));
            pendingCombinator = null;
            expectingSimpleSelector = false;
            index = result.nextIndex;
        }

        if (parsedSteps.isEmpty()) {
            throw new IllegalArgumentException("Invalid selector: " + selectorText);
        }
        if (expectingSimpleSelector) {
            throw new IllegalArgumentException("Selector cannot end with a combinator: " + selectorText);
        }
        return new UiSelector(parsedSteps);
    }

    /**
     * 基于当前选择器创建带伪类条件的新选择器。
     *
     * @param pseudoClass 伪类条件
     * @return 带伪类的新选择器
     */
    public UiSelector withPseudoClass(UiPseudoClass pseudoClass) {
        Objects.requireNonNull(pseudoClass, "pseudoClass");
        List<SelectorStep> nextSteps = new ArrayList<SelectorStep>(steps.size());
        for (int i = 0; i < steps.size(); i++) {
            SelectorStep step = steps.get(i);
            if (i == steps.size() - 1) {
                nextSteps.add(new SelectorStep(step.combinator,
                        step.selector.withPseudoCondition(PseudoCondition.stateOrStructural(pseudoClass, null))));
            } else {
                nextSteps.add(step);
            }
        }
        return new UiSelector(nextSteps);
    }

    /**
     * 判断选择器是否匹配指定元素（不考虑交互伪类状态）。
     *
     * <p>含交互状态伪类的选择器在无状态信息时不匹配；结构伪类仍会正常匹配。</p>
     *
     * @param element 目标元素
     * @return 是否匹配
     */
    public boolean matches(ElementNode element) {
        return matches(element, null);
    }

    /**
     * 判断选择器是否匹配指定元素（考虑目标元素伪类状态）。
     *
     * @param element 目标元素
     * @param activeStates 元素当前激活的伪类状态集合；为 null 时等同于无状态
     * @return 是否匹配
     */
    public boolean matches(ElementNode element, Set<UiPseudoClass> activeStates) {
        if (element == null) {
            return false;
        }
        return matchesStep(element, steps.size() - 1, activeStates);
    }

    private boolean matchesStep(ElementNode element, int stepIndex, Set<UiPseudoClass> activeStates) {
        SelectorStep step = steps.get(stepIndex);
        Set<UiPseudoClass> stepStates = stepIndex == steps.size() - 1 ? activeStates : null;
        if (!step.selector.matches(element, stepStates)) {
            return false;
        }
        if (stepIndex == 0) {
            return true;
        }
        if (step.combinator == Combinator.CHILD) {
            DocumentNode parent = element.getParent();
            return parent instanceof ElementNode && matchesStep((ElementNode) parent, stepIndex - 1, activeStates);
        }
        for (DocumentNode current = element.getParent(); current != null; current = current.getParent()) {
            if (current instanceof ElementNode && matchesStep((ElementNode) current, stepIndex - 1, activeStates)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回特异性的 ID 分量。
     *
     * @return ID 特异性分量
     */
    public int getSpecificityId() {
        return specificityId;
    }

    /**
     * 返回特异性的 class 分量。
     *
     * @return class 特异性分量
     */
    public int getSpecificityClass() {
        return specificityClass;
    }

    /**
     * 返回特异性的 tag 分量。
     *
     * @return tag 特异性分量
     */
    public int getSpecificityTag() {
        return specificityTag;
    }

    /**
     * 比较两个选择器的特异性。
     *
     * <p>返回值含义：正数表示 this 特异性更高，负数表示 other 更高，0 表示相同。</p>
     *
     * @param other 另一个选择器
     * @return 特异性比较结果
     */
    public int compareSpecificity(UiSelector other) {
        int cmp = Integer.compare(this.specificityId, other.specificityId);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(this.specificityClass, other.specificityClass);
        if (cmp != 0) return cmp;
        return Integer.compare(this.specificityTag, other.specificityTag);
    }

    /**
     * 返回最右侧简单选择器的标签名条件。
     *
     * @return 标签名；无标签条件时返回 null
     */
    public String getTagName() {
        return rightMostSelector.tagName;
    }

    /**
     * 返回最右侧简单选择器的类名条件列表。
     *
     * @return 类名列表（只读）
     */
    public List<String> getClassNames() {
        return rightMostSelector.classNames;
    }

    /**
     * 返回最右侧简单选择器的 ID 条件。
     *
     * @return ID 值；无 ID 条件时返回 null
     */
    public String getId() {
        return rightMostSelector.id;
    }

    /**
     * 判断最右侧简单选择器是否为纯通配符选择器。
     *
     * @return 是否为通配符
     */
    public boolean isUniversal() {
        return rightMostSelector.universal && rightMostSelector.tagName == null && rightMostSelector.id == null
                && rightMostSelector.classNames.isEmpty() && rightMostSelector.pseudoConditions.isEmpty();
    }

    /**
     * 返回最右侧简单选择器的第一个伪类条件。
     *
     * @return 伪类；无伪类条件时返回 null
     */
    public UiPseudoClass getPseudoClass() {
        return rightMostSelector.pseudoConditions.isEmpty() ? null
                : rightMostSelector.pseudoConditions.get(0).pseudoClass;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            SelectorStep step = steps.get(i);
            if (i > 0) {
                sb.append(step.combinator == Combinator.CHILD ? " > " : " ");
            }
            sb.append(step.selector.toSelectorText());
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof UiSelector)) return false;
        UiSelector other = (UiSelector) obj;
        return steps.equals(other.steps);
    }

    @Override
    public int hashCode() {
        return steps.hashCode();
    }

    private static List<SelectorStep> singleStep(SimpleSelector selector) {
        List<SelectorStep> list = new ArrayList<SelectorStep>(1);
        list.add(new SelectorStep(null, selector));
        return list;
    }

    private static ParseResult parseSimpleSelector(String text, int start, String selectorText) {
        String parsedTag = null;
        String parsedId = null;
        boolean parsedUniversal = false;
        List<String> parsedClasses = new ArrayList<String>();
        List<PseudoCondition> parsedPseudos = new ArrayList<PseudoCondition>();

        int index = start;
        if (index < text.length() && text.charAt(index) == '*') {
            parsedUniversal = true;
            index++;
        } else if (index < text.length() && isNameStart(text.charAt(index))) {
            int tagStart = index;
            index = readName(text, index);
            parsedTag = text.substring(tagStart, index).toLowerCase(java.util.Locale.ROOT);
        }

        while (index < text.length()) {
            char ch = text.charAt(index);
            if (Character.isWhitespace(ch) || ch == '>') {
                break;
            }
            if (ch == '.') {
                index++;
                int nameStart = index;
                index = readName(text, index);
                if (nameStart == index) {
                    throw new IllegalArgumentException("Empty class name in selector: " + selectorText);
                }
                parsedClasses.add(text.substring(nameStart, index));
            } else if (ch == '#') {
                index++;
                int nameStart = index;
                index = readName(text, index);
                if (nameStart == index) {
                    throw new IllegalArgumentException("Empty id in selector: " + selectorText);
                }
                if (parsedId != null) {
                    throw new IllegalArgumentException("Multiple IDs in selector: " + selectorText);
                }
                parsedId = text.substring(nameStart, index);
            } else if (ch == ':') {
                index++;
                int nameStart = index;
                index = readName(text, index);
                if (nameStart == index) {
                    throw new IllegalArgumentException("Empty pseudo-class in selector: " + selectorText);
                }
                String pseudoName = text.substring(nameStart, index).toLowerCase(java.util.Locale.ROOT);
                String pseudoArgument = null;
                if (index < text.length() && text.charAt(index) == '(') {
                    int argumentStart = ++index;
                    while (index < text.length() && text.charAt(index) != ')') {
                        index++;
                    }
                    if (index >= text.length()) {
                        throw new IllegalArgumentException("Unclosed pseudo-class argument in selector: " + selectorText);
                    }
                    pseudoArgument = text.substring(argumentStart, index);
                    index++;
                }
                parsedPseudos.add(parsePseudoCondition(pseudoName, pseudoArgument, selectorText));
            } else {
                throw new IllegalArgumentException("Unexpected character '" + ch + "' in selector: " + selectorText);
            }
        }

        if (index == start) {
            throw new IllegalArgumentException("Invalid selector: " + selectorText);
        }
        return new ParseResult(new SimpleSelector(parsedTag, parsedClasses.isEmpty() ? null : parsedClasses,
                parsedId, parsedUniversal, parsedPseudos.isEmpty() ? null : parsedPseudos), index);
    }

    private static PseudoCondition parsePseudoCondition(String name, String argument, String selectorText) {
        if (argument != null && !"nth-child".equals(name)) {
            throw new IllegalArgumentException("Pseudo-class ':" + name + "' does not accept arguments: " + selectorText);
        }
        switch (name) {
            case "hover": return PseudoCondition.stateOrStructural(UiPseudoClass.HOVER, null);
            case "focus": return PseudoCondition.stateOrStructural(UiPseudoClass.FOCUS, null);
            case "focus-visible": return PseudoCondition.stateOrStructural(UiPseudoClass.FOCUS_VISIBLE, null);
            case "active": return PseudoCondition.stateOrStructural(UiPseudoClass.ACTIVE, null);
            case "disabled": return PseudoCondition.stateOrStructural(UiPseudoClass.DISABLED, null);
            case "first-child": return PseudoCondition.stateOrStructural(UiPseudoClass.FIRST_CHILD, null);
            case "last-child": return PseudoCondition.stateOrStructural(UiPseudoClass.LAST_CHILD, null);
            case "nth-child":
                if (argument == null || argument.trim().isEmpty()) {
                    throw new IllegalArgumentException("Missing nth-child argument in selector: " + selectorText);
                }
                return PseudoCondition.stateOrStructural(UiPseudoClass.NTH_CHILD,
                        NthExpression.parse(argument, selectorText));
            default:
                throw new IllegalArgumentException("Unknown pseudo-class ':" + name + "' in selector: " + selectorText);
        }
    }

    private static int skipWhitespace(String text, int index) {
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int readName(String text, int index) {
        while (index < text.length()) {
            char ch = text.charAt(index);
            if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_') {
                index++;
            } else {
                break;
            }
        }
        return index;
    }

    private static boolean isNameStart(char ch) {
        return Character.isLetter(ch) || ch == '_' || ch == '-';
    }

    private enum Combinator {
        DESCENDANT,
        CHILD
    }

    private static final class SelectorStep {
        private final Combinator combinator;
        private final SimpleSelector selector;

        private SelectorStep(Combinator combinator, SimpleSelector selector) {
            this.combinator = combinator;
            this.selector = Objects.requireNonNull(selector, "selector");
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof SelectorStep)) return false;
            SelectorStep other = (SelectorStep) obj;
            return combinator == other.combinator && selector.equals(other.selector);
        }

        @Override
        public int hashCode() {
            return 31 * (combinator == null ? 0 : combinator.hashCode()) + selector.hashCode();
        }
    }

    private static final class SimpleSelector {
        private final String tagName;
        private final List<String> classNames;
        private final String id;
        private final boolean universal;
        private final List<PseudoCondition> pseudoConditions;
        private final int specificityId;
        private final int specificityClass;
        private final int specificityTag;

        private SimpleSelector(String tagName, List<String> classNames, String id, boolean universal,
                List<PseudoCondition> pseudoConditions) {
            this.tagName = tagName;
            this.classNames = classNames == null ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(classNames));
            this.id = id;
            this.universal = universal;
            this.pseudoConditions = pseudoConditions == null ? Collections.<PseudoCondition>emptyList()
                    : Collections.unmodifiableList(new ArrayList<PseudoCondition>(pseudoConditions));
            this.specificityId = id != null ? 1 : 0;
            this.specificityClass = this.classNames.size() + this.pseudoConditions.size();
            this.specificityTag = tagName != null && !universal ? 1 : 0;
        }

        private SimpleSelector withPseudoCondition(PseudoCondition pseudoCondition) {
            List<PseudoCondition> nextPseudos = new ArrayList<PseudoCondition>(pseudoConditions);
            nextPseudos.add(pseudoCondition);
            return new SimpleSelector(tagName, classNames.isEmpty() ? null : classNames, id, universal, nextPseudos);
        }

        private boolean matches(ElementNode element, Set<UiPseudoClass> activeStates) {
            if (tagName != null && !tagName.equals(element.getTagName())) {
                return false;
            }
            if (id != null && !id.equals(element.getId())) {
                return false;
            }
            for (String className : classNames) {
                if (!element.getClassList().contains(className)) {
                    return false;
                }
            }
            for (PseudoCondition condition : pseudoConditions) {
                if (!condition.matches(element, activeStates)) {
                    return false;
                }
            }
            return universal || tagName != null || id != null || !classNames.isEmpty() || !pseudoConditions.isEmpty();
        }

        private String toSelectorText() {
            if (universal && tagName == null && id == null && classNames.isEmpty() && pseudoConditions.isEmpty()) {
                return "*";
            }
            StringBuilder sb = new StringBuilder();
            if (tagName != null) {
                sb.append(tagName);
            } else if (universal) {
                sb.append('*');
            }
            if (id != null) {
                sb.append('#').append(id);
            }
            for (String className : classNames) {
                sb.append('.').append(className);
            }
            for (PseudoCondition condition : pseudoConditions) {
                sb.append(condition.toSelectorText());
            }
            return sb.toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof SimpleSelector)) return false;
            SimpleSelector other = (SimpleSelector) obj;
            return universal == other.universal
                    && Objects.equals(tagName, other.tagName)
                    && Objects.equals(id, other.id)
                    && classNames.equals(other.classNames)
                    && pseudoConditions.equals(other.pseudoConditions);
        }

        @Override
        public int hashCode() {
            int result = tagName == null ? 0 : tagName.hashCode();
            result = 31 * result + classNames.hashCode();
            result = 31 * result + (id == null ? 0 : id.hashCode());
            result = 31 * result + (universal ? 1 : 0);
            result = 31 * result + pseudoConditions.hashCode();
            return result;
        }
    }

    private static final class PseudoCondition {
        private final UiPseudoClass pseudoClass;
        private final NthExpression nthExpression;

        private PseudoCondition(UiPseudoClass pseudoClass, NthExpression nthExpression) {
            this.pseudoClass = Objects.requireNonNull(pseudoClass, "pseudoClass");
            this.nthExpression = nthExpression;
        }

        private static PseudoCondition stateOrStructural(UiPseudoClass pseudoClass, NthExpression nthExpression) {
            return new PseudoCondition(pseudoClass, nthExpression);
        }

        private boolean matches(ElementNode element, Set<UiPseudoClass> activeStates) {
            switch (pseudoClass) {
                case FIRST_CHILD: return childIndex(element) == 1;
                case LAST_CHILD: return lastElementChild(element);
                case NTH_CHILD:
                    int index = childIndex(element);
                    return index > 0 && nthExpression != null && nthExpression.matches(index);
                default:
                    return activeStates != null && activeStates.contains(pseudoClass);
            }
        }

        private String toSelectorText() {
            switch (pseudoClass) {
                case HOVER: return ":hover";
                case FOCUS: return ":focus";
                case FOCUS_VISIBLE: return ":focus-visible";
                case ACTIVE: return ":active";
                case DISABLED: return ":disabled";
                case FIRST_CHILD: return ":first-child";
                case LAST_CHILD: return ":last-child";
                case NTH_CHILD: return ":nth-child(" + nthExpression + ")";
                default: return ":" + pseudoClass.name().toLowerCase(java.util.Locale.ROOT);
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof PseudoCondition)) return false;
            PseudoCondition other = (PseudoCondition) obj;
            return pseudoClass == other.pseudoClass && Objects.equals(nthExpression, other.nthExpression);
        }

        @Override
        public int hashCode() {
            return 31 * pseudoClass.hashCode() + (nthExpression == null ? 0 : nthExpression.hashCode());
        }
    }

    private static final class NthExpression {
        private final int a;
        private final int b;
        private final String sourceText;

        private NthExpression(int a, int b, String sourceText) {
            this.a = a;
            this.b = b;
            this.sourceText = sourceText;
        }

        private static NthExpression parse(String expression, String selectorText) {
            String normalized = expression.replace(" ", "").replace("\t", "")
                    .toLowerCase(java.util.Locale.ROOT);
            if ("odd".equals(normalized)) {
                return new NthExpression(2, 1, "odd");
            }
            if ("even".equals(normalized)) {
                return new NthExpression(2, 0, "even");
            }
            int nIndex = normalized.indexOf('n');
            try {
                if (nIndex < 0) {
                    int value = Integer.parseInt(normalized);
                    return new NthExpression(0, value, normalized);
                }
                if (normalized.indexOf('n', nIndex + 1) >= 0) {
                    throw new NumberFormatException("multiple n");
                }
                String aPart = normalized.substring(0, nIndex);
                String bPart = normalized.substring(nIndex + 1);
                int a = parseNthCoefficient(aPart);
                int b = bPart.isEmpty() ? 0 : Integer.parseInt(bPart);
                return new NthExpression(a, b, normalized);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid nth-child expression '" + expression
                        + "' in selector: " + selectorText, ex);
            }
        }

        private boolean matches(int index) {
            if (a == 0) {
                return index == b;
            }
            int delta = index - b;
            if (a > 0 && delta < 0) {
                return false;
            }
            if (a < 0 && delta > 0) {
                return false;
            }
            return delta % a == 0;
        }

        @Override
        public String toString() {
            return sourceText;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof NthExpression)) return false;
            NthExpression other = (NthExpression) obj;
            return a == other.a && b == other.b;
        }

        @Override
        public int hashCode() {
            return 31 * a + b;
        }
    }

    private static int parseNthCoefficient(String value) {
        if (value.isEmpty() || "+".equals(value)) {
            return 1;
        }
        if ("-".equals(value)) {
            return -1;
        }
        return Integer.parseInt(value);
    }

    private static int childIndex(ElementNode element) {
        DocumentNode parent = element.getParent();
        if (parent == null) {
            return -1;
        }
        int index = 0;
        for (DocumentNode child : parent.getChildren()) {
            if (child instanceof ElementNode) {
                index++;
                if (child == element) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static boolean lastElementChild(ElementNode element) {
        DocumentNode parent = element.getParent();
        if (parent == null) {
            return false;
        }
        ElementNode lastElement = null;
        for (DocumentNode child : parent.getChildren()) {
            if (child instanceof ElementNode) {
                lastElement = (ElementNode) child;
            }
        }
        return lastElement == element;
    }

    private static final class ParseResult {
        private final SimpleSelector selector;
        private final int nextIndex;

        private ParseResult(SimpleSelector selector, int nextIndex) {
            this.selector = selector;
            this.nextIndex = nextIndex;
        }
    }
}
