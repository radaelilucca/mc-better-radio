package com.radaeli.betterradio.neoforge.client;

import com.radaeli.betterradio.music.MusicFrequency;
import com.radaeli.betterradio.music.MusicTrack;
import com.radaeli.betterradio.music.PlayerSession;
import com.radaeli.betterradio.music.StartupMode;
import com.radaeli.ruilib.ui.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Personal player and settings, built from RUI components with one catalog overlay. */
final class PlayerScreen extends UiScreen {
    private static final long CATALOG_SEARCH_DEBOUNCE_MS = 200;
    private static final String PLAYLIST_MODE_ICON = "≡";
    private static final String FREE_FLOW_MODE_ICON = "∞";
    private final Tabs tabs = new Tabs();
    private final Page player = new Page();
    private final Page history = new Page();
    private final Page playlist = new Page();
    private final Page settings = new Page();
    private Select<MusicFrequency> musicFrequencyInput;
    private Select<MusicFrequency> playlistFrequencyInput;
    private Slider biomeBiasInput;
    private Select<StartupMode> startupModeInput;
    private final Page catalogPage = new Page(false);
    private final SearchField catalogSearch = new SearchField(text("search_music"));
    private PlayerSession.Snapshot lastState;
    private List<MusicTrack> lastCatalog = List.of();
    private long lastRevision = -1;
    private String lastIdleStateKey = "";
    private PlaybackProgressView playbackProgress;
    private String pendingCatalogSearch = "";
    private String appliedCatalogSearch = "";
    private long catalogSearchChangedAt;
    private final Map<String, Boolean> historySaveEnabled = new HashMap<>();
    private boolean historySaveCacheInitialized;

    PlayerScreen() { this(new Column(6)); }

    private PlayerScreen(Column content) {
        super(Component.literal("Better Radio"), new Panel(content), MusicClientNeoForge.playerTheme());
        catalogSearch.setResponder(query -> {
            pendingCatalogSearch = query;
            catalogSearchChangedAt = net.minecraft.Util.getMillis();
        });
        Row title = new Row(4, Alignment.START, Alignment.CENTER);
        title.add(new Label(Component.literal("Better Radio")));
        title.add(Spacer.flex());
        title.add(icon("x", "close", this::onClose));
        content.add(title);
        tabs.addTab(text("player"), player).addTab(text("history"), history).addTab(text("playlist"), playlist)
                .addTab(text("settings"), settings);
        tabs.setOnSelectionChanged(index -> updateVisibleProgress());
        content.add(tabs);
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("better_radio.player." + key, arguments);
    }

    private static IconButton icon(String glyph, String name, Runnable action) {
        IconButton button = new IconButton(text(name), Component.literal(glyph), action);
        button.setPreferredSize(26, 26);
        button.tooltip(text(name));
        return button;
    }

    @Override
    protected void init() {
        super.init();
        if (!historySaveCacheInitialized) {
            refreshHistorySaveCache();
            historySaveCacheInitialized = true;
        }
        refresh(true);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        super.tick();
        if (!MusicClientNeoForge.inWorld()) {
            onClose();
            return;
        }
        refresh(false);
        refreshCatalogSearch();
        updateVisibleProgress();
    }

    private void updateVisibleProgress() {
        if (tabs.selectedIndex() == 0 && modal() == null && playbackProgress != null) playbackProgress.update();
    }

    @Override
    public UiTheme theme() { return MusicClientNeoForge.playerTheme(); }

