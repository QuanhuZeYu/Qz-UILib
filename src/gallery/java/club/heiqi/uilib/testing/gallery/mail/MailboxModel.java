package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import java.util.Objects;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.LinkedHashSet;

/** 本地收件箱业务；打开、勾选和消息事实分别拥有权威，不从控件位置推断身份。 */
public final class MailboxModel implements AutoCloseable {
    public enum Folder { INBOX, STARRED, ALL }
    public static final class Message {
        public final String id, sender, subject, preview, body, time;
        public final boolean starred, read, inbox;
        public Message(String id, String sender, String subject, String preview, String body, String time,
                       boolean starred, boolean read, boolean inbox) {
            this.id = Objects.requireNonNull(id, "消息id");
            if (id.isEmpty()) throw new IllegalArgumentException("消息id不能为空");
            this.sender = Objects.requireNonNull(sender); this.subject = Objects.requireNonNull(subject);
            this.preview = Objects.requireNonNull(preview); this.body = Objects.requireNonNull(body);
            this.time = Objects.requireNonNull(time); this.starred = starred; this.read = read; this.inbox = inbox;
        }
        public Message flags(boolean star, boolean seen, boolean inInbox) {
            return new Message(id, sender, subject, preview, body, time, star, seen, inInbox);
        }
    }
    public static final class Item {
        public final String id;
        public final Signal<Message> value;
        private Item(ReactiveDomain domain, Message message) { id = message.id; value = domain.signal(message); }
    }
    private final ReactiveDomain domain;
    private final Owner owner;
    private final Map<String, Item> byId = new LinkedHashMap<>();
    private final Signal<List<Item>> source;
    public final Signal<String> query, openedId, feedback;
    public final Signal<Boolean> reading;
    public final Signal<Folder> folder;
    public final Signal<Set<String>> checked;
    public final ReadableSignal<List<Item>> results;
    public final ReadableSignal<Message> opened;
    public final Signal<Integer> pageSize;
    private final Signal<Integer> requestedPage;
    public final ReadableSignal<Integer> pageIndex;
    public final ReadableSignal<List<Item>> pageItems;
    public MailboxModel(ReactiveDomain domain, List<Message> initial) {
        this.domain = domain; domain.checkWrite(); owner = domain.owner();
        source = domain.signal(Collections.emptyList()); query = domain.signal("");
        pageSize = domain.signal(25); requestedPage = domain.signal(0);
        openedId = domain.signal(null); reading = domain.signal(false); feedback = domain.signal("");
        folder = domain.signal(Folder.INBOX); checked = domain.signal(Collections.emptySet());
        for (Message message : initial) {
            if (byId.put(message.id, new Item(domain, message)) != null)
                throw new IllegalArgumentException("消息id重复: " + message.id);
        }
        publish();
        results = domain.computed(owner, () -> {
            String q = query.get().trim().toLowerCase(Locale.ROOT); Folder f = folder.get();
            List<Item> list = new ArrayList<>();
            for (Item item : source.get()) {
                Message m = item.value.get();
                if (f == Folder.INBOX && !m.inbox || f == Folder.STARRED && !m.starred) continue;
                if (q.isEmpty() || (m.sender + " " + m.subject + " " + m.preview).toLowerCase(Locale.ROOT).contains(q)) list.add(item);
            }
            return Collections.unmodifiableList(list);
        });
        pageIndex = domain.computed(owner, () -> {
            int size=pageSize.get();if(size<=0)throw new IllegalArgumentException("每页数量必须为正");
            return Math.max(0,Math.min(requestedPage.get(),Math.max(0,(results.get().size()-1)/size)));
        });
        pageItems = domain.computed(owner, () -> {
            List<Item> all=results.get();int start=pageIndex.get()*pageSize.get();
            return Collections.unmodifiableList(new ArrayList<>(all.subList(start,Math.min(all.size(),start+pageSize.get()))));
        });
        opened = domain.computed(owner, () -> {
            source.get(); Item item = byId.get(openedId.get()); return item == null ? null : item.value.get();
        });
    }
    /** 分类计数独立于当前查询；声明绑定追踪同一消息来源，不另存一份邮箱快照。 */
    public int count(Folder selected) {
        int total = 0;
        for (Item item : source.get()) {
            Message m = item.value.get();
            if (selected == Folder.ALL || selected == Folder.INBOX && m.inbox || selected == Folder.STARRED && m.starred) total++;
        }
        return total;
    }
    private void publish() { source.set(Collections.unmodifiableList(new ArrayList<>(byId.values()))); }
    private Item require(String id) {
        domain.checkWrite(); Item value = byId.get(id);
        if (value == null) throw new IllegalArgumentException("消息不存在: " + id); return value;
    }
    public void setQuery(String value) {
        if (!query.get().equals(value)) { query.set(Objects.requireNonNull(value)); checked.set(Collections.emptySet()); requestedPage.set(0); }
    }
    public void setFolder(Folder value) {
        if (folder.get() != value) { folder.set(Objects.requireNonNull(value)); checked.set(Collections.emptySet()); requestedPage.set(0); }
        reading.set(false);
    }
    public void open(String id) {
        Item item = require(id); Message m = item.value.get(); item.value.set(m.flags(m.starred, true, m.inbox));
        openedId.set(id); reading.set(true);
    }
    public void back() { reading.set(false); }
    public void toggleStar(String id) {
        Item item = require(id); Message m = item.value.get(); item.value.set(m.flags(!m.starred, m.read, m.inbox));
        retainVisibleSelection();
    }
    public void setRead(String id, boolean read) {
        Item item = require(id); Message m = item.value.get(); item.value.set(m.flags(m.starred, read, m.inbox));
    }
    public void toggleChecked(String id) {
        require(id); if (results.get().stream().noneMatch(i -> i.id.equals(id))) return;
        Set<String> next = new LinkedHashSet<>(checked.get()); if (!next.remove(id)) next.add(id);
        checked.set(Collections.unmodifiableSet(next));
    }
    public void setAllChecked(boolean value) {
        Set<String> ids = new LinkedHashSet<>(); if (value) for (Item item : results.get()) ids.add(item.id);
        checked.set(Collections.unmodifiableSet(ids));
    }
    private void retainVisibleSelection() {
        Set<String> ids = new LinkedHashSet<>(); for (Item item : results.get()) if (checked.get().contains(item.id)) ids.add(item.id);
        checked.set(Collections.unmodifiableSet(ids));
        normalizePage();
    }
    private void normalizePage() {
        // 结果缩短时结束旧的越界翻页意图，后来的新消息不能使它复活。
        requestedPage.set(pageIndex.get());
    }
    /** 阅读动作仅归档当前稳定id，不借批量选择暂存或改动其他勾选。 */
    public void archive(String id) {
        Item item=require(id);Message m=item.value.get();item.value.set(m.flags(m.starred,m.read,false));
        retainVisibleSelection();feedback.set("已归档邮件，可在所有邮件中查看");
    }
    public void archiveChecked() {
        Set<String> ids = new LinkedHashSet<>(checked.get());
        for (Item item : new ArrayList<>(results.get())) if (ids.contains(item.id)) {
            Message m = item.value.get(); item.value.set(m.flags(m.starred, m.read, false));
        }
        checked.set(Collections.emptySet()); normalizePage(); if (!ids.isEmpty()) feedback.set("已归档 " + ids.size() + " 封邮件，可在所有邮件中查看");
    }
    public void readChecked(boolean value) {
        for (Item item : new ArrayList<>(results.get())) if (checked.get().contains(item.id)) setRead(item.id, value);
        checked.set(Collections.emptySet());
    }
    public void receive(Message message) {
        domain.checkWrite(); if (byId.containsKey(message.id)) throw new IllegalArgumentException("消息id重复: " + message.id);
        byId.put(message.id, new Item(domain, message)); publish();
    }
    public void update(Message message) { require(message.id).value.set(message); retainVisibleSelection(); }
    public void remove(String id) { require(id); byId.remove(id); publish(); retainVisibleSelection(); }
    public void movePage(int delta) {
        domain.checkWrite();requestedPage.set(Math.max(0,pageIndex.get()+delta));normalizePage();
    }
    public void setPageSize(int value) {
        domain.checkWrite();if(value<=0)throw new IllegalArgumentException("每页数量必须为正");
        pageSize.set(value);requestedPage.set(0);
    }
    public void reverse() {
        domain.checkWrite(); List<Item> next = new ArrayList<>(source.get()); Collections.reverse(next);
        byId.clear(); for (Item item : next) byId.put(item.id,item); publish();
    }
    @Override public void close() { owner.dispose(); }
}
