package club.heiqi.uilib.ui.container.experimental.minecraft;

import java.util.Objects;

import club.heiqi.uilib.ui.container.experimental.model.EntryKey;

/** 同步输入裁决结果；只有 `LONG_ENTRY` owner 可以携带 Entry key。 */
public final class ContainerInputClaim {
    private static final ContainerInputClaim NONE = new ContainerInputClaim(ContainerInputOwner.NONE, null);
    private static final ContainerInputClaim VANILLA =
            new ContainerInputClaim(ContainerInputOwner.VANILLA_SLOT, null);

    private final ContainerInputOwner owner;
    private final EntryKey entryKey;

    /** 创建并校验 owner/key 组合。 */
    public ContainerInputClaim(ContainerInputOwner owner, EntryKey entryKey) {
        this.owner = Objects.requireNonNull(owner, "owner");
        if ((owner == ContainerInputOwner.LONG_ENTRY) != (entryKey != null)) {
            throw new IllegalArgumentException("only LONG_ENTRY claims carry an entry key");
        }
        this.entryKey = entryKey;
    }

    /** 返回无 owner claim。 */
    public static ContainerInputClaim none() { return NONE; }

    /** 返回 vanilla owner claim。 */
    public static ContainerInputClaim vanilla() { return VANILLA; }

    /** 返回 Entry owner claim。 */
    public static ContainerInputClaim longEntry(EntryKey key) {
        return new ContainerInputClaim(ContainerInputOwner.LONG_ENTRY, Objects.requireNonNull(key, "key"));
    }

    /** 返回输入 owner。 */
    public ContainerInputOwner owner() { return owner; }

    /** 返回 Entry key；非 Entry claim 返回 null。 */
    public EntryKey entryKey() { return entryKey; }

    /** scene owner 会消费 vanilla activation。 */
    public boolean consumesVanilla() {
        return owner == ContainerInputOwner.SCENE_OVERLAY
                || owner == ContainerInputOwner.SCENE_FOCUSED
                || owner == ContainerInputOwner.LONG_ENTRY;
    }
}