    @Override
    protected void layoutRoot(UiTheme theme) {
        int panelWidth = Math.max(1, Math.min(460, width - 16));
        int panelHeight = Math.max(1, Math.min(342, height - 16));
        Insets padding = theme.tokens().panelPadding();
        tabs.setPreferredSize(null, Math.max(0, panelHeight - padding.top() - padding.bottom() - 32));
        root().layout(font, theme, new Bounds((width - panelWidth) / 2, (height - panelHeight) / 2,
                panelWidth, panelHeight));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (modal() == null && keyCode != GLFW.GLFW_KEY_ESCAPE
                && MusicClientNeoForge.handleScreenKey(keyCode, scanCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void refresh(boolean force) {
        if (!MusicClientNeoForge.inWorld()) return;
        PlayerSession.Snapshot state = MusicClientNeoForge.snapshot();
        List<MusicTrack> catalog = MusicClientNeoForge.catalog();
        long revision = MusicClientNeoForge.revision();
        String idleStateKey = MusicClientNeoForge.idleStateKey();
        if (!force && revision == lastRevision && state.equals(lastState) && catalog.equals(lastCatalog)
                && idleStateKey.equals(lastIdleStateKey)) return;
        lastState = state;
        lastCatalog = List.copyOf(catalog);
        lastRevision = revision;
        lastIdleStateKey = idleStateKey;
        buildPlayer(state);
        buildHistory(state, catalog);
        buildPlaylist(state, catalog);
        buildSettings(state);
        if (modal() != null) buildCatalog(state, catalog);
        layoutRoot(theme());
        if (modal() != null) modal().layoutInScreen(font, theme(), width, height);
        player.restoreScroll();
        history.restoreScroll();
        playlist.restoreScroll();
        if (modal() != null) catalogPage.restoreScroll();
        // Seed a newly mounted counter from the existing duration cache before the first render.
        updateVisibleProgress();
    }

    private void buildPlayer(PlayerSession.Snapshot state) {
        player.clear();
        boolean playlistMode = state.mode() == PlayerSession.Mode.PLAYLIST;
        String currentModeIcon = playlistMode ? PLAYLIST_MODE_ICON : FREE_FLOW_MODE_ICON;
        String currentModeName = playlistMode ? "mode_playlist" : "mode_free_flow";
        CenteredGroup mode = new CenteredGroup(0);
        mode.add(new FittingLabel(Component.literal(currentModeIcon + " ").append(text(currentModeName)),
                false, false));
        player.header.add(mode);

        CenteredGroup controls = new CenteredGroup(8);
        IconButton switchMode = controls.add(icon(currentModeIcon, currentModeName,
                playlistMode ? MusicClientNeoForge::freeFlow : MusicClientNeoForge::activatePlaylist));
        switchMode.setEnabled(playlistMode || state.playlist().stream().anyMatch(lastCatalog::contains));
        switchMode.tooltip(
                text(playlistMode ? "playlist_mode_title" : "free_flow_mode_title"),
                text(playlistMode ? "playlist_mode_help" : "free_flow_mode_help"),
                text(playlistMode ? "switch_to_free_flow" : "switch_to_playlist"));
        IconButton previous = controls.add(icon("|<", "previous", MusicClientNeoForge::playPrevious));
        previous.setEnabled(state.canPrevious());
        IconButton playPause = controls.add(icon(state.current().isPresent() && !state.paused() ? "||" : ">",
                state.current().isPresent() && !state.paused() ? "pause" : "play",
                MusicClientNeoForge::togglePause));
        playPause.setEnabled(state.current().isPresent() || !lastCatalog.isEmpty());
        IconButton next = controls.add(icon(">|", "next", MusicClientNeoForge::playNext));
        next.setEnabled(!lastCatalog.isEmpty());
        IconButton shuffle = controls.add(icon("⇄", "shuffle", () ->
                MusicClientNeoForge.setShuffle(!state.shuffle())));
        shuffle.tooltip(text("shuffle_help"));
        player.header.add(controls);

        Component current = state.current().map(track -> state.paused()
                ? text("paused_track", MusicClientNeoForge.trackName(track))
                : MusicClientNeoForge.trackName(track)).orElseGet(() -> text(lastIdleStateKey));
        CenteredGroup currentTrack = new CenteredGroup(0);
        currentTrack.add(new FittingLabel(current, false, false));
        player.header.add(currentTrack);
        playbackProgress = null;
        if (state.current().isPresent()) {
            playbackProgress = new PlaybackProgressView();
            CenteredGroup progress = new CenteredGroup(0);
            progress.add(playbackProgress);
            player.header.add(progress);
        }
        player.header.add(new Label(text("up_next")));
        for (PlayerSession.QueueEntry entry : state.upcoming()) {
            player.addListItem(new TrackRow(MusicClientNeoForge.trackName(entry.track()),
                    icon(">", "skip_to", () -> MusicClientNeoForge.skipTo(entry.key()))));
        }
        if (lastCatalog.isEmpty()) {
            player.addListItem(new FittingLabel(text("empty_catalog")));
        } else if (state.randomNext()) {
            player.addListItem(new FittingLabel(text("random_next"), true));
        } else if (state.upcoming().isEmpty()) {
            player.addListItem(new FittingLabel(text("empty_queue")));
        }
    }

    /** Keep inputs mounted across playback refreshes so dragging and open menus retain focus. */
    private void buildSettings(PlayerSession.Snapshot state) {
        if (musicFrequencyInput == null) {
            List<Select.Option<MusicFrequency>> frequencies = new ArrayList<>();
            for (MusicFrequency frequency : MusicFrequency.values()) {
                String key = "frequency." + frequency.name().toLowerCase(Locale.ROOT);
                frequencies.add(new Select.Option<>(frequency, text(key), Tooltip.of(text(key + "_help"))));
            }
            musicFrequencyInput = new Select<>(frequencies, state.musicFrequency(), MusicClientNeoForge::setMusicFrequency);
            playlistFrequencyInput = new Select<>(frequencies, state.playlistFrequency(), MusicClientNeoForge::setPlaylistFrequency);
            biomeBiasInput = new Slider(0, 100, state.biomeBiasPercent(), 5,
                    value -> MusicClientNeoForge.setBiomeBiasPercent((int) Math.round(value)))
                    .setValueFormatter(value -> Math.round(value) + "%");
            List<Select.Option<StartupMode>> startupModes = new ArrayList<>();
            for (StartupMode startupMode : StartupMode.values()) {
                String key = "startup." + startupMode.name().toLowerCase(Locale.ROOT);
                startupModes.add(new Select.Option<>(startupMode, text(key), Tooltip.of(text(key + "_help"))));
            }
            startupModeInput = new Select<>(startupModes, state.startupMode(), MusicClientNeoForge::setStartupMode);
            settings.clear();
            settings.addListItem(new SettingRow(text("free_flow_interval"), text("free_flow_interval_help"), musicFrequencyInput));
            settings.addListItem(new SettingRow(text("playlist_interval"), text("playlist_interval_help"), playlistFrequencyInput));
            settings.addListItem(new SettingRow(text("biome_bias"), text("biome_bias_help"), biomeBiasInput));
            settings.addListItem(new SettingRow(text("startup_mode"), text("startup_mode_help"), startupModeInput));
        }
        if (musicFrequencyInput.value() != state.musicFrequency()) musicFrequencyInput.setValue(state.musicFrequency());
        if (playlistFrequencyInput.value() != state.playlistFrequency()) playlistFrequencyInput.setValue(state.playlistFrequency());
        if (biomeBiasInput.value() != state.biomeBiasPercent()) biomeBiasInput.setValue(state.biomeBiasPercent());
        if (startupModeInput.value() != state.startupMode()) startupModeInput.setValue(state.startupMode());
    }

    private void buildHistory(PlayerSession.Snapshot state, List<MusicTrack> available) {
        history.clear();
        history.header.add(new Label(text("recently_played")));
        List<MusicTrack> recent = new ArrayList<>(state.history());
        Collections.reverse(recent);
        if (recent.isEmpty()) history.addListItem(new FittingLabel(text("empty_history")));
        for (int i = 0; i < recent.size(); i++) {
            MusicTrack track = recent.get(i);
            Component name = trackName(track, available);
            if (i == 0 && state.current().filter(track::equals).isPresent()) name = text("current", name);
            IconButton enqueue = icon("+", "enqueue", () -> MusicClientNeoForge.enqueue(track));
            IconButton play = icon(">", "play_now", () -> MusicClientNeoForge.playNow(track));
            boolean trackAvailable = available.contains(track);
            boolean canSave = historySaveEnabled.getOrDefault(track.id(), true);
            IconButton save = icon("↓", "save_to_playlist", () -> saveHistoryTrack(track));
            save.setEnabled(trackAvailable && canSave);
            if (!canSave) save.tooltip(text("already_added"));
            enqueue.setEnabled(trackAvailable);
            play.setEnabled(trackAvailable);
            history.addListItem(new TrackRow(name, enqueue, play, save));
        }
    }

    /** Rebuilt only when opening this screen or saving from History, never by tick/render/refresh. */
    private void refreshHistorySaveCache() {
        historySaveEnabled.clear();
        for (MusicTrack track : MusicClientNeoForge.snapshot().playlist()) {
            historySaveEnabled.put(track.id(), false);
        }
    }

    private void saveHistoryTrack(MusicTrack track) {
        MusicClientNeoForge.addToPlaylist(track);
        refreshHistorySaveCache();
        refresh(true);
    }

    private void buildPlaylist(PlayerSession.Snapshot state, List<MusicTrack> available) {
        playlist.clear();
        CenteredGroup actions = new CenteredGroup(6);
        actions.add(new Button(text("add_music"), this::openCatalog));
        playlist.header.add(actions);
        if (state.playlist().isEmpty()) playlist.addListItem(new FittingLabel(text("empty_playlist")));
        for (MusicTrack track : state.playlist()) {
            playlist.addListItem(new TrackRow(trackName(track, available),
                    icon("x", "remove", () -> MusicClientNeoForge.removeFromPlaylist(track))));
        }
    }

    private static Component trackName(MusicTrack track, List<MusicTrack> available) {
        Component name = MusicClientNeoForge.trackName(track);
        return available.contains(track) ? name : text("unavailable_track", name);
    }

    private void openCatalog() {
        catalogSearch.setValue("");
        pendingCatalogSearch = "";
        appliedCatalogSearch = "";
        catalogSearchChangedAt = 0;
        buildCatalog(MusicClientNeoForge.snapshot(), MusicClientNeoForge.catalog());
        showModal(new Modal(text("add_music"), catalogPage, 420, Math.min(310, height - 16))
                .setHeaderAction(icon("x", "done", this::closeModal)));
    }

    private void buildCatalog(PlayerSession.Snapshot state, List<MusicTrack> available) {
        catalogPage.clear();
        catalogPage.header.add(catalogSearch);
        String query = appliedCatalogSearch.strip().toLowerCase(Locale.ROOT);
        List<MusicTrack> sorted = new ArrayList<>(available.stream()
                .filter(track -> !state.playlist().contains(track))
                .filter(track -> query.isEmpty()
                        || MusicClientNeoForge.trackName(track).getString().toLowerCase(Locale.ROOT).contains(query))
                .toList());
        sorted.sort(Comparator.comparing(track -> MusicClientNeoForge.trackName(track).getString(),
                String.CASE_INSENSITIVE_ORDER));
        if (sorted.isEmpty()) {
            String emptyMessage = !query.isEmpty() ? "no_search_results"
                    : available.isEmpty() ? "empty_catalog" : "all_music_added";
            catalogPage.addListItem(new FittingLabel(text(emptyMessage)));
        }
        for (MusicTrack track : sorted) {
            IconButton add = icon("+", "add", () -> MusicClientNeoForge.addToPlaylist(track));
            catalogPage.addListItem(new TrackRow(MusicClientNeoForge.trackName(track), add));
        }
    }

    private void refreshCatalogSearch() {
        if (modal() == null || pendingCatalogSearch.equals(appliedCatalogSearch)
                || net.minecraft.Util.getMillis() - catalogSearchChangedAt < CATALOG_SEARCH_DEBOUNCE_MS) return;

        appliedCatalogSearch = pendingCatalogSearch;
        buildCatalog(MusicClientNeoForge.snapshot(), lastCatalog);
        modal().layoutInScreen(font, theme(), width, height);
        catalogPage.list.setScrollOffset(0);
    }

    void showSaveError() {
        toastHost().show(new Toast(MusicClientNeoForge.toastMessage(text("save_error")), Toast.Level.INFO, 80));
    }

    /** Reserves the header's natural height and gives all remaining room to the RUI scroll view. */
    private static final class Page extends UiContainer {
        private final Column header = add(new Column(4));
        private final ScrollView list = add(new ScrollView(2));
        private final boolean paddedTop;
        private int savedOffset;

        private Page() { this(true); }

        private Page(boolean paddedTop) { this.paddedTop = paddedTop; }

        void clear() {
            savedOffset = list.scrollOffset();
            for (UiComponent child : List.copyOf(header.children())) header.remove(child);
            for (UiComponent child : List.copyOf(list.children())) list.remove(child);
            list.add(Spacer.fixed(0, 6));
        }

        void restoreScroll() { list.setScrollOffset(savedOffset); }

        void addListItem(UiComponent component) { list.add(new PaddedListItem(component)); }

        @Override
        protected UiSize measureIntrinsic(Font font, UiTheme theme, int maxWidth, int maxHeight) {
            return new UiSize(maxWidth, maxHeight);
        }

        @Override
        protected void layoutChildren(Font font, UiTheme theme) {
            int topPadding = paddedTop ? theme.tokens().panelPadding().top() * 2 : 0;
            Insets padding = theme.tokens().panelPadding();
            int contentX = bounds().x() + padding.left();
            int contentWidth = Math.max(0, bounds().width() - padding.left() - padding.right());
            int contentY = bounds().y() + topPadding;
            int contentHeight = Math.max(0, bounds().height() - topPadding);
            int headHeight = Math.min(contentHeight,
                    header.measure(font, theme, contentWidth, contentHeight).height());
            header.layout(font, theme, new Bounds(contentX, contentY, contentWidth, headHeight));
            int listTop = topPadding + headHeight + 4;
            list.layout(font, theme, new Bounds(bounds().x(), bounds().y() + listTop, bounds().width(),
                    Math.max(0, bounds().height() - listTop)));
        }
    }

    /** Keeps the list frame full width while insetting each row from its border. */
    private static final class PaddedListItem extends UiContainer {
        private final UiComponent content;

        private PaddedListItem(UiComponent content) { this.content = add(content); }

        @Override
        protected UiSize measureIntrinsic(Font font, UiTheme theme, int maxWidth, int maxHeight) {
            Insets padding = theme.tokens().panelPadding();
            int contentWidth = Math.max(0, maxWidth - padding.left() - padding.right());
            return new UiSize(maxWidth, content.measure(font, theme, contentWidth, maxHeight).height());
        }

        @Override
        protected void layoutChildren(Font font, UiTheme theme) {
            Insets padding = theme.tokens().panelPadding();
            int contentWidth = Math.max(0, bounds().width() - padding.left() - padding.right());
            content.layout(font, theme, new Bounds(bounds().x() + padding.left(), bounds().y(),
                    contentWidth, bounds().height()));
        }
    }

    /** Centers a short group by its measured width rather than relying on available-space spacers. */
    private static final class CenteredGroup extends UiContainer {
        private final int gap;

        private CenteredGroup(int gap) { this.gap = gap; }

        @Override
        protected UiSize measureIntrinsic(Font font, UiTheme theme, int maxWidth, int maxHeight) {
            int groupWidth = 0;
            int groupHeight = 0;
            for (UiComponent child : children()) {
                UiSize size = child.measure(font, theme, maxWidth, maxHeight);
                groupWidth += size.width();
                groupHeight = Math.max(groupHeight, size.height());
            }
            if (children().size() > 1) groupWidth += gap * (children().size() - 1);
            return new UiSize(groupWidth, groupHeight).constrain(maxWidth, maxHeight);
        }

        @Override
        protected void layoutChildren(Font font, UiTheme theme) {
            List<UiSize> sizes = children().stream()
                    .map(child -> child.measure(font, theme, bounds().width(), bounds().height())).toList();
            int groupWidth = sizes.stream().mapToInt(UiSize::width).sum()
                    + gap * Math.max(0, sizes.size() - 1);
            int x = bounds().x() + Math.max(0, (bounds().width() - groupWidth) / 2);
            for (int i = 0; i < sizes.size(); i++) {
                UiSize size = sizes.get(i);
                int y = bounds().y() + Math.max(0, (bounds().height() - size.height()) / 2);
                children().get(i).layout(font, theme, new Bounds(x, y, size.width(), size.height()));
                x += size.width() + gap;
            }
        }
    }

    /** Every setting uses the full row, with its description label left and a uniform input column right. */
    private static final class SettingRow extends UiContainer {
        private final FittingLabel label;
        private final UiComponent input;

        SettingRow(Component title, Component description, UiComponent input) {
            label = add(new FittingLabel(title, false, false));
            label.tooltip(description);
            this.input = add(input);
        }

        @Override
        protected UiSize measureIntrinsic(Font font, UiTheme theme, int maxWidth, int maxHeight) {
            return new UiSize(maxWidth, Math.min(40, maxHeight));
        }

        @Override
        protected void layoutChildren(Font font, UiTheme theme) {
            int inputWidth = Math.max(0, Math.min(190, (bounds().width() - 12) / 2));
            int labelWidth = Math.max(0, bounds().width() - inputWidth - 12);
            int labelHeight = label.measure(font, theme, labelWidth, bounds().height()).height();
            int inputHeight = input.measure(font, theme, inputWidth, bounds().height()).height();
            label.layout(font, theme, new Bounds(bounds().x(), bounds().centerY() - labelHeight / 2,
                    labelWidth, labelHeight));
            input.layout(font, theme, new Bounds(bounds().right() - inputWidth,
                    bounds().centerY() - inputHeight / 2, inputWidth, inputHeight));
        }
    }

    /** A narrow custom layout keeps action buttons at the right edge regardless of song-name length. */
    private static final class TrackRow extends UiContainer {
        private final FittingLabel name;
        private final Row actions = add(new Row(4, Alignment.END, Alignment.CENTER));

        TrackRow(Component name, IconButton... buttons) {
            this.name = add(new FittingLabel(name));
            for (IconButton button : buttons) actions.add(button);
        }

        @Override
        protected UiSize measureIntrinsic(Font font, UiTheme theme, int maxWidth, int maxHeight) {
            return new UiSize(maxWidth, Math.min(28, maxHeight));
        }

        @Override
        protected void layoutChildren(Font font, UiTheme theme) {
            int actionsWidth = actions.measure(font, theme, bounds().width(), 28).width();
            actions.layout(font, theme, new Bounds(bounds().right() - actionsWidth - 8, bounds().y(),
                    actionsWidth, bounds().height()));
            name.layout(font, theme, new Bounds(bounds().x() + 4, bounds().y() + 7,
                    Math.max(0, bounds().width() - actionsWidth - 20), 16));
        }
    }

    /** The library's labels are single-line; truncate here so names cannot overlap row controls. */
    private static final class FittingLabel extends UiComponent {
        private final Component text;
        private final boolean accent;

        FittingLabel(Component text) { this(text, false, true); }
        FittingLabel(Component text, boolean accent) { this(text, accent, true); }
        FittingLabel(Component text, boolean accent, boolean showTooltip) {
            this.text = text;
            this.accent = accent;
            if (showTooltip) tooltip(text);
        }

        @Override
        protected UiSize measureIntrinsic(Font font, UiTheme theme, int maxWidth, int maxHeight) {
            return new UiSize(Math.min(maxWidth, UiText.width(font, text, theme)),
                    Math.min(maxHeight, UiText.height(font, theme)));
        }

        @Override
        public void render(UiContext context) {
            if (bounds().width() <= 0 || bounds().height() <= 0) return;
            String label = text.getString();
            if (UiText.width(context, label) > bounds().width()) {
                int end = label.length();
                while (end > 0 && UiText.width(context, label.substring(0, end) + "...") > bounds().width()) end--;
                label = end == 0 ? "" : label.substring(0, end) + "...";
            }
            UiText.draw(context, label, bounds().x(), bounds().y(),
                    accent ? context.theme().palette().accent() : context.theme().palette().text());
        }
    }
}
