package se.lite.play;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.manifest.DashManifest;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import se.lite.play.ui.CaptionLook;
import se.lite.play.ui.CaptionView;
import se.lite.play.ui.CatalogueView;
import se.lite.play.ui.HeroView;
import se.lite.play.ui.KeyboardView;
import se.lite.play.ui.LoginView;
import se.lite.play.ui.OsdView;
import se.lite.play.ui.PickerView;
import se.lite.play.ui.PillsView;
import se.lite.play.ui.RowAdapter;
import se.lite.play.ui.RowView;
import se.lite.play.ui.Snack;
import se.lite.play.ui.TabsView;
import se.lite.play.ui.Theme;
import se.lite.play.ui.Ui;

/**
 * The whole app: one Activity, one SurfaceView, one ExoPlayer, a menu panel over the video.
 * Behaviour follows client/design/UX.md; the look follows client/design/DESIGN.md.
 *
 * Video: up/down zaps, OK opens the one player overlay on its action row, left/right open it on its bar and
 * scrub, ← on the first pill opens the rail (U11, A4); held OK is OK (A5).
 * Panel (U10): a side rail with the five sections at the left; up/down in a list moves, OK acts, right
 * opens the child (at most one level below a section), left goes to the rail in one press, Back goes one
 * step out: detail, section list, rail, closed.
 */
public final class MainActivity extends Activity {
    private static final String TAG = "TeliaLite";
    private static final int SEEK_MS = 30_000;
    private static final int SEEK_HELD_MS = 120_000;
    private static final int REQ_VOICE = 7;
    private static final int SEARCH_DEBOUNCE_MS = 300;
    private static final int GROUP_MAX = 6;
    private static final int RECENT_MAX = 8;
    private static final int TITLES_MAX = 2000;
    private static final int SEASONS_MAX = 12;
    private static final long QUERY_KEEP_MS = 10 * 60_000L;

    /** Test hook: build the UI and run the API calls, but never start video playback. */
    static boolean renderOnly;
    /** Test hook: pretend a speech recognizer is (true) or is not (false) installed; null asks the system. */
    static Boolean voiceOverride;
    /** Test hook: the device language ({@code Ljudspråk: Automatisk}); null asks the system. */
    static String languageOverride;

    // Work posted after onDestroy (a timer the dead activity left behind) is dropped, not thrown:
    // the process outlives the activity, and a RejectedExecutionException there kills it (seen on the TV).
    private final ExecutorService io = new java.util.concurrent.ThreadPoolExecutor(
            1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>(), new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
    private final Handler ui = new Handler(Looper.getMainLooper());
    /** The surfaces of the current section (root, then at most one detail; a sheet may sit on top). */
    private ArrayDeque<Screen> stack = new ArrayDeque<>();
    private final Locale sv = Locale.forLanguageTag("sv-SE");
    private final SimpleDateFormat hhmm = new SimpleDateFormat("HH:mm", sv);
    private final SimpleDateFormat dayFmt = new SimpleDateFormat("EEEE d MMM", sv);

    /** Test hook: where Watch Next rows go instead of the TV provider; null = the provider. */
    static WatchNext.Store watchNextStore;
    private Api api;
    private Logos logos;
    /** F16: search thumbnails, episode thumbnails and the Serie hero, under one byte cap. */
    private Images images;
    private HeroView hero;
    /** The player: a real ExoPlayer in the app, a stub in the boundary-jump tests (all calls are Player's). */
    private Player player;
    private VideoFrame video;
    private OsdView osd;
    private CaptionView captions;
    private Snack snack;
    /** "−12:30" top right while playback is behind live (F12, UX.md 4). */
    private LoginView login;
    /** Surface 2: the browser (the guide grid in its Tablå lens, the catalogue in its Katalog lens). */
    private TabsView tabs;
    private CatalogueView catalogue;
    /** The Serie detail: a focused row opens its own screen over the video. */
    private LinearLayout detail;
    private TextView detailEyebrow;
    private TextView detailTitle;
    private TextView detailHint;
    private KeyboardView keyboard;
    private ListView list;
    private RowAdapter<Item> adapter;
    /** Surface 3: the one picker sheet over video or browser. */
    private PickerView picker;
    private boolean pickerOpen;
    /** B4: the full-screen guide grid (the Tablå lens), over the video. */
    private GuideGridView grid;
    private boolean gridOpen;
    /** The browser is up: the Tablå lens (the grid) or the Katalog lens (the catalogue). */
    private boolean browserOpen;
    private boolean catalogueOpen;
    private boolean searchFieldFocus;

    /** One row height per list (DESIGN.md, stable geometry): tall when any row has a secondary line. */
    private boolean listTall;
    /** Any row of the shown list has progress: every row reserves the lane (RowView.lane). */
    private boolean listLane, listLogo;
    /** The list's shared lead column (px): the widest logo or thumbnail slot of any of its rows (Y3). */
    private int listIndent;
    private List<Api.Channel> channels = new ArrayList<>();
    private int current = -1;
    private int previous = -1;
    private boolean playingLive;
    /** A start-over stream of a programme that is still on air (watchMode STARTOVER). */
    private boolean playingStartOver;
    /** The focused pill of the overlay's action row (U11); the row the focus is on lives in the OsdView. */
    private int action;
    private final List<String> actionLabels = new ArrayList<>();
    private final List<Runnable> actionRuns = new ArrayList<>();
    /** The start frame of the title playing: resume position and span, so the passive overlay can be shown again. */
    private long playingResumeMs, playingDurMs;
    private boolean paused;
    /** G6: Fortsätt titta on the TV's home screen, the system's media session, and links waiting for the channels to load. */
    private WatchNext watchNext;
    private List<WatchNext.Entry> watchNextPushed;
    private long watchNextAnchor;
    /** Posters seen while browsing (series art, search posters): Watch Next needs an image for a title that never had one from the list. */
    private final Map<String, String> posterSources = new HashMap<>();
    private SessionBridge session;
    private DeepLink pendingLink;
    private VoiceSearch.Request pendingVoice;
    /** G5: the audio and text tracks of the stream playing, and the settings and session picks that choose among them. */
    private List<TrackInfo> trackList = new ArrayList<>();
    private Tracks lastTracks;
    private TrackPrefs trackPrefs;
    /** The track the viewer chose in Ljud och text this session (TrackInfo.key, or TrackPolicy.TEXT_OFF for text); null = the settings decide. */
    private String audioPick, textPick;
    /** The current programme per channel id, with its start-over option; prefetched on tune. */
    private final Map<String, Api.Program> onAir = new HashMap<>();
    /** F17: where each channel left while behind live was, so zapping back resumes that content moment. */
    private final PlaceStore places = new PlaceStore();
    private String playingTitle = "";
    /** Eyebrow of an episode or film playing ("AVSNITT · TV4 PLAY · STORAGE WARS"); empty for a channel's programme. */
    private String playingEyebrow = "";
    private long zapStartMs;
    private int requestSeq;
    private boolean startFailed;

    /** The screen the list shows now; its selection is saved before another screen replaces it. */
    private Screen shown;
    /** One-off footer text (e.g. why the Röst key did nothing), cleared by the next key. */
    private String transientHint;

    // Search state survives leaving Sök for 10 minutes (UX.md 3.2).
    private String query = "";
    private long searchLeftAt;
    private String resultsQuery;
    private List<Api.Asset> results = new ArrayList<>();
    private boolean searching, searchFailed;
    private int searchSeq;
    private final Set<String> expandedGroups = new HashSet<>();
    private final List<String> recent = new ArrayList<>();
    /** Completion sources: lower-case key to display text, with a rank (0 on air, 1 recent, 2 rest). */
    private final LinkedHashMap<String, String> knownTitles = new LinkedHashMap<>();
    private final Map<String, Integer> titleRank = new HashMap<>();
    private final Runnable searchRun = () -> runSearch(query);
    private final Runnable saveRecentIdle = () -> {
        if (resultsQuery != null && resultsQuery.equals(query.trim()) && !results.isEmpty()) addRecent(query.trim());
    };

    /** Learned in this session: assets (series or films) Telia cannot play. */
    private final Set<String> unplayable = new HashSet<>();
    private final Map<String, List<Api.Asset>> episodesCache = new HashMap<>();
    /** The channels' guides with when each was fetched; {@link #GUIDE_TTL_MS} ages them out. */
    private final Map<String, CachedGuide> guideCache = new HashMap<>();

    /** A guide as cached: the programmes and the time it was fetched. */
    static final class CachedGuide {
        final List<Api.Program> list;
        final long fetchedAtMs;

        CachedGuide(List<Api.Program> list, long fetchedAtMs) {
            this.list = list;
            this.fetchedAtMs = fetchedAtMs;
        }
    }

    /** A cached guide older than this is fetched again when the grid or a tune wants it (found on the TV,
     *  2026-10-17: Tablå opened after a day was blank; the cache had no age, so nothing fetched again). */
    private static final long GUIDE_TTL_MS = 60 * 60_000L;

    // Long-press of Back anywhere: short acts on release, long after the timeout. OK on video acts on release too
    // (A5: held OK on video is OK; the deeper context menu is held OK on a list row, B7).
    private boolean backLongDone, okDown, backDown;
    private final Runnable backLong = () -> {
        backLongDone = true;
        longBack();
    };

    static final int LIST = 0, SEARCH = 1, SHEET = 2;

    // ---- Surfaces (clean room, 2026-10-17) ------------------------------------------------------
    //
    // Two surfaces and one sheet. Surface 1 is the video with one unified strip; Surface 2 is the browser
    // (Tablå: the guide grid; Katalog & Sök: shelves of poster cards and the in-app keyboard); Surface 3 is
    // the universal picker, one right-aligned sheet over either. The rail and the panel are gone: Back on
    // clean video opens the browser, OK enters the strip's pills, and a focused catalogue card opens the
    // Serie detail over the video (one level, Back returns to the catalogue).
    //
    // The detail keeps the old Screen/Item model: one list with rows, pills and headers, drawn by RowView.

    /** One screen of the Serie detail. */
    static final class Screen {
        String eyebrow, title;
        /** Short name for the "‹ Name" hint of the screen above it. */
        final String name;
        final int kind;
        List<Item> items = new ArrayList<>();
        /** Key hints when the focused row has none of its own; "‹ Back" is added by render. */
        String[] hint = {"OK", "Välj"};
        int selection = -1;
        int selTop = Integer.MIN_VALUE;
        /** First open: centre the default row (Kanaler, Tablå). */
        boolean centre;
        /** Serie: the title follows the season of the focused row. */
        boolean titleFollows;
        boolean keyboardFocus = true;
        /** Runs after Back closed this screen (Nästa avsnitt: Back = to live). */
        Runnable onBack;
        /** A rail destination's root (B1): no title, the rail's highlight names it. */
        boolean headerless;
        /** The "‹ …" hint when Back does something other than close (Nästa avsnitt: "Till direkt"). */
        String backName;
        /** Serie detail (F16): half-width panel over the hero, the hero's request and placeholder colour. */
        boolean detail;
        String heroUrl;
        int heroColor;
        int chosenSeason = -1;
        /** Rebuilds the rows from cached data (a season chosen, Min lista changed, a retry landed). */
        Runnable refill;

        Screen(String name, int kind, String eyebrow, String title) {
            this.name = name;
            this.kind = kind;
            this.eyebrow = eyebrow;
            this.title = title;
        }
    }

    /** One row. The display fields map one to one onto RowView. */
    static final class Item {
        String label, sub, meta, group;
        /** A channel row: its logo goes in the row's logo slot ("12 SVT1" when there is no logo). */
        Api.Channel channel;
        /** Screen-reader text when the drawn title is not the identity (channel rows: "12 SVT1"). */
        String desc;
        Object key;
        float progress = -1;
        int trailing = RowView.TRAIL_NONE;
        int hlStart = -1, hlEnd = -1;
        boolean live, dim, marker, header, note;
        /** One-line row in a list whose kind of row is two lines (Meny's plain rows, search suggestions). */
        boolean compact;
        /** A note row that still takes the selection (the loading row): the list keeps its sync state, so the
         *  rows that replace it open at their own selection (ListView syncs to the first row otherwise). */
        boolean loading;
        /** Key hints while this row is focused; null = the screen's. */
        String[] hint;
        /** F16: thumbnail request (proxy URL) for the row's 16:9 slot; null with the slot = placeholder. */
        String image;
        boolean bigThumb, info, thumbSlot;
        /** Serie: empty room at the end of the list (px), so the season chips scroll as a whole row. */
        int spacerPx;
        /** F16: a row of pills (Serie actions, season chips): labels, icons, widths in dp, dim flags. */
        String[] pills;
        int[] pillIcons, pillWdp;
        boolean[] pillDim;
        int pillFocus, pillChosen = -1;
        String[][] pillHints;
        java.util.function.IntConsumer onPill, onPillMove;
        /** Chips: the row ↓ lands on (the season's default episode). */
        Object downKey;
        Runnable onSelect, onRight, onLong;

        Item(String label, Runnable onSelect) {
            this.label = label;
            this.onSelect = onSelect;
        }
    }

    // ---- Setup ---------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        trackPrefs = new TrackPrefs(getSharedPreferences("tracks", MODE_PRIVATE));
        logos = new Logos(this, this::onLogo);
        images = new Images(this, this::onImage);
        SurfaceView surface = buildViews();

        if (!renderOnly) {
            // Small buffers: a fast first frame matters more than a deep buffer.
            DefaultLoadControl load = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(8000, 20000, 1000, 2000)
                    .build();
            // Audio focus and the headset (G6): ExoPlayer pauses on a focus loss, ducks on a transient one and pauses when
            // the headphones are pulled; onPlayWhenReadyChanged mirrors that into the overlay's Paus/Spela.
            ExoPlayer p = new ExoPlayer.Builder(this).setLoadControl(load)
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
                    .setHandleAudioBecomingNoisy(true)
                    .build();
            p.setVideoSurfaceView(surface);
            player = p;
            applyTrackParameters();
            player.addListener(new Player.Listener() {
                @Override
                public void onTracksChanged(Tracks t) {
                    lastTracks = t;
                    tracksChanged(TrackInfo.from(t));
                }

                @Override
                public void onCues(CueGroup cues) {
                    StringBuilder sb = new StringBuilder();
                    for (androidx.media3.common.text.Cue c : cues.cues) {
                        if (c.text == null || c.text.length() == 0) continue;
                        if (sb.length() > 0) sb.append('\n');
                        sb.append(c.text);
                    }
                    captions.setCue(sb);
                }

                @Override
                public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                    playWhenReadyChanged(playWhenReady, reason);
                }

                @Override
                public void onIsPlayingChanged(boolean playing) {
                    updateSession();
                }

                @Override
                public void onPositionDiscontinuity(Player.PositionInfo a, Player.PositionInfo b, int reason) {
                    updateSession();
                }

                @Override
                public void onPlaybackStateChanged(int s) {
                    keepAwake(player.getPlayWhenReady(), s);
                    updateSession();
                    if (s == Player.STATE_ENDED && !playingLive) {
                        Log.i(TAG, dbgState("STATE_ENDED")); // F14 post-hoc: the facts the continue path decides on
                        if (delayedProgramme != null) {
                            // Right after a viewer's boundary jump the stream may end under them at
                            // once (the jump lands near the neighbour's end on purpose). Continuing
                            // then undoes their rewind: hold off and keep the frame (found on the TV,
                            // 2026-10-17). A second jump or a later press moves freely.
                            if (Clock.now() - userJumpAtMs < USER_JUMP_HOLD_MS) {
                                Log.i(TAG, dbgState("STATE_ENDED within " + (USER_JUMP_HOLD_MS / 1000)
                                        + " s of a boundary jump: waiting for the viewer"));
                                return;
                            }
                            continueDelayed();
                        } else if (mediaEnded()) { // an episode with a next one: Nästa avsnitt (F15)
                            return;
                        } else if (current >= 0) { // on-demand from search or series
                            notice("Slut. Tillbaka till direkt.", Snack.INFO, Theme.TOAST_MS);
                            tune(current);
                        }
                        return;
                    }
                    if (s == Player.STATE_READY && zapStartMs > 0) {
                        Log.i(TAG, "first frame " + (Clock.now() - zapStartMs) + " ms");
                        zapStartMs = 0;
                        if (current >= 0) ensureGuide(boundaryChannel() != null ? boundaryChannel() : channels.get(current));
                        // The banner waited for the picture; now it counts down like after a key.
                        if (osd.getVisibility() == View.VISIBLE) flashOverlay();
                        if (pendingSeekMs != 0) { // steps taken while the stream loaded
                            long d = pendingSeekMs;
                            pendingSeekMs = 0;
                            seek(d);
                        }
                    }
                }

                @Override
                public void onVideoSizeChanged(VideoSize v) {
                    if (v.width > 0 && v.height > 0) video.setVideoAspect(v.width * v.pixelWidthHeightRatio / v.height);
                }

                @Override
                public void onTimelineChanged(androidx.media3.common.Timeline t, int reason) {
                    if (reason == Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE) alignStartOver();
                }

                @Override
                public void onPlayerError(PlaybackException e) {
                    if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                        // Paused or rebuffered past the start of a sliding window: resume at the live position.
                        Log.i(TAG, "behind live window, resuming at the live position");
                        player.seekToDefaultPosition();
                        player.prepare();
                        return;
                    }
                    Log.w(TAG, "player error " + e.getErrorCodeName(), e);
                    playbackFailed(e.errorCode == PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED
                            || e.errorCode == PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED
                            || e.errorCode == PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION);
                }
            });
        }

        api = new Api(this);
        library = new Library(getFilesDir(), io);
        library.load(); // a few kB: the menu draws Fortsätt titta from it before the server answers
        watchNext = new WatchNext(watchNextStore != null ? watchNextStore : new WatchNext.ProviderStore(this));
        watchNextAnchor = Clock.now();
        session = new SessionBridge(this, ui, new SessionBridge.Controls() {
            @Override
            public void play() {
                if (paused) togglePause();
            }

            @Override
            public void pause() {
                if (!paused) togglePause();
            }

            @Override
            public void seekTo(long positionMs) {
                if (player != null) scrub(positionMs - player.getCurrentPosition());
            }

            @Override
            public void fastForward() {
                scrub(SEEK_MS);
            }

            @Override
            public void rewind() {
                scrub(-SEEK_MS);
            }

            @Override
            public void next() {
                Api.Asset n = nextEpisode(playingMedia);
                if (n != null && n.playable != null) playNextEpisode(n);
            }

            @Override
            public void previous() {
                if (playingLive || playingStartOver) restartCurrentProgramme();
                else restartMedia();
            }
        });
        syncWatchNext();
        loadRecent();
        io.execute(this::loadTitles);
        io.execute(this::start);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    /**
     * What an intent asks for (G6): a link of the app's own (flinga://, UX.md 7.16), a search from the system or the
     * Assistant (ACTION_SEARCH, ACTION_ASSIST: Sök with the query run; MEDIA_PLAY_FROM_SEARCH: the same, and the one
     * result that is certain plays), or nothing, and the app opens as from its icon. Never recreates the activity: a
     * playing session goes on under the new place. Before the channels have loaded the request waits for them.
     */
    private void handleIntent(Intent intent) {
        if (intent == null) return;
        DeepLink link = DeepLink.parse(intent.getData());
        VoiceSearch.Request voice = VoiceSearch.parse(intent);
        if (link == null && voice == null) return;
        if (channels.isEmpty()) { // start() is still loading them: it runs the request after the first tune
            pendingLink = link;
            pendingVoice = voice;
            return;
        }
        if (link != null) route(link);
        else search(voice);
    }

    /** Takes the viewer where a link points. */
    private void route(DeepLink l) {
        switch (l.kind) {
            case DeepLink.CHANNEL: {
                for (int i = 0; i < channels.size(); i++) {
                    if (!channels.get(i).id.equals(l.id)) continue;
                    closeBrowser();
                    if (i != current || !playingLive) tune(i, true);
                    return;
                }
                return; // a channel this subscription does not have: the app stays as it is
            }
            case DeepLink.TITLE:
                openTitle(l.id, l.play);
                return;
            case DeepLink.GUIDE:
                openBrowser(TabsView.TABLA);
                return;
            default:
                search(new VoiceSearch.Request(l.query, false));
        }
    }

    /** Sök with the query typed and run; with {@code play}, the certain result then plays. */
    private void search(VoiceSearch.Request r) {
        if (r.play) {
            Api.Channel c = VoiceSearch.channel(r.query, channels); // a channel's name is the whole answer: no search needed
            if (c != null) {
                closeBrowser();
                tune(channels.indexOf(c), true);
                return;
            }
        }
        voicePlay = r.play ? VoiceSearch.norm(r.query) : null;
        openBrowser(TabsView.KATALOG);
        if (!r.query.isEmpty()) setQuery(r.query, true);
    }

    /** The normalised query whose certain result plays when the results arrive (MEDIA_PLAY_FROM_SEARCH); null otherwise. */
    private String voicePlay;

    /** A series opens its page; a film or episode plays; {@code play} resumes a title in Fortsätt titta. */
    private void openTitle(String id, boolean play) {
        closeBrowser();
        Api.Saved sv = library.saved(id);
        boolean series = id.startsWith("s-");
        if (play && sv != null && sv.mediaId != null) {
            playSaved(sv, new Item("", null), false);
            return;
        }
        if (series) {
            Api.Asset a = new Api.Asset();
            a.id = id;
            a.title = sv != null ? sv.title : "";
            a.isSeries = true;
            a.imageUrl = a.heroUrl = sv != null ? sv.imageUrl : null;
            openSeries(a);
            return;
        }
        Api.Saved film = new Api.Saved();
        film.id = film.mediaId = id;
        film.title = sv != null ? sv.title : "";
        film.provider = sv != null ? sv.provider : "";
        playSaved(film, new Item("", null), false);
    }

    /** The results of a spoken "spela X" arrived: the one certain result plays (a series opens), else Sök shows them. */
    private void playCertain(String q, List<Api.Asset> hits) {
        Api.Asset a = VoiceSearch.asset(q, hits);
        if (a == null || unplayable.contains(a.id)) return;
        if (a.isSeries) openSeries(a);
        else playAsset(a);
    }

    private SurfaceView buildViews() {
        FrameLayout root = new FrameLayout(this);
        video = new VideoFrame(this);
        video.setMode(getPreferences(MODE_PRIVATE).getInt("videoMode", VideoFrame.CONTAIN));
        SurfaceView surface = new SurfaceView(this);
        video.addView(surface);
        root.addView(video, Ui.fullScreen());

        hero = new HeroView(this);
        hero.setVisibility(View.GONE);
        root.addView(hero, Ui.fullScreen());

        captions = new CaptionView(this);
        captions.setLook(CaptionLook.system(this));
        root.addView(captions, Ui.fullScreen());

        osd = Ui.osd(this);
        root.addView(osd, Ui.osdParams());
        captions.avoid(osd, osd::contentTop);

        // Surface 2: the browser. The Tablå lens is the guide grid; the Katalog lens is the catalogue and
        // the in-app keyboard. Both stand over the playing video; the tab row draws above them.
        grid = new GuideGridView(this);
        grid.bind(gridSource, gridListener);
        grid.setVisibility(View.GONE);
        root.addView(grid, Ui.fullScreen());

        catalogue = new CatalogueView(this);
        catalogue.setImages(key -> {
            if (key == null) return null;
            if (key.startsWith("logo:")) {
                for (Api.Channel c : channels) if (c.id.equals(key.substring(5))) {
                    android.graphics.Bitmap d = logos.get(c, false);
                    return d != null ? d : logos.get(c, true);
                }
                return null;
            }
            return images.get(key);
        });
        catalogue.setVisibility(View.GONE);
        root.addView(catalogue, Ui.catalogueParams(this));
        keyboard = Ui.keyboard(this);
        keyboard.setVisibility(View.GONE);
        root.addView(keyboard, Ui.keyboardParams(this));
        keyboard.setBackgroundColor(Theme.BG); // a column of its own: no poster shows between the keys

        tabs = new TabsView(this);
        tabs.setOnLensChange(() -> {
            if (!browserOpen) return;
            if (tabs.active() == TabsView.TABLA) {
                catalogueOpen = false;
                catalogue.setVisibility(View.GONE);
                showKeyboard(false);
                openGrid();
            } else {
                closeGrid();
                openCatalogue();
            }
            tabs.requestFocus(); // choosing a lens keeps the focus here: ↓ enters it, no climb back up (owner, 2026-10-18)
        });
        tabs.setOnFocusChangeListener((v, has) -> {
            grid.setActive(!has);
            catalogue.invalidate();
            updateHint();
        });
        tabs.setVisibility(View.GONE);
        root.addView(tabs, Ui.tabsParams());

        // The Serie detail: a focused catalogue card opens this screen over the video, one level.
        detail = Ui.detail(this);
        detailEyebrow = Ui.panelEyebrow(this);
        detail.addView(detailEyebrow);
        detailTitle = Ui.panelTitle(this);
        detail.addView(detailTitle);
        list = Ui.list(this);
        detail.addView(list, Ui.fill());
        detailHint = Ui.panelHint(this);
        detail.addView(detailHint);
        detail.setVisibility(View.GONE);
        root.addView(detail, Ui.detailParams(this));

        // Surface 3: the universal picker, the one sheet over video or browser.
        picker = new PickerView(this);
        picker.setVisibility(View.GONE);
        root.addView(picker, Ui.pickerParams());

        login = Ui.login(this);
        root.addView(login, Ui.fullScreen());
        snack = Ui.snack(this);
        root.addView(snack, Ui.snackParams(this));
        setContentView(root);

        adapter = Ui.adapter(new ArrayList<>(), (it, row) -> bindRow(it, row
                .tall(listTall && !it.compact).lane(listLane).logoColumn(listLogo).indent(listIndent)
                .text(it.label, it.sub).meta(it.meta).progress(it.progress)
                .trailing(it.trailing).live(it.live).marker(it.marker).header(it.header).note(it.note)
                .highlight(it.hlStart, it.hlEnd)
                .logoIf(it.channel != null, logos.get(it.channel, false), logos.get(it.channel, true), fallback(it.channel))
                .describe(it.desc).dim(it.dim || (it.onSelect == null && !it.header && !it.note))));
        adapter.setPills(it -> it.pills != null, (it, v) -> {
            int[] w = new int[it.pillWdp.length];
            for (int i = 0; i < w.length; i++) w[i] = Theme.dp(this, it.pillWdp[i]);
            v.set(it.pills, w, it.pillIcons, it.pillDim, it.pillFocus, it.pillChosen);
        });
        adapter.setSelectable(it -> !it.header && !it.info && it.spacerPx == 0 && (!it.note || it.loading));
        list.setAdapter(adapter);
        ((se.lite.play.ui.SnapList) list).setGroups(new se.lite.play.ui.SnapList.Groups() {
            @Override public CharSequence groupOf(int pos) {
                Item h = groupHeader(pos);
                return h == null ? null : h.label;
            }

            @Override public CharSequence metaOf(int pos) {
                Item h = groupHeader(pos);
                return h == null ? null : h.meta;
            }

            @Override public boolean headerAt(int pos) {
                return shown != null && pos >= 0 && pos < shown.items.size() && shown.items.get(pos).header;
            }
        });
        list.setOnItemClickListener((p, v, pos, id) -> {
            Screen s = stack.peek();
            transientHint = null;
            if (s == null || pos >= s.items.size()) return;
            Item it = s.items.get(pos);
            s.selection = pos;
            if (it.onSelect != null) it.onSelect.run();
            else updateHint();
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            Screen s = stack.peek();
            if (s == null || pos >= s.items.size() || s.items.get(pos).onLong == null) return false;
            s.selection = pos;
            s.items.get(pos).onLong.run();
            return true;
        });
        list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                Screen s = stack.peek();
                if (s == null || s != shown || pos >= s.items.size()) return;
                s.selection = pos;
                Item above = pos > 0 ? s.items.get(pos - 1) : null;
                if (above != null && above.info && v != null && v.getTop() < rowHeight(above)) {
                    list.setSelectionFromTop(pos, rowHeight(above)); // the actions keep the info block in view
                }
                if (s.detail) {
                    int chips = indexOfKey(s.items, "chips");
                    if (chips >= 0 && pos <= chips) keepTopInView(s, pos, v);
                    else keepChipsInView(s, pos);
                }
                if (s.titleFollows && s.items.get(pos).group != null) {
                    s.title = s.items.get(pos).group;
                    detailTitle.setText(s.title);
                }
                updateHint();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
                updateHint();
            }
        });
        list.setOnFocusChangeListener((v, has) -> {
            if (has) searchFieldFocus = false;
            updateHint();
        });
        keyboard.setOnFocusChangeListener((v, has) -> {
            if (has) searchFieldFocus = false;
            updateQueryDisplay();
            updateHint();
        });
        keyboard.setVoiceAvailable(voiceAvailable());
        keyboard.setListener(new KeyboardView.Listener() {
            @Override public void onType(String s) {
                setQuery(query + s, false);
            }

            @Override public void onBackspace() {
                if (!query.isEmpty()) setQuery(query.substring(0, query.length() - 1), false);
            }

            @Override public void onClear() {
                setQuery("", false);
            }

            @Override public void onVoice() {
                startVoice();
            }

            @Override public boolean onExitRight() {
                return focusList();
            }

            @Override public boolean onExitLeft() {
                focusField();
                return true;
            }

            @Override public void onFocusMoved() {
                transientHint = null;
                updateHint(); // the footer names the key under focus (Y4)
            }
        });
        return surface;
    }

    /** Test hook (M-b): the group eyebrow of row {@code pos} of the surface shown, or null. */
    CharSequence groupLabelForTest(int pos) {
        Item h = groupHeader(pos);
        return h == null ? null : h.label;
    }

    /** The header item of the group that row {@code pos} belongs to (the nearest header at or above it), or null. */
    private Item groupHeader(int pos) {
        Screen s = shown;
        if (s == null || pos < 0 || pos >= s.items.size()) return null;
        for (int i = pos; i >= 0; i--) if (s.items.get(i).header) return s.items.get(i);
        return null;
    }

    private void start() {
        try {
            if (!api.isLoggedIn()) login();
            channels = api.loadChannels();
            if (channels.isEmpty()) {
                startFailed = true;
                ui.post(() -> osd.showText("Inga kanaler i abonnemanget.\nOK Försök igen").flash(0));
                return;
            }
            try {
                api.loadNowNext(channels);
            } catch (Exception e) {
                Log.w(TAG, "now/next", e);
            }
            int saved = getPreferences(MODE_PRIVATE).getInt("ch", 0);
            ui.post(() -> {
                for (Api.Channel c : channels) {
                    knownTitle(c.name, 0);
                    if (c.now != null) knownTitle(c.now.title, 0);
                    if (c.next != null) knownTitle(c.next.title, 2);
                }
                int first = Math.min(saved, channels.size() - 1);
                if (pendingLink != null && pendingLink.kind == DeepLink.CHANNEL) { // a link to a channel starts there, not on the last one
                    for (int i = 0; i < channels.size(); i++) if (channels.get(i).id.equals(pendingLink.id)) first = i;
                }
                tune(first);
                refreshLibrary(true);
                DeepLink l = pendingLink;
                VoiceSearch.Request v = pendingVoice;
                pendingLink = null;
                pendingVoice = null;
                if (l != null && l.kind != DeepLink.CHANNEL) route(l);
                else if (v != null) search(v);
            });
            ui.postDelayed(this::scheduleNowNext, 5 * 60_000);
        } catch (Exception e) {
            Log.w(TAG, "start", e);
            startFailed = true;
            ui.post(() -> osd.showText("Det gick inte att starta.").hints("OK", "Försök igen", Theme.BACK, "Avsluta").flash(0));
        }
    }

    private void retryStart() {
        startFailed = false;
        osd.showText("Startar …").flash(0);
        io.execute(this::start);
    }

    private void scheduleNowNext() {
        io.execute(() -> {
            try {
                api.loadNowNext(channels);
            } catch (Exception e) {
                Log.w(TAG, "now/next", e);
            }
        });
        ui.postDelayed(this::scheduleNowNext, 5 * 60_000);
    }

    /** Device-code login. An expired code is replaced by a new one without asking (UX.md 6). */
    private void login() throws Exception {
        while (true) {
            Api.DeviceCode dc = api.startDeviceLogin();
            ui.post(() -> {
                login.setQr(Qr.bitmap(dc.verificationUri, LoginView.qrSizePx(this)));
                login.setUrl(dc.verificationUri);
                login.setCode(dc.userCode);
                login.setStatus("Väntar på inloggning …", false);
                login.setVisibility(View.VISIBLE);
            });
            long deadline = Clock.now() + dc.expiresInSec * 1000L;
            while (Clock.now() < deadline) {
                Thread.sleep(dc.intervalSec * 1000L);
                if (api.pollDeviceLogin(dc)) {
                    ui.post(() -> {
                        login.setVisibility(View.GONE);
                        login.setQr(null);
                        notice("Inloggad", Snack.SUCCESS, 3000);
                    });
                    return;
                }
            }
            ui.post(() -> login.setStatus("Koden har gått ut. Hämtar en ny …", true));
            Thread.sleep(2000);
        }
    }

    // ---- Playback ------------------------------------------------------------------------

    private void tune(int index) {
        tune(index, false);
    }

    /**
     * Tunes channel {@code index} live. {@code zapped} (↑ ↓, Föregående kanal): a place the channel kept
     * while it was left behind live is resumed instead (F17); any other way of choosing the channel is
     * "Titta direkt", so the place is dropped. The channel left keeps its place either way.
     */
    private void tune(int index, boolean zapped) {
        if (channels.isEmpty()) return;
        int next = (index + channels.size()) % channels.size();
        if (current >= 0 && current != next) leavePlace(channels.get(current));
        clearStartFrame();
        if (current >= 0 && current != next) previous = current;
        current = next;
        tuneFailed = false;
        Api.Channel ch = channels.get(current);
        getPreferences(MODE_PRIVATE).edit().putInt("ch", current).apply();
        PlaceStore.Place kept = places.take(ch.id);
        if (zapped && kept != null && PlaceStore.blocked(kept, Clock.now()) == null) {
            resumePlace(ch, kept);
            return;
        }
        showChannelOsd(ch);
        // Expired, ended or without start-over: live, silently (F17).
        play(ch.playable, true);
        prefetchOnAir(ch);
    }

    // ---- F17: the place of a channel left behind live -------------------------------------------
    //
    // A delayed session is live TV watched behind the edge: time-shifted live, or the start-over stream
    // reached by rewinding (or from Tablå; F14 continues it). Zapping away keeps the content moment
    // (programme + position within it) for up to 30 min; zapping back resumes it on the start-over stream.
    // A title started by hand (episode, film, catch-up) is not a channel: ↑ ↓ show its banner instead.

    /** The programme of the delayed session playing on {@code ch}, or null when {@code ch} plays live or a title plays. */
    private Api.Program delayedOn(Api.Channel ch) {
        if (playingStartOver) {
            Api.Program p = startOverProgramme != null ? startOverProgramme : delayedProgramme;
            return p != null && delayedChannel != null && delayedChannel.id.equals(ch.id) ? p : null;
        }
        if (playingLive && current >= 0 && channels.get(current) == ch && behindLiveMs() > OsdView.LIVE_SLACK_MS) return onAirNow(ch);
        return null;
    }

    /**
     * The broadcast moment playback is at (epoch ms) on a dynamic stream, or C.TIME_UNSET: the window's
     * start plus the player's position; before the start-over window is known, the moment asked for.
     */
    private long contentMomentMs() {
        if (player != null && player.isCurrentMediaItemDynamic() && player.getDuration() != C.TIME_UNSET) {
            long ws = windowStartMs();
            if (ws != C.TIME_UNSET && ws > 0) return PlaceStore.momentOf(ws, player.getCurrentPosition());
        }
        if (playingStartOver && startOverProgramme != null) {
            return startOverAtMs != C.TIME_UNSET ? startOverAtMs : PlaceStore.start(startOverProgramme);
        }
        return C.TIME_UNSET;
    }

    /** Leaving {@code ch} for another channel: its delayed session, if any, keeps its content moment. */
    private void leavePlace(Api.Channel ch) {
        Api.Program p = delayedOn(ch);
        long moment = p == null ? C.TIME_UNSET : contentMomentMs();
        if (moment == C.TIME_UNSET) return;
        places.save(ch.id, p, moment, Clock.now());
        Log.i(TAG, "place kept on " + ch.name + ": " + p.title + " at " + clock(PlaceStore.positionIn(p, moment)));
    }

    /** Zapped back to {@code ch}: the start-over stream from the kept moment, with the usual delayed timeline. */
    private void resumePlace(Api.Channel ch, PlaceStore.Place kept) {
        onAir.put(ch.id, kept.programme); // the programme it was left in, with its start-over option
        Log.i(TAG, "place resumed on " + ch.name + ": " + kept.programme.title + " at " + clock(kept.positionMs));
        playStartOver(kept.programme, kept.momentMs());
    }

    /** ↑ ↓ while a title plays (F17): its banner, the overlay without focus. With the focus they move between its rows. */
    private void showTitleBanner() {
        renderFrame();
        flashOverlay();
    }

    /** Test hook (F17): the broadcast moment playback is at, or C.TIME_UNSET. */
    long contentMomentForTest() {
        return contentMomentMs();
    }

    /**
     * Fetches the current programme's start-over option, so rewinding from live is instant. When the
     * "current" query fails, the channel's guide is fetched instead (it carries the same option and is
     * wanted anyway, UX.md 3.4), so every surface reads one fact about the channel (R4, X3).
     */
    private void prefetchOnAir(Api.Channel ch) {
        if (onAirNow(ch) != null) return;
        io.execute(() -> {
            Api.Program p = null;
            List<Api.Program> progs = null;
            try {
                p = api.current(ch);
            } catch (Exception e) {
                Log.w(TAG, "current " + ch.name, e);
            }
            if (p == null && cachedGuide(ch.id) == null) {
                try {
                    progs = api.guide(ch);
                } catch (Exception e) {
                    Log.w(TAG, "guide " + ch.name, e);
                }
            }
            Api.Program got = p;
            List<Api.Program> guide = progs;
            ui.post(() -> {
                if (guide != null && cachedGuide(ch.id) == null) {
                    guideCache.put(ch.id, new CachedGuide(guide, Clock.now()));
                    for (Api.Program g : guide) knownTitle(g.title, 2);
                }
                if (got != null) onAir.put(ch.id, got);
                Api.Program air = onAirNow(ch);
                // The banner is up for this channel: its hint and pill follow the fact (R4, M13).
                if (air != null && osd.getVisibility() == View.VISIBLE && "channel".equals(osd.modeName()) && !tuneFailed
                        && current >= 0 && channels.get(current) == ch) {
                    buildActions(ch); // the row follows the fact (R4, M13): Paus and Från början appear
                    applyActions();
                }
            });
        });
    }

    /** Channels whose full guide is on its way, so one query runs at a time for each. */
    private final java.util.Set<String> guideLoading = new java.util.HashSet<>();

    /**
     * The channel's whole guide into the one cache, if it is not there. The timeline needs the neighbours of
     * the programme on air (found on the TV, 2026-10-18: after a zap only the programme now was known, so every
     * rewind past its start read as a hard edge). Quiet: it runs after the first picture and on the first step.
     */
    private void ensureGuide(Api.Channel ch) {
        if (ch == null || cachedGuide(ch.id) != null || !guideLoading.add(ch.id)) return;
        io.execute(() -> {
            List<Api.Program> got = null;
            try {
                got = api.guide(ch);
            } catch (Exception e) {
                Log.w(TAG, "guide " + ch.name, e);
            }
            List<Api.Program> guide = got;
            ui.post(() -> {
                guideLoading.remove(ch.id);
                if (guide != null && cachedGuide(ch.id) == null) {
                    guideCache.put(ch.id, new CachedGuide(guide, Clock.now()));
                    for (Api.Program g : guide) knownTitle(g.title, 2);
                }
            });
        });
    }

    /**
     * R4: the one predicate for "this programme can be started over". The banner hint, the control
     * pill, the Tablå row and ← on live all ask it, so they agree.
     */
    static boolean hasStartOver(Api.Program p) {
        return p != null && p.startOver != null && p.startOver.videoId != null && !p.startOver.videoId.isEmpty();
    }

    /** The programme of {@code guide} on air at {@code now}, or null. */
    static Api.Program onAirIn(List<Api.Program> guide, long now) {
        if (guide != null) for (Api.Program g : guide) if (g.startMs <= now && now < g.endMs) return g;
        return null;
    }

    /**
     * The channel's cached guide, or null when there is none or it is older than {@link #GUIDE_TTL_MS}.
     * The grid reads this, so a day-old cache answers null and the grid fetches again (found on the TV,
     * 2026-10-17: Tablå opened after a day was blank).
     */
    private List<Api.Program> cachedGuide(String channelId) {
        CachedGuide c = guideCache.get(channelId);
        if (c == null) return null;
        if (Clock.now() - c.fetchedAtMs > GUIDE_TTL_MS) {
            guideCache.remove(channelId); // gone: the next gridWant queues the channel again
            return null;
        }
        return c.list;
    }

    /**
     * The programme on air on {@code ch}, or null when unknown or over: the "current" answer when it
     * landed, else the same programme out of the channel's guide (X3: Tablå, the banner, the controls,
     * and ← read this one source).
     */
    private Api.Program onAirNow(Api.Channel ch) {
        long now = Clock.now();
        Api.Program p = onAir.get(ch.id);
        if (p != null && p.endMs > now) return p;
        Api.Program g = onAirIn(cachedGuide(ch.id), now);
        if (g != null) onAir.put(ch.id, g);
        return g;
    }

    // ---- Programme-boundary jumps (owner, 2026-10-17) --------------------------------------
    //
    // ← → scrub inside one programme; at its start or end the next step crosses into the neighbour
    // programme of the same channel: backwards into the previous one's catch-up (near its end, where the
    // playhead was heading), forwards into the next one from its start. A hard boundary stands where the
    // neighbour has no option to play: the seek stops at the edge and the overlay says so once. The
    // neighbours come from the channel's guide (the one cache every surface reads, X3).

    /** The guide programme ending nearest before {@code t} with a catch-up option, or null. */
    private Api.Program programmeBefore(Api.Channel ch, long t) {
        List<Api.Program> g = cachedGuide(ch.id);
        if (g == null) return null;
        Api.Program best = null;
        for (Api.Program p : g) {
            if (p.endMs <= t && (best == null || p.endMs > best.endMs)) best = p;
        }
        return best != null && best.catchup != null ? best : null;
    }

    /** The guide programme starting nearest after {@code t}, or null (its catch-up option is read by the caller). */
    private Api.Program programmeAfter(Api.Channel ch, long t) {
        List<Api.Program> g = cachedGuide(ch.id);
        if (g == null) return null;
        Api.Program best = null;
        for (Api.Program p : g) {
            if (p.startMs >= t && (best == null || p.startMs < best.startMs)) best = p;
        }
        return best;
    }

    /** The channel of the delayed session, else the one playing; null before the first tune. */
    private Api.Channel boundaryChannel() {
        Api.Channel ch = delayedChannel != null ? delayedChannel : current >= 0 ? channels.get(current) : null;
        return ch;
    }

    /**
     * Test hook (R4): the call sites agree with {@link #hasStartOver} for the channel playing: the
     * overlay's pills, the Tablå row. Returns null, or what disagrees.
     */
    /** Test hook (harness `now:`): the programme on air a scenario invents, or null. */
    private Api.Program nowForTest;

    void nowForTest(Api.Program p) {
        nowForTest = p;
    }

    String startOverCheckForTest() {
        if (channels.isEmpty() || current < 0) return null;
        Api.Channel ch = channels.get(current);
        if (nowForTest != null) ch.now = nowForTest; // a harness scenario that changes the programme on air
        boolean expect = hasStartOver(onAirNow(ch));
        List<String> bad = new ArrayList<>();
        boolean bannerUp = osd.getVisibility() == View.VISIBLE && "channel".equals(osd.modeName()) && playingLive && !tuneFailed
                && osdChannel == ch;
        if (bannerUp) { // the overlay's row, passive or not: Från början and Paus exactly when the programme can be rewound into
            boolean pill = false, pause = false;
            for (String l : osd.shownActions()) {
                if (l.startsWith("Från början")) pill = true;
                if (l.equals("Paus") || l.equals("Spela")) pause = true;
            }
            if (pill != expect) bad.add("Från början pill");
            if (pause != expect) bad.add("Paus pill (F18)");
        }
        return bad.isEmpty() ? null : ch.name + " start-over=" + expect + " but " + String.join(", ", bad) + " say otherwise";
    }

    private static Object firstKey(Screen s) {
        return s.items.isEmpty() ? null : s.items.get(0).key;
    }

    /** The zap banner: the channel's frame with the action row, no focus (U11), unless the overlay has it already. */
    private void showChannelOsd(Api.Channel ch) {
        showChannelFrame(ch);
        flashOverlay();
    }

    /** The channel's frame and its row, without touching the timer. */
    private void showChannelFrame(Api.Channel ch) {
        osd.showChannel(ch.number, ch.name,
                ch.now == null ? null : ch.now.title,
                ch.now == null ? 0 : ch.now.startMs,
                ch.now == null ? 0 : ch.now.endMs);
        setOsdLogo(ch);
        osdChannel = ch;
        buildActions(ch);
        applyActions();
    }

    // ---- The guide grid (B4) -----------------------------------------------------------------------
    //
    // Tablå from the rail (and the Guide key) is a full-screen page: every channel a row, time the columns, OK
    // on a block starts it. Guides are the ones Tablå's list uses (one fetch per channel, the same cache), asked
    // for the rows on screen and three either side, one at a time, nearest the focus first.

    private static final int GRID_MARGIN_ROWS = 3, GRID_MAX_INFLIGHT = 2;
    private final java.util.Set<String> gridPending = new java.util.HashSet<>();
    /** Channels whose guide fetch failed, with when; older than {@link #GUIDE_TTL_MS} is retried. */
    private final java.util.Map<String, Long> gridFailed = new java.util.HashMap<>();
    private final java.util.ArrayList<Api.Channel> gridQueue = new java.util.ArrayList<>();
    private int gridInFlight;
    private boolean gridOkDown, gridHoldDone;
    private final Runnable gridHold = () -> {
        gridHoldDone = true;
        grid.hold();
    };

    private final GuideGridView.Source gridSource = new GuideGridView.Source() {
        @Override public int rows() {
            return channels.size();
        }

        @Override public String rowLabel(int row) {
            return fallback(channels.get(row));
        }

        @Override public Bitmap logo(int row) {
            Bitmap d = logos.get(channels.get(row), false);
            return d != null ? d : logos.get(channels.get(row), true);
        }

        @Override public boolean logoIsLight(int row) {
            return logos.get(channels.get(row), false) == null && logos.get(channels.get(row), true) != null;
        }

        @Override public List<Api.Program> guide(int row) {
            String id = channels.get(row).id;
            List<Api.Program> g = cachedGuide(id);
            if (g != null) return g;
            return failedGuide(id) ? java.util.Collections.<Api.Program>emptyList() : null;
        }
    };

    /** A guide fetch failed for the channel and the failure is young enough to stand (see {@link #gridFailed}). */
    private boolean failedGuide(String channelId) {
        Long at = gridFailed.get(channelId);
        if (at == null) return false;
        if (Clock.now() - at > GUIDE_TTL_MS) {
            gridFailed.remove(channelId); // an old failure retries when the grid wants the channel again
            return false;
        }
        return true;
    }

    private final GuideGridView.Listener gridListener = new GuideGridView.Listener() {
        @Override public void onVisibleRows(int first, int last, int focusRow) {
            gridWant(first - GRID_MARGIN_ROWS, last + GRID_MARGIN_ROWS);
        }

        @Override public void onOk(int row, Api.Program p) {
            gridOk(row, p);
        }

        @Override public void onHold(int row, Api.Program p) {
            gridSheet(row, p);
        }

        @Override public void onLeftEdge() {
            if (browserOpen) {
                tabs.setVisibility(View.VISIBLE);
                tabs.setActive(TabsView.TABLA);
                tabs.requestFocus();
            }
        }
    };

    /** Opens the grid on the playing channel's programme on air, that row centred. */
    void openGrid() {
        openGrid(playingChannel());
    }

    /** Opens the grid on channel {@code row}'s programme on air, that row centred (the browser's Tablå lens, the Guide key). */
    void openGrid(int row) {
        if (channels.isEmpty() || gridOpen) return;
        closePicker();
        closeDetail();
        osd.hideNow();
        gridOpen = true;
        grid.setAlpha(1f);
        grid.setVisibility(View.VISIBLE);
        grid.open(row, Clock.now());
        grid.requestFocus();
    }

    private void closeGrid() {
        if (!gridOpen) return;
        gridOpen = false;
        ui.removeCallbacks(gridHold);
        gridQueue.clear();
        grid.setVisibility(View.GONE);
    }

    /** OK on a block: the programme on air plays live, a past one from its start (catch-up); a future one does nothing (the footer says when it airs). */
    private void gridOk(int row, Api.Program p) {
        if (p == null) return;
        long now = Clock.now();
        if (p.startMs <= now && now < p.endMs) {
            closeBrowser();
            tune(row);
        } else if (p.endMs <= now && p.catchup != null) {
            closeBrowser();
            playFromPanel(p.catchup, null);
        }
    }

    /** Held OK on a block: the picker of that programme: Titta direkt and Från början on air, Spela från början in catch-up. */
    private void gridSheet(int row, Api.Program p) {
        if (p == null) return;
        long now = Clock.now();
        boolean air = p.startMs <= now && now < p.endMs, past = p.endMs <= now && p.catchup != null;
        boolean off = grid.windowOffNow();
        if (!air && !past && !off) return;
        Api.Channel ch = channels.get(row);
        grid.setAlpha(0.3f); // the sheet is the question: the grid stands back behind it
        List<PickerView.Row> rows = new ArrayList<>();
        Runnable back = () -> {
            grid.setAlpha(1f);
            grid.requestFocus();
        };
        if (off) { // the window is away from now: the way back is the picker's first row (and the Nu chip)
            rows.add(PickerView.Row.action("Gå till nu", (Runnable) () -> {
                closePicker();
                grid.setAlpha(1f);
                grid.goNow(playingChannel(), Clock.now());
                grid.requestFocus();
            }));
        }
        if (air) {
            rows.add(PickerView.Row.action("Titta direkt", (Runnable) () -> {
                closeBrowser();
                tune(row);
            }));
            if (hasStartOver(p)) {
                rows.add(PickerView.Row.action("Från början " + hhmm.format(new Date(p.startMs)), (Runnable) () -> {
                    closeBrowser();
                    if (row != current) tune(row);
                    onAir.put(ch.id, p);
                    playStartOver(p, C.TIME_UNSET);
                }));
            }
        } else if (past) {
            rows.add(PickerView.Row.action("Spela från början", (Runnable) () -> {
                closeBrowser();
                playFromPanel(p.catchup, null);
            }));
        }
        picker.setOnClose(back);
        openPicker("Alternativ", p.title, rows, 0);
    }

    /** The guides of rows {@code first..last} are wanted: the ones not known are queued, nearest the focus first. */
    private void gridWant(int first, int last) {
        for (int r = Math.max(0, first); r <= Math.min(channels.size() - 1, last); r++) {
            Api.Channel c = channels.get(r);
            if (cachedGuide(c.id) != null || failedGuide(c.id) || gridPending.contains(c.id) || gridQueue.contains(c)) continue;
            gridQueue.add(c);
        }
        gridPump();
    }

    private void gridPump() {
        while (gridOpen && gridInFlight < GRID_MAX_INFLIGHT && !gridQueue.isEmpty()) {
            int best = 0, bestD = Integer.MAX_VALUE;
            for (int i = 0; i < gridQueue.size(); i++) {
                int d = Math.abs(channels.indexOf(gridQueue.get(i)) - grid.focusRow());
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            final Api.Channel ch = gridQueue.remove(best);
            gridPending.add(ch.id);
            gridInFlight++;
            io.execute(() -> {
                List<Api.Program> progs = null;
                try {
                    progs = api.guide(ch);
                } catch (Exception e) {
                    Log.w(TAG, "guide grid " + ch.name, e);
                }
                final List<Api.Program> got = progs;
                ui.post(() -> {
                    gridInFlight--;
                    gridPending.remove(ch.id);
                    if (got == null || got.isEmpty()) {
                        gridFailed.put(ch.id, Clock.now());
                    } else {
                        guideCache.put(ch.id, new CachedGuide(got, Clock.now()));
                        for (Api.Program q : got) knownTitle(q.title, 2);
                    }
                    if (gridOpen) grid.dataChanged();
                    gridPump();
                });
            });
        }
    }

    /** Test hook (B4): the grid's focus as "<channel name>|<programme title>", or null while it is closed. */
    String gridFocusForTest() {
        if (!gridOpen) return null;
        Api.Program p = grid.focusProgramme();
        return channels.get(grid.focusRow()).name + "|" + (p == null ? "" : p.title);
    }

    /** Test hooks for the guide-cache TTL (GuideCacheTest). */
    int playingChannelForTest() {
        return playingChannel();
    }

    void openGridForTest(int row) {
        openGrid(row);
    }

    void closeGridForTest() {
        closeGrid();
    }

    void prefetchOnAirForTest() {
        if (current >= 0) prefetchOnAir(channels.get(current));
    }

    /** The title of the programme on air on the channel playing, or null (the fact the whole UI reads). */
    String programmeOnAirTitleForTest() {
        if (current < 0) return null;
        Api.Program p = onAirNow(channels.get(current));
        return p == null ? null : p.title;
    }

    GuideGridView gridForTest() {
        return gridOpen ? grid : null;
    }

    // ---- The player overlay (U11, U0) ------------------------------------------------------------
    //
    // One overlay for every playback kind (live, delayed live, catch-up, VOD): the frame of what plays
    // (eyebrow, title, the bar) and one action row in one order. Two states: the light one (the bar and its
    // numbers, no pills) for the zap banner, every start and scrubbing, and the full one (the pills too). ← →
    // from clean video scrub on the light one: the picture seeks when the keys stop, or at once on OK, and Back
    // cancels. After that OK opens the full overlay on its first pill; Back hides it; it hides by itself after
    // OSD_CONTROLS_MS without a key, never while paused. OK means "confirm what I am doing now" in both, and
    // the hint slot says which.

    /**
     * The pills' one order (clean room 2026-10-17): Paus/Spela, Från början, Hoppa över, Direkt, Tablå,
     * Ljud och text, Alternativ. Only what applies is drawn; the browser is also Back's one-press
     * destination and the picker is also held OK on a block, so the last two always have a home.
     */
    static final String[] ACTION_ORDER = {"Paus", "Från början", "Hoppa över", "Direkt", "Avsnitt", "Tablå", "Ljud och text", "Alternativ"};

    /** A pill label's place in {@link #ACTION_ORDER} ("Spela" is Paus's other state, "Från början 20:00" is Från början); -1 for none. */
    public static int actionRank(String label) {
        if (label.equals("Spela")) return 0;
        for (int i = 0; i < ACTION_ORDER.length; i++) if (label.startsWith(ACTION_ORDER[i])) return i;
        return -1;
    }

    /**
     * The pill labels for one playback kind, in the one order: Paus/Spela when pausable, Från början
     * (with the programme's start on a channel) when there is one, Direkt when the picture is behind live
     * or in catch-up (A3), Avsnitt for an episode of a series, Ljud och text when the stream has audio or text
     * to choose (G5). Pure, so a test can walk every kind.
     */
    public static List<String> actionLabels(boolean pausable, boolean paused, String restart, String skip, boolean direkt, boolean series,
            boolean tracks) {
        List<String> out = new ArrayList<>();
        if (pausable) out.add(paused ? "Spela" : "Paus");
        if (restart != null) out.add(restart);
        if (skip != null) out.add(skip);
        if (direkt) out.add("Direkt");
        if (series) out.add("Avsnitt");
        out.add("Tablå"); // the browser, one press from the strip
        if (tracks) out.add("Ljud och text");
        out.add("Alternativ"); // the picker, one press from the strip
        return out;
    }

    private void buildActions() {
        buildActions(null);
    }

    /**
     * Builds the row for what plays; {@code shown} is the channel a live banner is drawn for when that is
     * not the one playing (a zap burst's preview). Live is pausable when the programme can be rewound
     * into (F18), everything else always is.
     */
    private void buildActions(Api.Channel shown) {
        actionLabels.clear();
        actionRuns.clear();
        Api.Channel ch = shown != null ? shown : current >= 0 && current < channels.size() ? channels.get(current) : null;
        Api.Program air = ch == null ? null : onAirNow(ch);
        boolean pausable = !playingLive || hasStartOver(air);
        String restart;
        if (playingLive) {
            restart = hasStartOver(air) ? "Från början " + hhmm.format(new Date(air.startMs)) : null;
        } else if (playingStartOver) {
            restart = startOverProgramme == null ? null : "Från början " + hhmm.format(new Date(startOverProgramme.startMs));
        } else {
            restart = "Från början";
        }
        boolean series = !playingLive && playingMedia != null && playingMedia.seriesId != null;
        Api.TimelineItem skip = skipItem();
        String skipLabel = skip == null ? null : skip.type.equals(Api.TimelineItem.RECAP) ? "Hoppa över recap" : "Hoppa över intro";
        for (String label : actionLabels(pausable, paused, restart, skipLabel, behindOrCatchUp(), series, TrackPolicy.pillWanted(trackList))) {
            switch (actionRank(label)) {
                case 0: addAction(label, this::togglePause); break;
                case 1: addAction(label, playingLive || playingStartOver ? this::restartCurrentProgramme : this::restartMedia); break;
                case 2: {
                    Api.TimelineItem s = skip;
                    addAction(label, s == null ? this::goLive : () -> skipOver(s)); // the skip pill takes Direkt's place when Direkt is out
                    break;
                }
                case 3: addAction(label, this::goLive); break; // Direkt, behind the skip
                case 4: addAction(label, () -> openSeriesOf(playingMedia)); break;
                case 5: addAction(label, () -> openBrowser(TabsView.TABLA)); break;
                case 6: addAction(label, this::openTracksSheet); break;
                default: addAction(label, this::openOptionsPicker); break;
            }
        }
        if (action >= actionLabels.size()) action = Math.max(0, actionLabels.size() - 1);
    }

    /**
     * The timeline item (skip intro/recap) the playhead is inside now, or null. The spans come from the
     * ticket (`timeLine`, the official app's SGTimelineItem); every ticket measured so far (catch-up,
     * recordings) carries none, series VOD is the open question. Not while scrubbing or paused behind.
     */
    private Api.TimelineItem skipItem() {
        if (playingLive || playingStartOver || playingStream == null) return null;
        if (zapStartMs > 0 || seekTarget != C.TIME_UNSET || cursorMs != C.TIME_UNSET) return null;
        long pos = player == null ? C.TIME_UNSET : player.getCurrentPosition();
        if (pos == C.TIME_UNSET) return null;
        for (Api.TimelineItem t : playingStream.timeLine) {
            if (t.type.equals(Api.TimelineItem.OPENING) || t.type.equals(Api.TimelineItem.RECAP)) {
                if (pos >= t.offsetMs && pos < t.offsetMs + t.durationMs) return t;
            }
        }
        return null;
    }

    /** The Hoppa över pill: the playhead jumps past the span it is inside. */
    private void skipOver(Api.TimelineItem t) {
        cancelSeek();
        player.seekTo(t.offsetMs + t.durationMs);
        showPosition(t.offsetMs + t.durationMs, true);
    }

    /** A3: the picture is behind live on a channel, or a programme of a channel plays from the guide. */
    private boolean behindOrCatchUp() {
        if (playingStartOver) return true;
        if (playingLive) return (cursorMs != C.TIME_UNSET && cursorMs < liveNowMs() - OsdView.LIVE_SLACK_MS) || behindLiveMs() > OsdView.LIVE_SLACK_MS;
        return delayedChannel != null;
    }

    /** The Direkt pill: the channel playing or watched with a delay, live. */
    private void goLive() {
        int idx = delayedChannel != null ? channels.indexOf(delayedChannel) : current;
        if (idx < 0) idx = Math.max(0, current);
        tune(idx);
    }

    private void addAction(String label, Runnable r) {
        actionLabels.add(label);
        actionRuns.add(r);
    }

    /** Shows the overlay with the focus on {@code row}: the action row (OK, first pill) or the bar (← →). */
    private void showOverlay(int row) {
        if (channels.isEmpty() || current < 0) return;
        if (row == OsdView.FOCUS_ACTIONS && osd.focusRow() != OsdView.FOCUS_ACTIONS) action = 0; // one rule: the first pill (U0)
        renderFrame();
        osd.focusRow(row);
        flashOverlay();
    }

    /**
     * The frame of what plays, whatever the kind: the channel's banner on live, the bar from the player
     * when it knows its span, the start frame before (A2). Always with the row (U11).
     */
    private void renderFrame() {
        if (channels.isEmpty() || current < 0) return;
        if (cursorMs != C.TIME_UNSET && boundaryChannel() != null) {
            showCursorFrame(boundaryChannel());
        } else if (playingLive && (player == null || !timeShiftWindow() || behindLiveMs() <= OsdView.LIVE_SLACK_MS)) {
            showChannelFrame(channels.get(current));
        } else if (player != null && zapStartMs == 0 && player.getDuration() != C.TIME_UNSET) {
            showPosition(player.getCurrentPosition(), false);
        } else {
            showStartFrame();
        }
    }

    /** OSD_CONTROLS_MS after the last key while the overlay has the focus, OSD_BANNER_MS for the banner, never while paused. */
    private void flashOverlay() {
        osd.flash(paused ? 0 : osd.focusRow() != OsdView.FOCUS_NONE ? Theme.OSD_CONTROLS_MS : Theme.OSD_BANNER_MS);
    }

    private void applyActions() {
        osd.actions(actionLabels.toArray(new String[0]), action);
    }

    private void hideControls() {
        osd.hideNow();
    }

    private void moveAction(int d) {
        if (osd.focusRow() != OsdView.FOCUS_ACTIONS) return;
        action = Math.max(0, Math.min(actionLabels.size() - 1, action + d));
        applyActions();
        flashOverlay();
    }

    /**
     * ↑ ↓ while the overlay has the focus: ↑ from a pill puts the focus on the seek bar (the pills stay up),
     * ↓ from that bar returns to the pill that was focused; nothing else moves and a zap never fires (F17).
     */
    private void moveRow(int d) {
        int row = osd.focusRow();
        if (row == OsdView.FOCUS_ACTIONS && d < 0) osd.focusBarKeepingPills();
        else if (row == OsdView.FOCUS_BAR && d > 0) {
            // From a scrub on clean video the pills were not drawn: ↓ still reaches them (the first one), and
            // the preview commits on its own timer.
            if (!osd.pillsOnBar()) action = 0;
            if (osd.pillsDrawn()) osd.focusRow(OsdView.FOCUS_ACTIONS);
        }
        flashOverlay();
    }

    /** OK on an empty pill row: the browser, Tablå lens (never a dead end, U3). */
    private void openRailFromVideo() {
        hideControls();
        openBrowser(TabsView.TABLA);
    }

    private void runAction() {
        if (osd.focusRow() != OsdView.FOCUS_ACTIONS) return;
        if (actionRuns.isEmpty()) { // nothing to perform: the browser is what the row leads to (U3)
            openRailFromVideo();
            return;
        }
        if (action < 0 || action >= actionRuns.size()) return;
        Runnable r = actionRuns.get(action);
        if (actionRank(actionLabels.get(action)) != 0) hideControls(); // Paus/Spela keeps the overlay up: only its label changes
        r.run();
    }

    /** One-shot (F18): the stream started next begins paused. */
    private boolean startPaused;

    /**
     * Play/pause: the Paus pill, OK on the bar and the media keys. On live with a programme that can be
     * rewound into (F18, owner: "Live bör ha pausa också om det finns rewind-möjlighet. Då byter vi till
     * den strömmen och pausar") the start-over stream is started at the live position and paused there,
     * invisibly; Spela continues delayed and → to the edge returns to live. Without start-over a live
     * channel cannot pause: the banner says so.
     */
    private void togglePause() {
        int row = osd.focusRow() == OsdView.FOCUS_NONE ? OsdView.FOCUS_ACTIONS : osd.focusRow();
        if (playingLive && paused) { // the audio focus or the headset paused the live window: Spela resumes it
            paused = false;
            if (player != null) player.setPlayWhenReady(true);
            updateSession();
            showOverlay(row);
            return;
        }
        if (playingLive) {
            Api.Channel ch = current >= 0 ? channels.get(current) : null;
            Api.Program p = ch == null ? null : onAirNow(ch);
            if (!hasStartOver(p)) {
                if (ch != null) {
                    prefetchOnAir(ch);
                    showChannelOsd(ch);
                    osd.status("Går inte att pausa", false);
                }
                return;
            }
            cancelSeek();
            paused = true;
            startPaused = true;
            playStartOver(p, liveNowMs());
            showOverlay(row);
            return;
        }
        paused = !paused;
        if (player != null) {
            player.setPlayWhenReady(!paused);
            if (paused) saveMediaProgress(false);
        }
        updateSession();
        showOverlay(row); // the same pill now reads Spela or Paus
    }

    /** Plays the on-demand title from its start (the Från början pill). */
    private void restartMedia() {
        if (player == null) return;
        cancelSeek();
        player.seekTo(0);
        showPosition(0, true);
    }

    /**
     * The Serie surface of the title playing (its episodes), from the Avsnitt pill: the browser's one
     * level over the video (Back returns to the video).
     */
    private void openSeriesOf(Api.Playable p) {
        Api.Asset a = new Api.Asset();
        a.id = p.seriesId;
        a.title = p.seriesTitle == null ? p.title : p.seriesTitle;
        a.isSeries = true;
        openSeries(a, p.mediaId);
    }

    private void clearStartFrame() {
        playingResumeMs = playingDurMs = 0;
    }

    /**
     * The passive overlay of a start (A2) before the player reports a position: a start-over session and
     * a programme from the guide are a channel's timeline (logo, the programme's times, the playhead at
     * the start), an episode or film is its title with Telia's duration and the resume position.
     */
    private void showStartFrame() {
        if (playingStartOver && startOverProgramme != null) {
            Api.Program p = startOverProgramme;
            long live = liveNowMs();
            long head = startOverAtMs != C.TIME_UNSET ? startOverAtMs : p.broadcastStartMs; // render-only: no player to ask
            showTimelineFrame(delayedChannel != null ? delayedChannel : channels.get(current), p.title, p.startMs, p.endMs,
                    live, Math.min(live, head));
            return;
        }
        if (delayedChannel != null && delayedProgramme != null) { // catch-up: the programme on its channel (U0)
            Api.Program p = delayedProgramme;
            showTimelineFrame(delayedChannel, p.title, p.startMs, p.endMs, liveNowMs(), p.startMs + playingResumeMs);
            return;
        }
        osd.showSeek(playingEyebrow, playingTitle, playingResumeMs, playingDurMs);
        buildActions();
        applyActions();
    }

    /** A channel behind live, on the live window or the start-over stream: one frame with the channel's logo and the row. */
    private void showTimelineFrame(Api.Channel ch, String title, long start, long end, long live, long head) {
        if (ch != null) {
            osd.showTimeline(ch.number, ch.name, title, start, end, live, head);
            setOsdLogo(ch);
            osdChannel = ch;
        } else {
            osd.showTimeline(startOverEyebrow(null), title, start, end, live, head);
        }
        buildActions();
        applyActions();
    }

    /** The OSD and headers are dark surfaces: the dark-background artwork, else the light one on a light plate. */
    private void setOsdLogo(Api.Channel ch) {
        Bitmap d = logos.get(ch, false);
        osd.setLogo(d != null ? d : logos.get(ch, true), d == null);
    }

    /** The channel the OSD shows, so a logo that arrives later can be put in its slot. */
    private Api.Channel osdChannel;

    /** What the logo slot shows when there is no logo: the number and the name. */
    private static String fallback(Api.Channel c) {
        return c == null ? null : c.number > 0 ? c.number + " " + c.name : c.name;
    }

    /** A logo arrived: rows rebind (same bitmap stays, no flicker), the OSD and the header get it. */
    private void onLogo() {
        adapter.notifyDataSetChanged();
        if (gridOpen) grid.invalidate();
        if (osdChannel != null && osd.isFrame() && ("channel".equals(osd.modeName()) || "timeline".equals(osd.modeName()))) {
            setOsdLogo(osdChannel);
        }
    }

    /** F16 parts of a row: the Serie info block, or a thumbnail slot (episode or search result). */
    private void bindRow(Item it, RowView row) {
        if (it.spacerPx > 0) row.spacer(it.spacerPx);
        else if (it.info) row.info(it.label);
        else if (it.image != null || it.bigThumb || it.thumbSlot) row.thumb(it.image, images.get(it.image), it.bigThumb);
    }

    /** An image arrived (or failed: null). Rows and the hero keep their slot; only the pixels change. */
    private void onImage(String url, Bitmap bmp) {
        if (bmp == null) return; // the placeholder stays
        for (int i = 0; i < list.getChildCount(); i++) {
            View v = list.getChildAt(i);
            if (v instanceof RowView) ((RowView) v).thumbArrived(url, bmp);
        }
        hero.arrived(url, bmp);
    }

    /** The panel closed: every decoded image goes (rows, hero, cache); disk copies stay. */
    private void releaseImages() {
        hero.clear();
        hero.setVisibility(View.GONE);
        List<View> views = new ArrayList<>();
        for (int i = 0; i < list.getChildCount(); i++) views.add(list.getChildAt(i));
        for (View v : views) if (v instanceof RowView) ((RowView) v).releaseImages();
        images.releaseAll();
    }

    /**
     * UX.md section 6: a failed live tune keeps the banner up with the reason and the keys that help;
     * a failed catch-up or VOD start says so in a toast and returns to live.
     */
    private boolean tuneFailed;

    private void playbackFailed(boolean licence) {
        if (playingLive) {
            tuneFailed = true;
            if (current >= 0) showChannelOsd(channels.get(current));
            osd.status(licence ? "Kanalen gick inte att starta (licens)." : "Kanalen gick inte att starta.", true)
                    .flash(0);
            return;
        }
        notice(licence ? "Det gick inte att spela upp (licens)." : "Programmet gick inte att starta.",
                Snack.ERROR, Theme.TOAST_ERROR_MS);
        if (current >= 0) tune(current);
    }

    /**
     * Eyebrow of a channel watched behind live, on the live window or the start-over stream alike (owner,
     * 2026-10-16: rewinding is just rewinding; the stream switch is invisible): the channel's name, nothing
     * else. The bar with its DIREKT tick and playhead says the rest; no mode word is ever shown (U0).
     */
    public static String delayedEyebrow(String channelName) {
        return channelName == null ? "" : channelName.toUpperCase(Locale.forLanguageTag("sv-SE"));
    }

    private String startOverEyebrow(Api.Channel ch) {
        return delayedEyebrow(ch == null ? null : ch.name);
    }

    private void play(Api.Playable p, boolean live) {
        Log.i(TAG, "play " + (live ? "live" : p.watchMode) + " \"" + p.title + "\" from " + p.startPositionMs + " ms, before: " + scrubState());
        loadHeadMs = C.TIME_UNSET;
        leaveMedia(); // saves the position of the title being left (F15)
        playingMedia = !live && p.mediaId != null ? p : null;
        zapStartMs = Clock.now();
        playingLive = live;
        playingStartOver = !live && "STARTOVER".equals(p.watchMode);
        pendingSeekMs = 0;
        cancelSeek();
        startOverAtMs = C.TIME_UNSET;
        startOverProgramme = null;
        delayedChannel = live ? null : p.channel;
        delayedProgramme = live ? null : p.programme;
        upcoming = null;
        continuing = false;
        if (playingStartOver && current >= 0) { // started from the guide: find its programme for the bar
            Api.Program a = onAirNow(channels.get(current));
            if (a != null && a.startOver != null && a.startOver.videoId.equals(p.videoId)) startOverProgramme = a;
        }
        playingTitle = p.title;
        trackList = new ArrayList<>(); // the new stream reports its own tracks
        lastTracks = null;
        captions.setCue(null);
        paused = startPaused; // only a pause on live carries into the stream it starts (F18)
        startPaused = false;
        buildActions();
        applyActions();
        updateSession();
        // The banner stays while the stream loads (feedback during the wait), then counts down from
        // the first picture. Render-only (no player): the timed hide, so the tour sees a clean frame.
        osd.flash(paused ? 0 : player == null ? Theme.OSD_BANNER_MS : Theme.OSD_HOLD_MS);
        final int seq = ++requestSeq;
        io.execute(() -> {
            try {
                Api.Stream s = api.ticket(p);
                if (seq != requestSeq || player == null) return; // replaced, or render-only
                MediaItem.Builder b = new MediaItem.Builder()
                        .setUri(s.manifestUrl)
                        .setMimeType(MimeTypes.APPLICATION_MPD);
                if (s.licenseUrl != null && !s.licenseUrl.isEmpty()) {
                    b.setDrmConfiguration(new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                            .setLicenseUri(s.licenseUrl)
                            .setLicenseRequestHeaders(s.licenseHeaders)
                            .build());
                }
                if (live) b.setLiveConfiguration(new MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(10_000).build());
                MediaItem item = b.build();
                ui.post(() -> {
                    if (seq != requestSeq) return;
                    mediaStarted(p, s);
                    player.setMediaItem(item, live ? C.TIME_UNSET : p.startPositionMs);
                    player.prepare();
                    player.setPlayWhenReady(!paused); // F18: a pause on live starts its start-over stream paused
                });
            } catch (Exception e) {
                Log.w(TAG, "ticket " + p.title, e);
                ui.post(() -> {
                    if (seq != requestSeq) return;
                    playbackFailed(false);
                });
            }
        });
    }

    /**
     * Plays catch-up, start-over or VOD chosen in the browser or the detail. The browser closes; Back from
     * this playback goes to the video, and the next Back opens the browser again.
     */
    /**
     * @param eyebrow the kind and source of an episode or film ("AVSNITT · TV4 PLAY · STORAGE WARS"); null for a
     *                channel's programme, whose eyebrow is the channel (A2)
     */
    private void playFromPanel(Api.Playable p, String eyebrow) {
        closeBrowser();
        closeDetail(); // the detail's rows go with the screen; Back from the video opens the browser again
        playingEyebrow = eyebrow == null ? "" : eyebrow;
        playingResumeMs = resumeAt(p);
        // The span before the player reports one (F12: the bar shows whenever the overlay does): Telia's
        // duration for a film or episode, the programme's length for catch-up from Tablå.
        playingDurMs = p.durationMs > 0 ? p.durationMs : p.programme != null ? p.programme.endMs - p.programme.startMs : 0;
        playingTitle = p.title;
        play(p, false);
        showStartFrame(); // after play(): it knows what kind of playback this is
    }

    // ---- Surfaces ------------------------------------------------------------------------
    //
    // Surface 2 is the browser: the Tablå lens (the guide grid) and the Katalog & Sök lens (shelves of
    // poster cards, the query field and the in-app keyboard). Surface 3 is the picker, one sheet over
    // video or browser. The Serie detail is the browser's one level: a focused card opens it over the
    // video, Back returns to the catalogue with the card focused.

    /** The channel the browser opens on: the delayed channel of a catch-up, else the live one. */
    private int playingChannel() {
        int d = delayedChannel == null ? -1 : channels.indexOf(delayedChannel);
        return d >= 0 ? d : Math.max(0, current);
    }

    /** Back on clean video, Guide and Menu: the browser, Tablå lens (clean room 2026-10-17). */
    void openBrowser() {
        openBrowser(TabsView.TABLA);
    }

    /** The browser on a lens; the Tablå lens opens the grid on the playing channel's programme on air. */
    void openBrowser(int lens) {
        if (channels.isEmpty() && lens == TabsView.TABLA) return;
        closeDetail();
        closePicker();
        osd.hideNow();
        browserOpen = true;
        tabs.setVisibility(View.VISIBLE);
        updatePip();
        tabs.setActive(lens);
        if (lens == TabsView.TABLA) {
            catalogueOpen = false;
            catalogue.setVisibility(View.GONE);
            keyboard.setVisibility(View.GONE);
            openGrid();
        } else {
            closeGrid();
            openCatalogue();
        }
    }

    /** The Katalog & Sök lens: the shelves (Fortsätt titta, then the catalogue), the field and the keyboard. */
    private void openCatalogue() {
        catalogueOpen = true;
        catalogue.setVisibility(View.VISIBLE);
        showKeyboard(false); // browse first: the field on top, the shelves under it
        rebuildCatalogue();
        refreshLibrary(false);
        if (catalogue.shelfCount() > 0 && query.trim().isEmpty()) focusShelves(); // the content first; ↑ is the field
        else focusField();
    }

    /**
     * While the browser is up and something plays, the picture shrinks to a chip in the top right corner (the tab
     * row's block holds it); the strip and the grid leave a hole there. Otherwise the picture fills the screen again.
     * The same player and surface, only resized: no second stream.
     */
    private void updatePip() {
        boolean on = browserOpen && player != null;
        int w = getResources().getDisplayMetrics().widthPixels;
        android.graphics.RectF r = on ? Ui.pipRect(this, w) : null;
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) video.getLayoutParams();
        if (on) {
            p.width = Math.round(r.width());
            p.height = Math.round(r.height());
            p.gravity = Gravity.TOP | Gravity.END;
            p.topMargin = Math.round(r.top);
            p.rightMargin = Theme.dp(this, Theme.SAFE_X);
        } else {
            p.width = p.height = ViewGroup.LayoutParams.MATCH_PARENT;
            p.gravity = Gravity.NO_GRAVITY;
            p.topMargin = p.rightMargin = 0;
        }
        video.setLayoutParams(p);
        tabs.setPip(r);
        grid.setPip(r);
    }

    /** Closes the browser (Back), to the video. */
    private void closeBrowser() {
        closeGrid();
        if (browserOpen) searchLeftAt = Clock.now(); // the query is kept 10 min after Sök is left
        browserOpen = false;
        updatePip();
        catalogueOpen = false;
        tabs.setVisibility(View.GONE);
        catalogue.setVisibility(View.GONE);
        keyboard.setVisibility(View.GONE);
        releaseImages();
    }

    // ---- The catalogue lens ------------------------------------------------------------------

    /** The field has the focus (↑ from the first row, or ↑ from the keyboard's top row); the layout stays as it is. */
    private void focusField() {
        searchFieldFocus = true;
        catalogue.focusField(true);
        catalogue.setKeyboardFocus(false);
        catalogue.requestFocus();
        updateQueryDisplay();
        updateHint();
    }

    /** The keyboard is up (the field entered, or a key focused): the field and the hint move above it. */
    private void showKeyboard(boolean on) {
        keyboard.setVisibility(on ? View.VISIBLE : View.GONE);
        catalogue.setSearchLayout(on); // the search layout: the keys at the left, the results at the right
    }

    /** The keyboard types into the field: it takes the focus. */
    private void focusKeyboard() {
        searchFieldFocus = false;
        catalogue.focusField(false);
        catalogue.setKeyboardFocus(true);
        showKeyboard(true);
        keyboard.requestFocus();
        updateQueryDisplay();
        updateHint();
    }

    /** The cards take the focus (↓ from the field, or → from the keyboard); an open keyboard stays, so the layout holds still. */
    private void focusShelves() {
        searchFieldFocus = false;
        catalogue.focusField(false);
        catalogue.setKeyboardFocus(false);
        catalogue.requestFocus();
        if (catalogue.shelfCount() == 0) {
            focusField();
            return;
        }
        if (catalogue.shelfIndex() < 0) catalogue.setFocus(0, 0);
        updateHint();
    }

    /** → from the keyboard: the shelves if they have cards, else stay on the keyboard. */
    private boolean focusList() {
        if (catalogue.shelfCount() == 0) return false;
        focusShelves();
        return true;
    }

    // ---- The picker (Surface 3) --------------------------------------------------------------

    /** Opens the picker with its rows; Back closes it and returns the focus to what is under it. */
    void openPicker(String eyebrow, String title, List<PickerView.Row> rows, int focus) {
        pickerOpen = true;
        picker.setContent(eyebrow, title, rows, focus);
        picker.setVisibility(View.VISIBLE);
        picker.setFocus(focus);
    }

    private void closePicker() {
        if (!pickerOpen) return;
        pickerOpen = false;
        picker.setVisibility(View.GONE);
        Runnable r = picker.onClose();
        picker.setOnClose(null);
        if (r != null) r.run();
    }

    /** The picker's focused row changed: ← → on a value row steps it in place. */
    /**
     * The Alternativ pill: the one picker over the video, with the settings and the stream's own choices
     * (Surface 3): Spela från början, Undertext, Ljudspår, Bildformat, Min lista. Back closes it and the
     * strip returns with the focus on the pill.
     */
    private void openOptionsPicker() {
        picker.setOnClose(this::reopenOnOptionsPill);
        openPicker("Alternativ", playingTitle, pickerRows(), 0);
    }

    /** The Alternativ picker's rows again (a value changed in place: the sheet stays, only the value moves). */
    private List<PickerView.Row> optionsRows() {
        return pickerRows();
    }

    /** Builds the Alternativ picker's rows for the current settings and stream, without opening it. */
    private List<PickerView.Row> pickerRows() {
        // Reuse the builder by opening nothing: the values are read from the settings and the tracks.
        List<PickerView.Row> rows = new ArrayList<>();
        for (String l : actionLabels) {
            if (actionRank(l) == 1) {
                final String label = l;
                rows.add(PickerView.Row.action(label, (Runnable) () -> {
                    closePicker();
                    restartCurrentProgramme();
                }));
            }
        }
        String text = trackPrefs.subtitleLabel();
        for (TrackPolicy.Row r : TrackPolicy.rows(trackList, sv)) {
            if (r.selected && !r.header) {
                text = r.label + (r.sub == null ? "" : " · " + r.sub);
                break;
            }
        }
        final String subValue = text;
        rows.add(new PickerView.Row("Undertext", subValue, PickerView.Row.VALUE, (java.util.function.IntConsumer) d -> {
            trackPrefs.nextSubtitleMode();
            textPick = null;
            applyTrackParameters();
            selectTracks();
            picker.setContent("Alternativ", playingTitle, optionsRows(), picker.focus());
        }, false));
        rows.add(new PickerView.Row("Ljudspråk", trackPrefs.audioLabel(sv), PickerView.Row.VALUE, (java.util.function.IntConsumer) d -> {
            trackPrefs.nextAudio();
            audioPick = null;
            applyTrackParameters();
            selectTracks();
            picker.setContent("Alternativ", playingTitle, optionsRows(), picker.focus());
        }, false));
        rows.add(new PickerView.Row("Bildformat", videoModeLabel(), PickerView.Row.VALUE, (java.util.function.IntConsumer) d -> {
            toggleVideoMode();
            picker.setContent("Alternativ", playingTitle, optionsRows(), picker.focus());
        }, false));
        if (playingMedia != null && playingMedia.mediaId != null) {
            String id = playingMedia.seriesId != null ? playingMedia.seriesId : playingMedia.mediaId;
            boolean in = library.inMyList(id);
            rows.add(PickerView.Row.action(in ? "Ta bort från Min lista" : "Lägg till i Min lista", (Runnable) () -> {
                toggleMyList(id, playingTitle, playingMedia.seriesId != null);
                picker.setContent("Alternativ", playingTitle, optionsRows(), picker.focus());
            }));
        }
        rows.add(PickerView.Row.action("Logga ut", (Runnable) this::confirmLogout));
        return rows;
    }

    /** Logga ut asks once, in the same sheet: it ends the session of this TV; the QR code logs in again. */
    private void confirmLogout() {
        List<PickerView.Row> rows = new ArrayList<>();
        rows.add(PickerView.Row.action("Logga ut", (Runnable) this::logout));
        rows.add(PickerView.Row.action("Avbryt", (Runnable) () -> {
            List<PickerView.Row> back = optionsRows();
            picker.setContent("Alternativ", playingTitle, back, back.size() - 1);
        }));
        picker.setContent("Logga ut?", "Du loggar in igen med QR-koden", rows, 1); // Avbryt has the focus: no accident
    }

    private void logout() {
        api.logout();
        watchNextPushed = null;
        io.execute(() -> watchNext.sync(new ArrayList<>())); // another account: nothing of this one on the home screen
        session.hide();
        picker.setOnClose(null);
        closePicker();
        closeBrowser();
        closeDetail(); // another account: nothing to come back to
        osd.hideNow();
        if (player != null) player.stop();
        channels = new ArrayList<>();
        current = previous = -1;
        io.execute(this::start);
    }

    private void reopenOnOptionsPill() {
        renderFrame();
        for (int i = 0; i < actionLabels.size(); i++) if (actionRank(actionLabels.get(i)) == 7) action = i;
        applyActions();
        osd.focusRow(OsdView.FOCUS_ACTIONS);
        flashOverlay();
    }

    private void movePicker(int d) {
        picker.setFocus(picker.move(d));
    }

    /** OK on the picker's focused row: a value steps, an action runs. */
    private void runPicker() {
        PickerView.Row r = picker.row(picker.focus());
        if (r == null) return;
        if (r.kind == PickerView.Row.VALUE) stepPicker(r, 1);
        else if (r.kind == PickerView.Row.ACTION && r.key instanceof Runnable) ((Runnable) r.key).run();
    }

    /** ← → on a value row: the next or previous value, applied at once. */
    private void stepPicker(PickerView.Row r, int d) {
        if (r.key instanceof java.util.function.IntConsumer) ((java.util.function.IntConsumer) r.key).accept(d);
    }

    // ---- The Serie detail ------------------------------------------------------------------

    /** Closes the Serie detail, back to the catalogue (or the video). */
    private void closeDetail() {
        detail.setVisibility(View.GONE);
        hero.setVisibility(View.GONE);
        hero.clear();
        releaseImages();
        shown = null;
        stack.clear();
    }

    // ---- Sök (in the Katalog & Sök lens) -----------------------------------------------------

    /** Sök is the catalogue lens with the query field focused; re-opening within 10 minutes keeps the query. */
    private void openSearch() {
        if (Clock.now() - searchLeftAt > QUERY_KEEP_MS) {
            query = "";
            results = new ArrayList<>();
            resultsQuery = null;
            expandedGroups.clear();
            keyboard.home();
        }
        if (!browserOpen || tabs.active() != TabsView.KATALOG) openBrowser(TabsView.KATALOG);
        focusField(); // the query field first; ↓ into the keyboard
    }

    /** Sets the query from a key, a suggestion, a recent search or voice. {@code now}: search at once. */
    private void setQuery(String q, boolean now) {
        query = q;
        expandedGroups.clear();
        ui.removeCallbacks(searchRun);
        ui.removeCallbacks(saveRecentIdle);
        searchFailed = false;
        if (q.trim().length() >= 2) {
            searching = true;
            ui.postDelayed(searchRun, now ? 0 : SEARCH_DEBOUNCE_MS);
        } else {
            searching = false;
            searchSeq++; // a newer query cancels an older one
        }
        rebuildCatalogue();
    }

    private void runSearch(String q) {
        String qq = q.trim();
        if (qq.length() < 2) return;
        final int seq = ++searchSeq;
        searching = true;
        searchFailed = false;
        rebuildCatalogue();
        io.execute(() -> {
            try {
                List<Api.Asset> hits = api.search(qq);
                ui.post(() -> {
                    if (seq != searchSeq) return;
                    searching = false;
                    results = hits;
                    resultsQuery = qq;
                    if (voicePlay != null) {
                        String want = voicePlay;
                        voicePlay = null;
                        if (want.equals(VoiceSearch.norm(qq))) playCertain(qq, hits);
                    }
                    for (Api.Asset a : hits) knownTitle(a.title, 2);
                    saveTitles();
                    rebuildCatalogue();
                    if (!hits.isEmpty()) ui.postDelayed(saveRecentIdle, 3000);
                    if (pendingFocusResults) {
                        pendingFocusResults = false;
                        focusFirstResult();
                    }
                });
            } catch (Exception e) {
                Log.w(TAG, "search", e);
                ui.post(() -> {
                    if (seq != searchSeq) return;
                    searching = false;
                    searchFailed = true;
                    rebuildCatalogue();
                });
            }
        });
    }

    private boolean pendingFocusResults;

    /**
     * The shelves of the catalogue lens for the current query: with no query, Fortsätt titta, the channels'
     * programmes on air and the recent searches; with a query, the local completions and the search results
     * (series, films, episodes), each a shelf of fixed poster cards (E6).
     */
    private void rebuildCatalogue() {
        if (!catalogueOpen) return;
        List<CatalogueView.Shelf> shelves = new ArrayList<>();
        String q = query.trim();
        if (q.isEmpty()) {
            List<CatalogueView.Card> mine = new ArrayList<>(); // B6: the starred titles come first
            for (Api.Saved sv : library.starred()) {
                String kind = sv.series ? "Serie" : "Film";
                mine.add(new CatalogueView.Card("my:" + sv.id, sv.title, sv.provider.isEmpty() ? kind : kind + " · " + sv.provider,
                        thumbUrl(sv.imageUrl, false), -1f));
            }
            if (!mine.isEmpty()) shelves.add(new CatalogueView.Shelf("Min lista", mine));
            List<CatalogueView.Card> cont = new ArrayList<>();
            for (Api.Saved sv : library.continueWatching) {
                ContinueState st = continueState(sv);
                cont.add(new CatalogueView.Card("cw:" + sv.id, sv.title, st.text, thumbUrl(sv.imageUrl, false), st.progress));
            }
            if (!cont.isEmpty()) shelves.add(new CatalogueView.Shelf("Fortsätt titta", cont));
            List<CatalogueView.Card> nowCards = new ArrayList<>();
            for (Api.Channel c : channels) {
                if (c.now == null) continue;
                nowCards.add(new CatalogueView.Card("ch:" + c.id, c.now.title, c.name, "logo:" + c.id, progress(c.now, Clock.now())));
            }
            if (!nowCards.isEmpty()) shelves.add(new CatalogueView.Shelf("Just nu", nowCards));
            List<CatalogueView.Card> recents = new ArrayList<>();
            for (String r : recent) recents.add(new CatalogueView.Card("rec:" + r, r, "Sökning", null, -1f));
            if (!recents.isEmpty()) shelves.add(new CatalogueView.Shelf("Senaste sökningar", recents));
        } else if (q.length() >= 2) {
            addResultShelves(shelves, q);
        }
        if (shelves.isEmpty() && !q.isEmpty() && !searching) {
            List<CatalogueView.Card> none = new ArrayList<>();
            none.add(new CatalogueView.Card("none", "Inga träffar", "Prova en del av titeln", null, -1f));
            shelves.add(new CatalogueView.Shelf("Sökning", none));
        }
        catalogue.setShelves(shelves);
        catalogue.setQuery(q);
    }

    private void addResultShelves(List<CatalogueView.Shelf> shelves, String q) {
        String nq = norm(q);
        long now = Clock.now();
        List<CatalogueView.Card> chans = new ArrayList<>(), onNow = new ArrayList<>();
        for (Api.Channel c : channels) {
            if (matchAt(norm(c.name), nq) >= 0 && c.now != null) {
                chans.add(new CatalogueView.Card("ch:" + c.id, c.now.title, c.name, "logo:" + c.id, progress(c.now, now)));
            }
            if (c.now != null && matchAt(norm(c.now.title), nq) >= 0) {
                onNow.add(new CatalogueView.Card("ch:" + c.id, c.now.title, c.name, "logo:" + c.id, progress(c.now, now)));
            }
        }
        if (!chans.isEmpty()) shelves.add(new CatalogueView.Shelf("Kanaler", chans));
        if (!onNow.isEmpty()) shelves.add(new CatalogueView.Shelf("Just nu", onNow));
        if (searchFailed) {
            List<CatalogueView.Card> err = new ArrayList<>();
            err.add(new CatalogueView.Card("error", "Sökningen gick inte", "Kontrollera anslutningen", null, -1f));
            shelves.add(new CatalogueView.Shelf("Sökning", err));
            return;
        }
        if (searching || !q.equals(resultsQuery)) return;
        List<CatalogueView.Card> series = new ArrayList<>(), films = new ArrayList<>(), eps = new ArrayList<>();
        for (Api.Asset a : results) {
            CatalogueView.Card card = new CatalogueView.Card("r:" + a.id, a.title, a.kindLabel,
                    thumbUrl(a.imageUrl, false), -1f);
            if (a.imageUrl != null) posterSources.put(a.id, a.imageUrl);
            (a.isSeries ? series : "Film".equals(a.kind) ? films : eps).add(card);
        }
        if (!series.isEmpty()) shelves.add(new CatalogueView.Shelf("Serier", series));
        if (!films.isEmpty()) shelves.add(new CatalogueView.Shelf("Filmer", films));
        if (!eps.isEmpty()) shelves.add(new CatalogueView.Shelf("Avsnitt och program", eps));
    }

    /** OK on a catalogue card: a channel tunes, a Fortsätt titta title plays, a search hit opens or plays. */
    private void openCard(CatalogueView.Card card) {
        if (card == null) return;
        if (card.key.startsWith("ch:")) {
            for (int i = 0; i < channels.size(); i++) {
                if (channels.get(i).id.equals(card.key.substring(3))) {
                    closeBrowser();
                    tune(i);
                    return;
                }
            }
            return;
        }
        if (card.key.startsWith("my:")) {
            for (Api.Saved sv : library.starred()) {
                if (card.key.equals("my:" + sv.id)) {
                    if (sv.series) {
                        openSavedSeries(sv);
                    } else {
                        closeBrowser();
                        playSaved(sv, new Item("", null), false);
                    }
                    return;
                }
            }
            return;
        }
        if (card.key.startsWith("cw:")) {
            for (Api.Saved sv : library.continueWatching) {
                if (card.key.equals("cw:" + sv.id)) {
                    closeBrowser();
                    playSaved(sv, new Item("", null), false);
                    return;
                }
            }
            return;
        }
        if (card.key.startsWith("rec:")) {
            runSuggestion(card.title);
            return;
        }
        if (card.key.startsWith("sug:")) {
            runSuggestion(card.title);
            return;
        }
        if (card.key.startsWith("r:")) {
            for (Api.Asset a : results) {
                if (card.key.equals("r:" + a.id)) {
                    addRecent(query.trim());
                    if (a.isSeries) {
                        openSeries(a);
                    } else {
                        playAsset(a);
                    }
                    return;
                }
            }
        }
    }

    /** A film or episode from the catalogue: the same path the old search list used. */
    private void playAsset(Api.Asset a) {
        if (unplayable.contains(a.id)) {
            notice("Ingår inte i ditt abonnemang", Snack.INFO, 3000);
            return;
        }
        String provider = a.provider == null ? "" : a.provider;
        String eyebrow = join(a.kind.toUpperCase(sv), provider.isEmpty() ? "TELIA PLAY" : provider.toUpperCase(sv)); // mode · source (E7)
        if (a.playable != null) {
            closeBrowser();
            playFromPanel(a.playable, eyebrow);
            return;
        }
        io.execute(() -> {
            try {
                Api.Playable p = api.resolve(a);
                ui.post(() -> {
                    a.playable = p;
                    a.resolved = true;
                    if (p == null) {
                        unplayable.add(a.id);
                        notice("Ingår inte i ditt abonnemang", Snack.INFO, 3000);
                        rebuildCatalogue();
                    } else {
                        closeBrowser();
                        playFromPanel(p, eyebrow);
                    }
                });
            } catch (Exception e) {
                Log.w(TAG, "resolve " + a.id, e);
                ui.post(() -> notice("Det gick inte att hämta programmet. Försök igen.", Snack.ERROR, 6000));
            }
        });
    }

    private void runSuggestion(String text) {
        addRecent(text);
        pendingFocusResults = true;
        setQuery(text, true);
        focusShelves();
    }

    private boolean listChanged;

    private void focusFirstResult() {
        if (!catalogueOpen) return;
        focusShelves();
    }

    /** The query as the catalogue draws it (the field is a View, not a TextView). */
    private void updateQueryDisplay() {
        catalogue.setQuery(query);
        catalogue.setKeyboardFocus(!searchFieldFocus && keyboard.hasFocus());
    }

    // Completions: local and instant (UX.md 5.1).

    private void knownTitle(String t, int rank) {
        if (t == null || t.trim().isEmpty()) return;
        String k = norm(t.trim());
        if (!knownTitles.containsKey(k)) {
            if (knownTitles.size() >= TITLES_MAX) return;
            knownTitles.put(k, t.trim());
        }
        Integer r = titleRank.get(k);
        if (r == null || rank < r) titleRank.put(k, rank);
    }

    private List<String> completions(String q, int max) {
        String nq = norm(q);
        List<String[]> hits = new ArrayList<>(); // {text, sortKey}
        for (String r : recent) knownTitle(r, 1);
        for (Map.Entry<String, String> e : knownTitles.entrySet()) {
            String k = e.getKey();
            if (k.equals(nq)) continue;
            int at = matchAt(k, nq);
            if (at < 0) continue;
            int rank = titleRank.getOrDefault(k, 2);
            String sort = (at == 0 ? "0" : "1") + rank + String.format(Locale.ROOT, "%04d", Math.min(9999, k.length())) + k;
            hits.add(new String[] {e.getValue(), sort});
        }
        Collections.sort(hits, (a, b) -> a[1].compareTo(b[1]));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < hits.size() && out.size() < max; i++) out.add(hits.get(i)[0]);
        return out;
    }

    /** Lower case, punctuation as spaces; same length as the input so match offsets map back. å ä ö stay. */
    private String norm(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            b.append(Character.isLetterOrDigit(c) ? c : ' ');
        }
        return b.toString();
    }

    /** Index where {@code q} starts a word in {@code text}, or -1. */
    private static int matchAt(String text, String q) {
        if (q.trim().isEmpty()) return -1;
        int from = 0;
        while (true) {
            int at = text.indexOf(q, from);
            if (at < 0) return -1;
            if (at == 0 || text.charAt(at - 1) == ' ') return at;
            from = at + 1;
        }
    }

    // Recent searches: newest first, 8 kept, on this device only.

    private void loadRecent() {
        recent.clear();
        String s = getPreferences(MODE_PRIVATE).getString("recent", "");
        for (String r : s.split("\n")) if (!r.trim().isEmpty() && recent.size() < RECENT_MAX) recent.add(r);
    }

    private void addRecent(String q) {
        String t = q == null ? "" : q.trim();
        if (t.length() < 2) return;
        for (int i = recent.size() - 1; i >= 0; i--) if (recent.get(i).equalsIgnoreCase(t)) recent.remove(i);
        recent.add(0, t);
        while (recent.size() > RECENT_MAX) recent.remove(recent.size() - 1);
        saveRecent();
    }

    private void saveRecent() {
        getPreferences(MODE_PRIVATE).edit().putString("recent", String.join("\n", recent)).apply();
    }

    private void recentSheet(String r) {
        List<PickerView.Row> rows = new ArrayList<>();
        rows.add(PickerView.Row.action("Ta bort", (Runnable) () -> {
            recent.remove(r);
            saveRecent();
            closePicker();
            rebuildCatalogue();
        }));
        rows.add(PickerView.Row.action("Rensa alla", (Runnable) () -> {
            recent.clear();
            saveRecent();
            closePicker();
            rebuildCatalogue();
        }));
        picker.setOnClose(this::focusShelves);
        openPicker("Alternativ", r, rows, 0);
    }

    /** Titles from earlier search results, kept on disk for completions (max 2,000). */
    private void loadTitles() {
        try {
            File f = new File(getFilesDir(), "titles.txt");
            if (!f.exists()) return;
            List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            ui.post(() -> {
                for (String l : lines) knownTitle(l, 2);
            });
        } catch (Exception e) {
            Log.w(TAG, "titles", e);
        }
    }

    private void saveTitles() {
        List<String> copy = new ArrayList<>(knownTitles.values());
        io.execute(() -> {
            try {
                Files.write(new File(getFilesDir(), "titles.txt").toPath(), copy, StandardCharsets.UTF_8);
            } catch (Exception e) {
                Log.w(TAG, "titles", e);
            }
        });
    }

    // Voice: the Röst key and KEYCODE_SEARCH.

    private Intent voiceIntent() {
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sv-SE")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Säg vad du vill se");
    }

    /** True when a speech recognizer is installed. Stubbed in tests through {@link #voiceOverride}. */
    boolean voiceAvailable() {
        if (voiceOverride != null) return voiceOverride;
        return voiceIntent().resolveActivity(getPackageManager()) != null;
    }

    private void startVoice() {
        if (!voiceAvailable()) {
            showTransientHint("Röstsökning finns inte på den här enheten");
            return;
        }
        try {
            startActivityForResult(voiceIntent(), REQ_VOICE);
        } catch (ActivityNotFoundException e) {
            keyboard.setVoiceAvailable(false);
            showTransientHint("Röstsökning finns inte på den här enheten");
        }
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQ_VOICE) return;
        ArrayList<String> said = data == null ? null : data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (result != RESULT_OK || said == null || said.isEmpty() || said.get(0).trim().isEmpty()) {
            showTransientHint("Jag hörde inget. Försök igen.");
            return;
        }
        if (!browserOpen || tabs.active() != TabsView.KATALOG) openBrowser(TabsView.KATALOG);
        pendingFocusResults = true;
        setQuery(said.get(0).trim().toLowerCase(sv), true);
    }

    private void showTransientHint(String text) {
        transientHint = text;
        updateHint();
    }

    // ---- Serie ---------------------------------------------------------------------------

    /**
     * Serie: a detail screen (F16, UX.md 7.8). The series' key art fills the screen behind a half-width
     * panel; the list holds, top to bottom: the info block (genre, seasons, age rating, description), the
     * actions (Fortsätt titta / Spela S1 A1, Min lista), the season chips, and the episodes of the chosen
     * season with thumbnails. Default focus: the primary action, which plays the episode in Fortsätt titta,
     * else Telia's suggestion, else the newest playable one; ↓ from the chips lands on that same episode.
     * Seasons Telia cannot play are dim chips; with nothing playable at all the primary action is
     * "Öppna SVT Play" (if installed) or "Tillbaka till sökningen", never a dead list.
     */
    private void openSeries(Api.Asset series) {
        openSeries(series, null);
    }

    /**
     * Serie with {@code episodeId} focused (U9 `Avsnitt` from the player): its season chosen and its row
     * selected; null = the default (the primary action).
     */
    private void openSeries(Api.Asset series, String episodeId) {
        String provider = series.provider == null ? "" : series.provider;
        Screen s = new Screen(series.title, LIST, null, series.title);
        s.hint = new String[] {"OK", "Spela"};
        s.detail = true;
        s.heroColor = series.heroColor;
        s.heroUrl = heroUrl(series);
        loadInto(s, () -> {
            List<Api.Season> seasons = api.seasons(series);
            Map<String, List<Api.Asset>> eps = new HashMap<>();
            for (int i = 0; i < seasons.size() && i < SEASONS_MAX; i++) {
                Api.Season se = seasons.get(i);
                List<Api.Asset> got = episodesCache.get(se.id);
                if (got == null) {
                    try {
                        got = api.episodes(se, series.id, series.title);
                    } catch (Exception e) {
                        Log.w(TAG, "episodes " + se.id, e); // the chip stays; its list is a retry row
                    }
                }
                if (got != null) eps.put(se.id, got);
            }
            return () -> {
                if (series.imageUrl != null || series.heroUrl != null) posterSources.put(series.id, series.heroUrl != null ? series.heroUrl : series.imageUrl);
                seasonsCache.put(series.id, seasons);
                if (series.favorite != null) library.setInMyList(series.id, series.title, true, series.favorite);
                episodesCache.putAll(eps);
                s.heroColor = series.heroColor;
                s.heroUrl = heroUrl(series);
                s.refill = () -> patchItems(s, seriesItems(s, series, seasons, s.chosenSeason));
                int chosen = -1;
                for (int i = 0; episodeId != null && i < seasons.size() && chosen < 0; i++) {
                    List<Api.Asset> got = episodesCache.get(seasons.get(i).id);
                    if (got != null) for (Api.Asset a : got) if (episodeId.equals(a.id)) chosen = i;
                }
                s.items = seriesItems(s, series, seasons, chosen);
                int ep = episodeId == null ? -1 : indexOfKey(s.items, episodeId);
                s.selection = ep >= 0 ? ep : indexOf(s.items, "actions");
            };
        });
    }

    /** The hero request: half the screen in each direction (960 x 540 on a 1080p TV), drawn at 2x. */
    private String heroUrl(Api.Asset series) {
        android.util.DisplayMetrics m = getResources().getDisplayMetrics();
        return Images.scaled(series.heroUrl, Math.max(16, m.widthPixels / 2), Math.max(9, m.heightPixels / 2));
    }

    /** A thumbnail request at exactly the pixel size of its slot (dp x density). */
    private String thumbUrl(String source, boolean episode) {
        int w = Theme.dp(this, episode ? Theme.THUMB_W : Theme.RESULT_W);
        int h = Theme.dp(this, episode ? Theme.THUMB_H : Theme.RESULT_H);
        return Images.scaled(source, w, h);
    }

    /** "S21 A3"; "A3" without a season; "" for daily shows numbered by date. */
    private static String shortEpisode(int season, int number) {
        if (number > 999) number = 0;
        if (season > 0 && number > 0) return "S" + season + " A" + number;
        return number > 0 ? "A" + number : "";
    }

    /** The rows of the Serie detail screen for the chosen season (-1: the default episode's season). */
    private List<Item> seriesItems(Screen s, Api.Asset series, List<Api.Season> seasons, int chosen) {
        String provider = series.provider == null ? "" : series.provider;
        int playable = 0;
        boolean unknown = false;
        int[] playableIn = new int[seasons.size()];
        for (int i = 0; i < seasons.size(); i++) {
            List<Api.Asset> eps = episodesCache.get(seasons.get(i).id);
            if (eps == null) {
                unknown = true;
                continue;
            }
            for (Api.Asset a : eps) if (a.playable != null) playableIn[i]++;
            playable += playableIn[i];
        }
        boolean nothing = playable == 0 && !unknown;
        if (nothing) unplayable.add(series.id);

        // The default episode: Fortsätt titta, Telia's suggestion, else the newest playable one.
        Object resumeKey = resumeEpisode(series);
        Api.Asset def = null;
        int defSeason = -1;
        for (int i = 0; i < seasons.size() && def == null; i++) {
            List<Api.Asset> eps = episodesCache.get(seasons.get(i).id);
            if (eps == null || resumeKey == null) continue;
            for (Api.Asset a : eps) {
                if (a.playable != null && resumeKey.equals(a.id)) {
                    def = a;
                    defSeason = i;
                }
            }
        }
        if (def == null) {
            int fs = firstPlayableSeason(seasons);
            if (fs >= 0) {
                for (Api.Asset a : episodesCache.get(seasons.get(fs).id)) {
                    if (a.playable != null && (def == null || a.number > def.number)) def = a;
                }
                defSeason = fs;
            }
        }
        if (chosen < 0 || chosen >= seasons.size()) {
            chosen = defSeason;
            // Nothing playable known yet: the first season whose list failed, so its retry row shows.
            for (int i = 0; i < seasons.size() && chosen < 0; i++) if (episodesCache.get(seasons.get(i).id) == null) chosen = i;
            if (chosen < 0) chosen = 0;
        }
        s.chosenSeason = chosen;

        List<Item> items = new ArrayList<>();
        // Info block: what it is, how much there is, and the description.
        String count = seasons.size() == 1 ? "1 säsong" : seasons.size() + " säsonger";
        String meta = nothing ? (provider.isEmpty() ? "Inte i Telia Play" : "Inte i Telia Play · finns på " + provider)
                : join(join(provider, series.genre),
                        join(join(seasons.isEmpty() ? "" : count, series.ageRating),
                                join(series.yearProduction, series.imdbRating.isEmpty() ? "" : "IMDb " + series.imdbRating)));
        Item info = new Item(meta, null);
        info.info = true;
        info.key = "info";
        items.add(info);

        // Actions: the primary one and Min lista. Fixed pill widths (E6).
        Item actions = new Item("", null);
        actions.key = "actions";
        List<String> labels = new ArrayList<>();
        List<Integer> icons = new ArrayList<>(), widths = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        List<String[]> hints = new ArrayList<>();
        if (nothing) {
            Intent app = providerApp(provider);
            if (app != null) {
                labels.add("Öppna " + provider);
                acts.add(() -> {
                    try {
                        startActivity(app);
                    } catch (Exception e) {
                        notice("Det gick inte att öppna " + provider, Snack.ERROR, 6000);
                    }
                });
                hints.add(new String[] {"OK", "Öppna " + provider});
            } else {
                labels.add("Tillbaka till sökningen");
                acts.add(this::back);
                hints.add(new String[] {"OK", "Tillbaka"});
            }
            icons.add(PillsView.ICON_NONE);
            widths.add(Theme.ACTION_W);
        } else if (def != null) {
            Api.Asset d = def;
            String ep = shortEpisode(d.season, d.number);
            Library.Local local = library.local(d.id);
            long pos = local != null ? local.positionMs : d.positionMs;
            boolean started = pos >= Library.RESUME_MIN_MS
                    && !Library.finished(pos, local != null && local.durationMs > 0 ? local.durationMs : d.durationMs);
            labels.add(started ? (ep.isEmpty() ? "Fortsätt titta" : "Fortsätt titta · " + ep) : ep.isEmpty() ? "Spela" : "Spela " + ep);
            acts.add(() -> playFromPanel(d.playable, episodeEyebrow(provider, series.title)));
            hints.add(new String[] {"OK", started ? "Fortsätt titta" : "Spela"});
            icons.add(PillsView.ICON_PLAY);
            widths.add(Theme.ACTION_W);
        }
        boolean in = library.inMyList(series.id);
        labels.add(in ? "I Min lista" : "Min lista");
        icons.add(in ? PillsView.ICON_STAR_FILLED : PillsView.ICON_STAR);
        widths.add(Theme.ACTION_2_W);
        acts.add(() -> toggleMyList(series.id, series.title, true));
        hints.add(new String[] {"OK", in ? "Ta bort från Min lista" : "Lägg till i Min lista"});
        int keep = 0; // a Min lista toggle rebuilds the rows: the focus stays on the pill it was on
        for (Item old : s.items) if ("actions".equals(old.key) && old.pills != null) keep = old.pillFocus;
        pills(actions, labels, icons, widths, null, Math.min(keep, labels.size() - 1), -1, hints);
        actions.onPill = i -> acts.get(i).run();
        actions.onLong = () -> savedSheet(series.id, series.title, true, null, null, null, series.recordable, series.description);
        items.add(actions);

        // Season chips, newest first; the chosen one says which season the list below shows.
        Item chosenEp = null;
        if (!seasons.isEmpty()) {
            Item chips = new Item("", null);
            chips.key = "chips";
            List<String> cl = new ArrayList<>();
            List<Integer> cw = new ArrayList<>();
            boolean[] dim = new boolean[seasons.size()];
            List<String[]> ch = new ArrayList<>();
            for (int i = 0; i < seasons.size(); i++) {
                Api.Season se = seasons.get(i);
                cl.add(se.title);
                cw.add(Theme.CHIP_W);
                List<Api.Asset> eps = episodesCache.get(se.id);
                dim[i] = eps != null && playableIn[i] == 0;
                // OK goes to the season's rows on every chip: a dim season's note and a failed season's retry row explain themselves.
                ch.add(new String[] {"OK", "Avsnitt", "← →", "Säsong"});
            }
            List<Integer> noIcons = new ArrayList<>();
            for (int i = 0; i < seasons.size(); i++) noIcons.add(PillsView.ICON_NONE);
            pills(chips, cl, noIcons, cw, dim, chosen, chosen, ch);
            chips.onPillMove = i -> {
                s.chosenSeason = i;
                if (s.refill != null) s.refill.run();
            };
            chips.onPill = i -> focusDown(chips);
            chips.onLong = actions.onLong;
            items.add(chips);
            chosenEp = chips;
        }

        // The episodes of the chosen season, ascending, each with its thumbnail.
        if (!seasons.isEmpty()) {
            Api.Season se = seasons.get(chosen);
            List<Api.Asset> eps = episodesCache.get(se.id);
            if (eps == null) {
                Item retry = new Item("Kunde inte hämta listan", null);
                retry.group = se.title;
                retry.key = "retry:" + se.id;
                retry.hint = new String[] {"OK", "Försök igen"};
                retry.onSelect = () -> {
                    retry.label = "Laddar …";
                    adapter.notifyDataSetChanged();
                    io.execute(() -> {
                        List<Api.Asset> got = null;
                        try {
                            got = api.episodes(se, series.id, series.title);
                        } catch (Exception e) {
                            Log.w(TAG, "episodes " + se.id, e);
                        }
                        List<Api.Asset> fin = got;
                        ui.post(() -> {
                            if (fin != null) episodesCache.put(se.id, fin);
                            if (alive(s) && s.refill != null) s.refill.run();
                        });
                    });
                };
                items.add(retry);
                if (chosenEp != null) chosenEp.downKey = retry.key;
            } else {
                if (playableIn[chosen] == 0) {
                    Item why = new Item(nothing
                            ? (provider.isEmpty() ? "Serien går inte att spela upp med ditt abonnemang."
                            : "Serien finns på " + provider + " men går inte att spela upp här.")
                            : (provider.isEmpty() ? se.title + " ingår inte i ditt abonnemang."
                            : se.title + " finns på " + provider + " men går inte att spela upp här."), null);
                    why.note = true;
                    why.key = "why:" + se.id;
                    items.add(why);
                }
                String sNum = se.title.replaceAll("\\D+", "");
                List<Api.Asset> sorted = new ArrayList<>(eps);
                Collections.sort(sorted, (x, y) -> Integer.compare(x.number, y.number)); // ascending in a season
                String firstPlayable = null;
                for (Api.Asset a : sorted) {
                    Item it = new Item(a.title, null);
                    it.key = a.id;
                    it.group = se.title;
                    it.image = thumbUrl(a.imageUrl, true);
                    it.bigThumb = true;
                    if (a.playable != null) {
                        if (firstPlayable == null) firstPlayable = a.id;
                        it.trailing = RowView.TRAIL_PLAY;
                        it.sub = (sNum.isEmpty() ? "" : "S" + sNum + " · ") + (a.number > 0 && a.number <= 999 ? "A" + a.number : "");
                        if (it.sub.endsWith(" · ")) it.sub = it.sub.substring(0, it.sub.length() - 3);
                        it.onSelect = () -> playFromPanel(a.playable, episodeEyebrow(provider, series.title));
                        it.hint = new String[] {"OK", "Spela"};
                        episodeProgress(it, a, series);
                    } else {
                        it.dim = true;
                        it.sub = "Inte i Telia Play";
                        it.hint = new String[] {"", "Inte i Telia Play"};
                    }
                    items.add(it);
                }
                if (chosenEp != null) {
                    chosenEp.downKey = def != null && chosen == defSeason ? def.id : firstPlayable;
                }
            }
        }
        // Room below the last episode: the list never runs out of content while the chips are half
        // scrolled, so they stay whole under the header or leave as a whole row (C1).
        Item room = new Item("", null);
        room.key = "room";
        room.spacerPx = Math.max(Theme.dp(this, Theme.ROW_H_THUMB), listHeight() - Theme.dp(this, Theme.ROW_H_THUMB));
        items.add(room);
        return items;
    }

    /** Sets an item up as a row of pills; hints[i] is the footer while pill i is focused. */
    private static void pills(Item it, List<String> labels, List<Integer> icons, List<Integer> widthsDp, boolean[] dim,
                              int focus, int chosen, List<String[]> hints) {
        it.pills = labels.toArray(new String[0]);
        it.pillIcons = new int[icons.size()];
        for (int i = 0; i < icons.size(); i++) it.pillIcons[i] = icons.get(i);
        it.pillWdp = new int[widthsDp.size()];
        for (int i = 0; i < widthsDp.size(); i++) it.pillWdp[i] = widthsDp.get(i);
        it.pillDim = dim;
        it.pillFocus = focus;
        it.pillChosen = chosen;
        it.pillHints = hints.toArray(new String[0][]);
        it.hint = it.pillHints.length > 0 ? it.pillHints[Math.max(0, Math.min(focus, it.pillHints.length - 1))] : null;
        it.label = it.pills.length > 0 ? it.pills[Math.max(0, Math.min(focus, it.pills.length - 1))] : "";
        it.onSelect = () -> {
            if (it.onPill != null) it.onPill.accept(it.pillFocus);
        };
    }

    /** ← → on a row of pills: moves its focused pill; at either end nothing happens (no accidental exit). */
    private void movePill(Item it, int dir) {
        int n = it.pills.length, to = it.pillFocus + dir;
        if (to < 0 || to >= n) return;
        it.pillFocus = to;
        it.label = it.pills[to];
        if (it.pillHints != null && to < it.pillHints.length) it.hint = it.pillHints[to];
        if (it.onPillMove != null) it.onPillMove.accept(to);
        else adapter.notifyDataSetChanged();
        updateHint();
    }

    /**
     * ↓ (or OK) from the season chips: the season's default episode (Fortsätt titta or the newest playable),
     * else its first row, kept in view below the chips.
     */
    private boolean focusDown(Item chips) {
        Screen s = stack.peek();
        if (s == null || chips.downKey == null) return false;
        int idx = -1;
        for (int i = 0; i < s.items.size(); i++) if (chips.downKey.equals(s.items.get(i).key)) idx = i;
        if (idx < 0) return false;
        View sel = list.getSelectedView();
        int y = sel == null ? 0 : sel.getBottom();
        for (int i = s.items.indexOf(chips) + 1; i < idx; i++) y += rowHeight(s.items.get(i));
        int h = rowHeight(s.items.get(idx));
        y = Math.max(0, Math.min(y, list.getHeight() - h));
        s.selection = idx;
        list.setSelectionFromTop(idx, y);
        return true;
    }

    private int firstPlayableSeason(List<Api.Season> seasons) {
        for (int i = 0; i < seasons.size(); i++) {
            List<Api.Asset> eps = episodesCache.get(seasons.get(i).id);
            if (eps == null) continue;
            for (Api.Asset a : eps) if (a.playable != null) return i;
        }
        return -1;
    }

    /** The launch intent of an installed TV app whose name matches the provider ("SVT Play"), or null. */
    private Intent providerApp(String provider) {
        if (provider == null || provider.isEmpty()) return null;
        try {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            for (ResolveInfo ri : getPackageManager().queryIntentActivities(q, 0)) {
                CharSequence label = ri.loadLabel(getPackageManager());
                if (label != null && label.toString().trim().equalsIgnoreCase(provider.trim())) {
                    return getPackageManager().getLeanbackLaunchIntentForPackage(ri.activityInfo.packageName);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "apps", e);
        }
        return null;
    }

    // ---- Fortsätt titta, Min lista, resume, Nästa avsnitt (F15, UX.md 7.4, 7.8, 7.10) ----------
    //
    // Telia keeps both lists and the positions (the official app's panels and progress events), so
    // they follow the user between this TV and every other Telia Play device. Library caches them.

    private static final long LIBRARY_STALE_MS = 60_000;
    private static final long PROGRESS_EVERY_MS = 60_000;
    private static final int NEXT_EPISODE_S = 10;
    /** No key for this long: Nästa avsnitt waits for OK instead of counting down (Netflix asks at 90 min). */
    private static final long IDLE_STOP_MS = 90 * 60_000L;

    private Library library;
    /** The on-demand title playing (it has a media id), or null for live, start-over and the guide's catch-up. */
    private Api.Playable playingMedia;
    private Api.Stream playingStream;
    private long lastProgressMs, libraryAt, lastKeyMs;
    private boolean refreshingLibrary;
    private final Map<String, List<Api.Season>> seasonsCache = new HashMap<>();
    private Runnable countdown;

    /** Fetches both lists from Telia; at most once a minute unless forced. The menu patches in place. */
    private void refreshLibrary(boolean force) {
        long now = Clock.now();
        if (refreshingLibrary || (!force && now - libraryAt < LIBRARY_STALE_MS)) return;
        refreshingLibrary = true;
        io.execute(() -> {
            List<Api.Saved> cw = null, ml = null;
            try {
                cw = api.panel(Api.PANEL_CONTINUE, 20);
            } catch (Exception e) {
                Log.w(TAG, "continue watching", e);
            }
            try {
                ml = api.panel(Api.PANEL_MY_LIST, 50);
            } catch (Exception e) {
                Log.w(TAG, "my list", e);
            }
            List<Api.Saved> c = cw, m = ml;
            ui.post(() -> {
                refreshingLibrary = false;
                if (c == null && m == null) return; // offline: the cached lists stay
                libraryAt = now;
                library.applyServer(c, m, now);
                libraryChanged();
            });
        });
    }

    /** Redraws what shows saved state: the catalogue's Fortsätt titta shelf, the detail's rows. */
    private void libraryChanged() {
        syncWatchNext();
        rebuildCatalogue();
        Screen s = shown;
        if (s == null || stack.peek() != s) return;
        if (s.refill != null) s.refill.run();
    }

    private boolean continueExpanded;

    private List<Item> libraryItems() {
        List<Item> out = new ArrayList<>();
        List<Api.Saved> cw = library.continueWatching;
        // The starred titles come first and take the initial focus (B6); Fortsätt titta is the second group.
        Item mh = header("Min lista", library.myList.isEmpty() ? null : String.valueOf(library.myList.size()));
        mh.key = "lib:mh";
        out.add(mh);
        out.addAll(myListItems());
        if (!cw.isEmpty()) {
            Item h = header("Fortsätt titta", String.valueOf(cw.size()));
            h.key = "lib:h";
            out.add(h);
            boolean all = continueExpanded || cw.size() <= GROUP_MAX;
            for (int i = 0; i < cw.size() && (all || i < GROUP_MAX); i++) out.add(continueItem(cw.get(i)));
            if (!all) {
                Item more = new Item("Visa alla " + cw.size(), () -> {
                    continueExpanded = true;
                    libraryChanged();
                });
                more.key = "lib:all";
                more.compact = true;
                more.hint = new String[] {"OK", "Visa alla"};
                out.add(more);
            }
        }
        return out;
    }

    private Object firstContinueKey() {
        return library.continueWatching.isEmpty() ? null : "lib:cw:" + library.continueWatching.get(0).id;
    }

    /**
     * What a Fortsätt titta row says and shows (X2): text and lane come from one pair, the position and
     * the duration Telia implies with it (position + "N min återstår"). A title below the resume threshold
     * plays from its start, so it shows its length ("4 min"), no "kvar" and no lane; a started one shows
     * the minutes left and a lane at position / duration; a series Telia moved on shows "Nästa avsnitt".
     */
    static final class ContinueState {
        String text;
        float progress = -1f;
    }

    static ContinueState continueState(Api.Saved sv) {
        ContinueState s = new ContinueState();
        long pos = sv.positionMs, dur = sv.minutesLeft >= 0 ? pos + sv.minutesLeft * 60_000L : 0;
        boolean started = pos >= Library.RESUME_MIN_MS;
        if (started && dur > 0) {
            s.text = Math.max(1, (dur - pos + 59_999) / 60_000) + " min kvar";
            s.progress = Math.max(0.02f, Math.min(1f, pos / (float) dur));
        } else if (started) { // Telia gave no time left: its rounded percent is all there is
            s.text = "Påbörjad";
            s.progress = Math.max(0.02f, Math.min(1f, sv.percent / 100f));
        } else if (dur > 0) {
            s.text = Math.max(1, Math.round(dur / 60_000.0)) + " min"; // the same number the bar's total rounds to (M-d)
        } else {
            s.text = sv.series ? "Nästa avsnitt" : "Från början";
        }
        return s;
    }

    /**
     * The duration a Fortsätt titta title plays with before the player reports its own (M-d): the one the
     * row's text and lane came from, position + Telia's "N min återstår", when Telia gave it; else the
     * asset's nominal duration. Telia's nominal duration can be far off the real one (UX.md 7.8), and the
     * minutes left are measured against the real one, so row and bar agree and the bar is nearer the truth.
     */
    static long playbackDurationMs(Api.Saved sv, long assetDurationMs) {
        return sv != null && sv.minutesLeft >= 0 ? sv.positionMs + sv.minutesLeft * 60_000L : assetDurationMs;
    }

    /** One Fortsätt titta row: title, "22 min kvar · SVT Play" or "Nästa avsnitt", progress lane. */
    private Item continueItem(Api.Saved sv) {
        Item it = new Item(sv.title, null);
        it.key = "lib:cw:" + sv.id;
        ContinueState state = continueState(sv);
        it.sub = (library.inMyList(sv.id) ? "★ " : "") + (sv.provider.isEmpty() ? state.text : state.text + " · " + sv.provider);
        it.progress = state.progress;
        it.trailing = RowView.TRAIL_PLAY;
        it.hint = sv.series ? new String[] {"OK", "Spela", "›", "Serien"} : new String[] {"OK", "Spela"};
        it.onSelect = () -> playSaved(sv, it, false);
        if (sv.series) it.onRight = () -> openSavedSeries(sv);
        it.onLong = () -> savedSheet(sv.id, sv.title, sv.series, sv, null, null);
        return it;
    }

    private List<Item> myListItems() {
        List<Item> out = new ArrayList<>();
        for (Api.Saved sv : library.starred()) {
            Item it = new Item(sv.title, null);
            it.key = "lib:my:" + sv.id;
            String kind = sv.series ? "Serie" : "Film";
            it.sub = sv.provider.isEmpty() ? kind : kind + " · " + sv.provider;
            if (sv.series) {
                it.trailing = RowView.TRAIL_CHEVRON;
                it.onSelect = () -> openSavedSeries(sv);
                it.onRight = it.onSelect;
            } else {
                it.trailing = RowView.TRAIL_PLAY;
                it.hint = new String[] {"OK", "Spela"};
                it.onSelect = () -> playSaved(sv, it, false);
            }
            it.onLong = () -> savedSheet(sv.id, sv.title, sv.series, null, null, null);
            out.add(it);
        }
        if (out.isEmpty()) {
            Item none = new Item("Inget sparat ännu", null);
            none.key = "lib:my:none";
            none.sub = "Håll OK på en serie eller film för att spara den här";
            none.hint = new String[] {"", "Håll OK på en serie eller film i Sök"};
            out.add(none);
        }
        return out;
    }

    private void openSavedSeries(Api.Saved sv) {
        Api.Asset a = new Api.Asset();
        a.id = sv.id;
        a.title = sv.title;
        a.isSeries = true;
        a.provider = sv.provider;
        a.kind = "Serie";
        openSeries(a);
    }

    /** OK on a saved row: the episode or film Telia points at, from where it stopped. */
    private void playSaved(Api.Saved sv, Item it, boolean fromStart) {
        if (sv.mediaId == null) {
            if (sv.series) openSavedSeries(sv);
            return;
        }
        boolean fromPanel = !stack.isEmpty(); // a catalogue card (Fortsätt titta, Min lista) starts with no panel open
        String keep = it.sub;
        it.sub = "Laddar …";
        adapter.notifyDataSetChanged();
        io.execute(() -> {
            Api.Asset a = null;
            try {
                a = api.media(sv.mediaId, sv.title);
            } catch (Exception e) {
                Log.w(TAG, "media " + sv.mediaId, e);
            }
            Api.Asset got = a;
            ui.post(() -> {
                it.sub = keep;
                adapter.notifyDataSetChanged();
                if (got == null || got.playable == null) {
                    notice(got == null ? "Det gick inte att hämta programmet. Försök igen."
                            : "Ingår inte i ditt abonnemang", got == null ? Snack.ERROR : Snack.INFO, 6000);
                    return;
                }
                if (fromPanel && stack.isEmpty()) return; // the panel was closed meanwhile
                Api.Playable p = got.playable;
                if (sv.series && p.seriesId == null) {
                    p.seriesId = sv.id;
                    p.seriesTitle = sv.title;
                }
                if (!sv.series && sv.positionMs > p.startPositionMs) p.startPositionMs = sv.positionMs;
                p.durationMs = playbackDurationMs(sv, p.durationMs); // the row's length and the bar's total are one number (M-d)
                p.fromStart = fromStart;
                prefetchSeries(p);
                playFromPanel(p, sv.series ? episodeEyebrow(sv.provider, sv.title) : join("FILM", sv.provider.toUpperCase(sv())));
            });
        });
    }

    private Locale sv() {
        return sv;
    }

    /**
     * The asset eyebrow of an episode: the mode word, the source and the series (E7, Y6), e.g.
     * "AVSNITT · TV4 PLAY · STORAGE WARS". Every asset eyebrow starts with its mode word
     * ({@link OsdView#MODE_WORDS}) and names its source; the harness checks every bar-mode shot.
     */
    private String episodeEyebrow(String provider, String seriesTitle) {
        String p = provider == null ? "" : provider.toUpperCase(sv);
        String s = seriesTitle == null ? "" : seriesTitle.toUpperCase(sv);
        return join(join("AVSNITT", p.isEmpty() ? "TELIA PLAY" : p), s);
    }

    private static String join(String a, String b) {
        if (a == null || a.isEmpty()) return b == null ? "" : b; // no empty segment: "a ·  · b" never shows
        return b == null || b.isEmpty() ? a : a + " · " + b;
    }

    private static String episodeMeta(int season, int number) {
        if (number > 999) number = 0; // daily shows number by date ("Avsnitt 2293880")
        if (season <= 0 && number <= 0) return null;
        return season > 0 ? "Säsong " + season + (number > 0 ? " · Avsnitt " + number : "") : "Avsnitt " + number;
    }

    /**
     * Alternativ for a saved title (held OK on a row): Spela från början, Öppna serien, Min lista on/off,
     * Ta bort från Fortsätt titta. `restart`: an episode row's own playable (its from-the-start action).
     */
    private void savedSheet(String id, String title, boolean series, Api.Saved cw, Api.Playable restart,
                            String eyebrow) {
        savedSheet(id, title, series, cw, restart, eyebrow, null, null);
    }

    private void savedSheet(String id, String title, boolean series, Api.Saved cw, Api.Playable restart,
                            String eyebrow, Boolean recordable, String description) {
        Screen s = new Screen("Alternativ", SHEET, "Alternativ", title);
        if (description != null && !description.isEmpty()) { // the description the list has no room for (B5)
            Item d = new Item(description, null);
            d.note = true;
            d.key = "desc";
            s.items.add(d);
        }
        if (restart != null) {
            s.items.add(new Item("Spela från början", () -> {
                stack.pop();
                restart.fromStart = true;
                playFromPanel(restart, eyebrow);
            }));
        } else if (cw != null && cw.positionMs >= Library.RESUME_MIN_MS) {
            s.items.add(new Item("Spela från början", () -> {
                stack.pop();
                Api.Saved fresh = new Api.Saved();
                fresh.id = cw.id;
                fresh.title = cw.title;
                fresh.series = cw.series;
                fresh.provider = cw.provider;
                fresh.mediaId = cw.mediaId;
                Item row = new Item(cw.title, null);
                playSaved(fresh, row, true);
            }));
        }
        if (series && restart == null) {
            Item open = chevron(new Item("Öppna serien", () -> {                stack.pop();
                Api.Saved sv = new Api.Saved();
                sv.id = id;
                sv.title = title;
                sv.series = true;
                openSavedSeries(sv);
            }));
            s.items.add(open);
        }
        if (Boolean.TRUE.equals(recordable)) { // the series can be recorded (userData.npvrInfo / isRecordable)
            s.items.add(new Item("Spela in", () -> {
                back();
                io.execute(() -> {
                    try {
                        String msg = api.scheduleRecording(id, true);
                        ui.post(() -> notice(msg, Snack.SUCCESS, 4000));
                    } catch (Exception e) {
                        Log.w(TAG, "record " + id, e);
                        ui.post(() -> notice("Inspelningen gick inte att schemalägga", Snack.ERROR, Theme.TOAST_ERROR_MS));
                    }
                });
            }));
        }
        boolean in = library.inMyList(id);
        String what = series && restart != null ? "serien " : "";
        s.items.add(new Item(in ? "Ta bort " + what + "från Min lista" : "Lägg till " + what + "i Min lista", () -> {
            back();
            toggleMyList(id, title, series);
        }));
        if (cw != null) {
            s.items.add(new Item("Ta bort från Fortsätt titta", () -> {
                back();
                library.hide(id);
                libraryChanged();
                notice("Borttagen från Fortsätt titta", Snack.SUCCESS, 3000);
                io.execute(() -> {
                    try {
                        api.hideContinue(id);
                    } catch (Exception e) {
                        Log.w(TAG, "hide continue " + id, e); // the next refresh shows it again
                    }
                });
            }));
        }
        push(s);
    }

    /** Optimistic toggle; reverted with an error toast when Telia refuses. */
    private void toggleMyList(String id, String title, boolean series) {
        boolean add = !library.inMyList(id);
        library.setInMyList(id, title, series, add);
        libraryChanged();
        notice(add ? "Tillagd i Min lista" : "Borttagen från Min lista", Snack.SUCCESS, 3000);
        io.execute(() -> {
            try {
                api.setMyList(id, series, add);
            } catch (Exception e) {
                Log.w(TAG, "my list " + id, e);
                ui.post(() -> {
                    library.setInMyList(id, title, series, !add);
                    libraryChanged();
                    notice("Min lista kunde inte sparas. Försök igen.", Snack.ERROR, 6000);
                });
            }
        });
    }

    /** A catalogue row: ★ in front of the secondary line when saved; held OK offers Min lista. */
    private void markSaved(Item it, String id, String title, boolean series) {
        if (library.inMyList(id)) it.sub = "★ " + (it.sub == null ? "" : it.sub);
        it.onLong = () -> savedSheet(id, title, series, null, null, null);
    }

    /** An episode row: "S18 · A3 · 45 min", or the time left and a progress lane when started. */
    private void episodeProgress(Item it, Api.Asset a, Api.Asset series) {
        Library.Local local = library.local(a.id);
        long pos = local != null ? local.positionMs : a.positionMs;
        String base = it.sub == null ? "" : it.sub;
        String extra = null;
        if (local != null && local.durationMs > 0) { // watched on this TV: the player's real duration
            if (Library.finished(pos, local.durationMs)) {
                extra = "Sett";
            } else if (pos >= Library.RESUME_MIN_MS) {
                it.progress = pos / (float) local.durationMs;
                extra = Math.max(1, (local.durationMs - pos + 59_999) / 60_000) + " min kvar";
            }
        } else if (pos >= Library.RESUME_MIN_MS && a.minutesLeft >= 0) { // Telia's numbers; the nominal duration is off
            // One pair for lane and text (X2): the position and the duration its "N min återstår" implies.
            long dur = pos + a.minutesLeft * 60_000L;
            it.progress = Math.max(0.02f, Math.min(1f, pos / (float) dur));
            extra = a.minutesLeft < 3 ? "Sett" : a.minutesLeft + " min kvar";
        }
        // No duration otherwise: Telia's nominal one can be far off (90 min for a 45 min episode). Only the
        // player's real length, once this TV has played the episode, is shown.
        if (extra == null && local != null && local.durationMs > 0) extra = Math.max(1, local.durationMs / 60_000) + " min";
        if (extra != null) it.sub = base.isEmpty() ? extra : base + " · " + extra;
        it.onLong = () -> savedSheet(series.id, series.title, true, null,
                pos >= Library.RESUME_MIN_MS ? a.playable : null, series.title.toUpperCase(sv));
    }

    /** The episode Serie focuses: the one in Fortsätt titta, else Telia's suggestion. */
    private Object resumeEpisode(Api.Asset series) {
        for (Api.Saved sv : library.continueWatching) if (sv.id.equals(series.id) && sv.mediaId != null) return sv.mediaId;
        return series.suggestedId;
    }

    /** Sets where `p` starts (silent resume, UX.md 7.4) and returns it; 0 = from the start. */
    private long resumeAt(Api.Playable p) {
        if (p.mediaId == null) return p.startPositionMs; // a channel's programme: where the caller put it
        long r = p.fromStart ? 0 : library.resumeMs(p);
        p.fromStart = false;
        p.startPositionMs = r;
        return r;
    }

    private static String resumeMeta(String meta, long resumeMs) {
        if (resumeMs <= 0) return meta;
        return (meta == null ? "" : meta + " · ") + "Fortsätter från " + clock(resumeMs);
    }

    /** The ticket of an on-demand title arrived: start its tracking session. */
    private void mediaStarted(Api.Playable p, Api.Stream s) {
        if (p != playingMedia) return;
        playingStream = s;
        lastProgressMs = Clock.now();
        prefetchSeries(p);
        io.execute(() -> {
            try {
                api.playbackEvent(s, "SESSION_START", p.startPositionMs, p.durationMs);
            } catch (Exception e) {
                Log.w(TAG, "progress start", e);
            }
        });
    }

    /** Leaving the title (Back, another title, live): saves its position and ends the session. */
    private void leaveMedia() {
        if (playingMedia == null) return;
        saveMediaProgress(true);
        playingMedia = null;
        playingStream = null;
    }

    /**
     * Saves the position: in the cache at once (Fortsätt titta updates before Telia does), and to Telia as
     * a progress event (SESSION_END and the ticket release when `end`). Not before the first frame.
     */
    private void saveMediaProgress(boolean end) {
        Api.Playable p = playingMedia;
        if (p == null || player == null || zapStartMs > 0) return;
        long pos = player.getCurrentPosition(), dur = player.getDuration();
        if (dur == C.TIME_UNSET || dur <= 0) dur = p.durationMs;
        Api.Asset next = Library.finished(pos, dur) ? nextEpisode(p) : null;
        library.played(p, pos, dur, next == null ? null : next.id, Clock.now());
        syncWatchNext(); // stop, pause and the minute while playing: the home screen follows Fortsätt titta
        lastProgressMs = Clock.now();
        Api.Stream s = playingStream;
        long fp = pos, fd = dur;
        io.execute(() -> {
            try {
                api.playbackEvent(s, end ? "SESSION_END" : "PROGRESS", fp, fd);
                if (end) api.releaseTicket(s, fp);
            } catch (Exception e) {
                Log.w(TAG, "progress", e);
            }
        });
    }

    /** Once a second from tick(): a progress event at the ticket's interval, at least a minute apart. */
    private void reportMediaProgress() {
        if (playingMedia == null || player == null || !player.isPlaying()) return;
        Api.Stream s = playingStream;
        long every = Math.max(PROGRESS_EVERY_MS, s == null ? 0 : s.progressIntervalMs);
        if (Clock.now() - lastProgressMs >= every) saveMediaProgress(false);
    }

    /** The episode after `p` in its series (next number, then the next season), or null. */
    private Api.Asset nextEpisode(Api.Playable p) {
        if (p == null || p.seriesId == null || p.mediaId == null) return null;
        List<Api.Season> seasons = seasonsCache.get(p.seriesId);
        if (seasons == null) return null;
        boolean found = false;
        for (int i = seasons.size() - 1; i >= 0; i--) { // oldest season first
            List<Api.Asset> eps = episodesCache.get(seasons.get(i).id);
            if (eps == null) continue;
            List<Api.Asset> sorted = new ArrayList<>(eps);
            Collections.sort(sorted, (x, y) -> Integer.compare(x.number, y.number));
            for (Api.Asset a : sorted) {
                if (found && a.playable != null) return a;
                if (a.id.equals(p.mediaId)) found = true;
            }
        }
        return null;
    }

    /** Loads the seasons of an episode's series (its season and the next), so Nästa avsnitt is known. */
    private void prefetchSeries(Api.Playable p) {
        if (p == null || p.seriesId == null || seasonsCache.containsKey(p.seriesId)) return;
        String sid = p.seriesId, stitle = p.seriesTitle;
        int season = p.season;
        io.execute(() -> {
            try {
                Api.Asset a = new Api.Asset();
                a.id = sid;
                a.title = stitle;
                List<Api.Season> seasons = api.seasons(a);
                Map<String, List<Api.Asset>> eps = new HashMap<>();
                for (Api.Season se : seasons) {
                    String d = se.title == null ? "" : se.title.replaceAll("\\D+", "");
                    int n = d.isEmpty() || d.length() > 4 ? -1 : Integer.parseInt(d);
                    if (season > 0 && n != season && n != season + 1) continue;
                    if (episodesCache.containsKey(se.id)) continue;
                    eps.put(se.id, api.episodes(se, sid, stitle));
                    if (season <= 0) break;
                }
                ui.post(() -> {
                    episodesCache.putAll(eps);
                    seasonsCache.put(sid, seasons);
                });
            } catch (Exception e) {
                Log.w(TAG, "series " + sid, e);
            }
        });
    }

    /** An on-demand title reached its end. True when Nästa avsnitt took over. */
    private boolean mediaEnded() {
        Api.Playable p = playingMedia;
        if (p == null) return false;
        Api.Asset next = nextEpisode(p);
        leaveMedia();
        if (next == null || next.playable == null) return false;
        offerNextEpisode(next);
        return true;
    }

    /** Test hook: what STATE_ENDED does, for the harness (render-only playback never ends). */
    void endOfMediaForTest() {
        Api.Playable p = playingMedia;
        Api.Asset next = nextEpisode(p);
        if (p != null && next != null && next.id.equals(p.mediaId)) {
            throw new AssertionError("R5 Nästa avsnitt offers the episode that just ended: " + next.id);
        }
        // The same branch the player's STATE_ENDED takes (BoundaryJumpTest reads the hold-off).
        if (delayedProgramme != null) {
            if (Clock.now() - userJumpAtMs < USER_JUMP_HOLD_MS) {
                Log.i(TAG, dbgState("STATE_ENDED within " + (USER_JUMP_HOLD_MS / 1000)
                        + " s of a boundary jump: waiting for the viewer"));
                return;
            }
            continueDelayed();
            return;
        }
        if (!mediaEnded() && current >= 0) tune(current);
    }

    /**
     * Test hook (F14, post-hoc debugging): the delayed session as one line "channel|programme|mode|left",
     * or null when no delayed session plays. The harness's debug command and a TV logcat read this.
     */
    String delayedForTest() {
        if (delayedProgramme == null) return null;
        return (delayedChannel == null ? "?" : delayedChannel.name) + "|" + delayedProgramme.title + "|" + playbackForTest()
                + "|left=" + (delayedRemainingMs() == C.TIME_UNSET ? "unknown" : delayedRemainingMs() / 1000 + "s");
    }

    /**
     * Test hook (R2): the surface on screen: "login", "video", "osd:<mode>" (channel, seek, timeline,
     * asset, text), "browser:tabla", "browser:katalog" or "detail:<title>".
     */
    String surfaceForTest() {
        if (login.getVisibility() == View.VISIBLE) return "login";
        if (detail.getVisibility() == View.VISIBLE && shown != null) return "detail:" + shown.name;
        if (gridOpen) return "grid"; // the browser's Tablå lens
        if (browserOpen) return "browser:katalog";
        if (osd.getVisibility() == View.VISIBLE) return "osd:" + osd.modeName();
        return "video";
    }

    /** Test hook: the focused card's key ("ch:", "cw:", "r:"), or null. */
    String catalogueFocusKeyForTest() {
        if (!catalogueOpen) return null;
        CatalogueView.Card c = catalogue.focusedCard();
        return c == null ? null : c.key;
    }

    /** Test hook (harness): the catalogue lens with this query, run at once (no keyboard walk in a tour). */
    void searchForTest(String q) {
        openBrowser(TabsView.KATALOG);
        setQuery(q, true);
        focusShelves();
    }

    /** Test hook (harness): opens the catalogue card with this key, as OK does on it. */
    void openCardForTest(String key) {
        if (!catalogueOpen) return;
        openCard(catalogue.cardByKey(key));
    }

    /** Test hook (harness): the key of the result card whose title starts with this text, or null. */
    String catalogueKeyForTest(String title) {
        if (!catalogueOpen) return null;
        for (int i = 0; i < catalogue.shelfCount(); i++) {
            for (int k = 0; k < catalogue.cardCount(i); k++) {
                CatalogueView.Card c = catalogue.cardAt(i, k);
                if (c != null && c.title.startsWith(title) && c.key.startsWith("r:")) return c.key;
            }
        }
        return null;
    }

    /** Test hook: the catalogue lens's focused poster title, or null when no shelf has the focus. */
    String catalogueFocusForTest() {
        if (!catalogueOpen) return null;
        CatalogueView.Card c = catalogue.focusedCard();
        return c == null ? null : c.title;
    }

    /** Test hook: the catalogue lens's footer line. */
    CharSequence catalogueHintForTest() {
        return catalogue.shownHint();
    }

    /** Test hook: the picker's eyebrow and focused row as "Alternativ|Undertext", or null when it is closed. */
    String pickerForTest() {
        if (!pickerOpen) return null;
        PickerView.Row r = picker.row(picker.focus());
        return picker.eyebrowForTest() + "|" + (r == null ? "" : r.label);
    }

    /** Test hook (C6): the Serie detail holds one screen below the browser (it is the browser's one level). */
    int panelDepthForTest() {
        return stack.size();
    }

    /** Test hook: the browser's active lens ("Tablå" or "Katalog & Sök"), or null while the browser is closed. */
    String sectionForTest() {
        if (!browserOpen) return null;
        return tabs.active() == TabsView.TABLA ? "Tablå" : "Katalog & Sök";
    }

    /** Test hook (U10): the tab row has the focus (← from the grid's first column, ↑ from the top shelf). */
    String railFocusForTest() {
        return browserOpen && tabs.hasFocus() ? (tabs.active() == TabsView.TABLA ? "Tablå" : "Katalog & Sök") : null;
    }

    /**
     * Test hook: where one ← lands from the focus as it is: "tabs" (the catalogue's field or the keyboard's
     * left column), "key" (a keyboard key not in its first column), "pill" (a Serie pill row not at its
     * first pill), "none" (the grid's first column, nothing to the left) or "video".
     */
    String leftTargetForTest() {
        if (browserOpen) {
            if (tabs.hasFocus()) return "none";
            if (searchFieldFocus) return catalogue.searchLayout() ? "none" : "tabs";
            if (keyboard.hasFocus()) return keyboard.focusedKeyColumn() == 0 ? "none" : "key";
            return catalogue.searchLayout() ? "key" : "none"; // the cards: the keys when they are up, else nothing at the left
        }
        if (detail.getVisibility() != View.VISIBLE) return "video";
        Screen s = stack.peek();
        if (s == null) return "none";
        int pos = list.getSelectedItemPosition();
        Item it = pos >= 0 && pos < s.items.size() ? s.items.get(pos) : null;
        if (it != null && it.pills != null && it.pillFocus > 0) return "pill";
        return "none";
    }

    /**
     * Plays the episode after the one that played, at once: the sheet's Spela nu and the system's "next". The same
     * series and source as the episode that ended keep its eyebrow (mode, source, series).
     */
    private void playNextEpisode(Api.Asset next) {
        String eyebrow = playingEyebrow.startsWith("AVSNITT · ") ? playingEyebrow : episodeEyebrow(next.provider, next.seriesTitle);
        closePicker();
        closeBrowser();
        playFromPanel(next.playable, eyebrow);
    }

    /**
     * Nästa avsnitt (UX.md 7.10): the picker with a 10 s countdown, OK plays now, Back only closes it.
     * After 90 minutes without a key it waits for OK instead of counting down.
     */
    private void offerNextEpisode(Api.Asset next) {
        String series = next.seriesTitle == null || next.seriesTitle.isEmpty() ? next.title : next.seriesTitle;
        int[] left = {NEXT_EPISODE_S};
        boolean idle = android.os.SystemClock.uptimeMillis() - lastKeyMs > IDLE_STOP_MS && lastKeyMs > 0;
        Runnable playNext = () -> {
            if (countdown != null) ui.removeCallbacks(countdown);
            playNextEpisode(next);
        };
        String num = episodeMeta(0, next.number);
        List<PickerView.Row> rows = new ArrayList<>();
        rows.add(PickerView.Row.action("Spela nu" + (num == null ? "" : " · " + num), (Runnable) playNext));
        rows.add(PickerView.Row.action("Tillbaka till direkt", (Runnable) () -> {
            if (countdown != null) ui.removeCallbacks(countdown);
            closePicker();
            if (current >= 0) tune(current);
        }));
        picker.setOnClose(() -> {
            if (countdown != null) ui.removeCallbacks(countdown);
        });
        String eb = idle ? "Nästa avsnitt" : "Nästa avsnitt om " + left[0] + " s";
        openPicker(eb, series, rows, 0);
        if (!idle) {
            countdown = new Runnable() {
                @Override
                public void run() {
                    if (!pickerOpen) return; // closed, or replaced by something else
                    if (--left[0] <= 0) {
                        playNext.run();
                        return;
                    }
                    picker.setEyebrow("Nästa avsnitt om " + left[0] + " s");
                    ui.postDelayed(this, 1000);
                }
            };
            ui.postDelayed(countdown, 1000);
        }
    }

    // ---- Panel mechanics -----------------------------------------------------------------

    private interface Loader {
        /** Runs on the I/O thread; returns the main-thread step that fills the screen. */
        Runnable load() throws Exception;
    }

    /**
     * Opens a surface at once with one dim "Laddar …" row (focus on the list, so Back works), then fills
     * it when the data lands. A failure turns the row into "Kunde inte hämta listan · OK Försök igen".
     */
    private void loadInto(Screen s, Loader loader) {
        Item loading = new Item("Laddar …", null);
        loading.key = "loading";
        loading.note = true; // dim text, never a focused card
        loading.loading = true;
        loading.hint = new String[0]; // the row says it once; the footer keeps only "‹ Back"
        s.items = new ArrayList<>();
        s.items.add(loading);
        if (stack.peek() != s) push(s);
        else render();
        io.execute(() -> {
            try {
                Runnable fill = loader.load();
                ui.post(() -> {
                    if (!alive(s)) return;
                    fill.run();
                    if (s.items.isEmpty()) s.items.add(emptyRow());
                    if (stack.peek() == s) render();
                });
            } catch (Exception e) {
                Log.w(TAG, "load " + s.name, e);
                ui.post(() -> {
                    if (!alive(s)) return;
                    Item err = new Item("Kunde inte hämta listan", () -> loadInto(s, loader));
                    err.key = "error";
                    err.sub = "OK Försök igen"; // UX.md 6: the row says what OK does (B4)
                    err.hint = new String[] {"OK", "Försök igen"};
                    s.items = new ArrayList<>();
                    s.items.add(err);
                    s.selection = 0;
                    if (stack.peek() == s) render();
                });
            }
        });
    }

    /** True while {@code s} is the Serie detail on screen (a load keeps running while the viewer moves). */
    private boolean alive(Screen s) {
        return detail.getVisibility() == View.VISIBLE && stack.contains(s);
    }

    private Item emptyRow() {
        Item it = new Item("Inget att visa här just nu", null);
        it.key = "empty";
        it.hint = new String[] {"", "Inget att visa här just nu"};
        return it;
    }

    private static Item header(String title, String meta) {
        Item h = new Item(title.toUpperCase(Locale.forLanguageTag("sv-SE")), null);
        h.header = true;
        h.meta = meta;
        return h;
    }

    private static Item chevron(Item it) {
        it.trailing = RowView.TRAIL_CHEVRON;
        return it;
    }

    private static int indexOf(List<Item> items, Object key) {
        if (key != null) for (int i = 0; i < items.size(); i++) if (key.equals(items.get(i).key)) return i;
        return adapterFirst(items);
    }

    /** The list's height, or an estimate before the first layout. */
    private int listHeight() {
        int h = list.getHeight();
        return h > 0 ? h : getResources().getDisplayMetrics().heightPixels - Theme.dp(this, 150);
    }

    /**
     * Serie: an episode selected below the season chips keeps the chips whole at the top of the list while
     * it fits under them, so the season it belongs to stays in view (pinned while it can be).
     */
    /**
     * The offset of row `sel` when the list starts at its first row, or the centring default when that
     * row would not be fully visible.
     */
    private int fromListTop(List<Item> items, int sel) {
        boolean tall = tall(items);
        int y = 0;
        for (int i = 0; i < sel && i < items.size(); i++) y += rowH(items.get(i), i, tall);
        int h = listHeight();
        int selH = sel >= 0 && sel < items.size() ? rowH(items.get(sel), sel, tall) : 0;
        return y + selH <= h ? y : Integer.MIN_VALUE;
    }

    /** Serie: the actions and the chips sit where the screen opened (info block in view); a horizontal move never scrolls (A1, B6). */
    private void keepTopInView(Screen s, int pos, View v) {
        int y = fromListTop(s.items, pos);
        if (y != Integer.MIN_VALUE && v != null && v.getTop() != y) list.setSelectionFromTop(pos, y);
    }

    private void keepChipsInView(Screen s, int pos) {
        int chips = indexOfKey(s.items, "chips");
        if (chips < 0 || pos <= chips || !s.items.get(pos).bigThumb) return;
        // The chips start where the band's cut puts them: the actions row above slides out under the fade (B2).
        int y = ((se.lite.play.ui.SnapList) list).firstWholeTop(rowHeight(s.items.get(chips - 1))) + Theme.dp(this, Theme.ROW_H_PILLS);
        for (int i = chips + 1; i < pos; i++) y += rowHeight(s.items.get(i));
        int h = rowHeight(s.items.get(pos));
        View sel = list.getSelectedView();
        if (y + h <= list.getHeight() && (sel == null || sel.getTop() != y)) list.setSelectionFromTop(pos, y);
    }

    /** The position of the row with {@code key}, or -1. */
    private static int indexOfKey(List<Item> items, Object key) {
        for (int i = 0; i < items.size(); i++) if (key.equals(items.get(i).key)) return i;
        return -1;
    }

    private static int adapterFirst(List<Item> items) {
        for (int i = 0; i < items.size(); i++) if (!items.get(i).header && !items.get(i).note) return i;
        return 0;
    }

    /** Replaces a screen's rows; if it is on screen, keeps the focused row by key (data never moves focus). */
    private void patchItems(Screen s, List<Item> items) {
        Object key = null;
        int top = Integer.MIN_VALUE;
        if (s.selection >= 0 && s.selection < s.items.size()) {
            key = s.items.get(s.selection).key;
            View v = shown == s ? list.getSelectedView() : null;
            if (v != null) top = v.getTop();
        }
        boolean same = s.items.size() == items.size();
        for (int i = 0; same && i < items.size(); i++) {
            Object a = s.items.get(i).key, b = items.get(i).key;
            same = a == null ? b == null : a.equals(b);
        }
        if (!same) listChanged = true;
        s.items = items;
        listTall = tall(items);
        listLane = lane(items);
        listLogo = sheetLogos(s, items);
        listIndent = indent(s, items, listLogo);
        int idx = -1;
        if (key != null) for (int i = 0; i < items.size(); i++) if (key.equals(items.get(i).key)) idx = i;
        if (shown != s) {
            if (idx >= 0) s.selection = idx;
            return;
        }
        adapter.setItems(items);
        if (list.hasFocus()) {
            int sel = idx >= 0 ? idx : adapter.firstSelectable();
            if (sel >= 0) {
                s.selection = sel;
                if (top != Integer.MIN_VALUE) list.setSelectionFromTop(sel, top);
                else list.setSelection(sel);
            }
        } else {
            s.selection = idx;
            list.setSelection(Math.max(0, adapter.firstSelectable()));
        }
        updateHint();
    }

    /** Saves the focused row and its position of the screen on display, before something replaces it. */
    private void remember() {
        Screen top = stack.peek();
        if (top == null || top != shown) return;
        int pos = list.getSelectedItemPosition();
        if (pos >= 0) top.selection = pos;
        View v = list.getSelectedView();
        if (v != null) top.selTop = v.getTop();
    }

    /**
     * Puts a screen on the Serie detail's stack. The detail is the browser's one level: a new detail
     * replaces the one shown (a Serie opened from another Serie's row).
     */
    private void push(Screen s) {
        remember();
        stack.push(s);
        osd.hideNow(); // one overlay at a time: the detail replaces the OSD
        hideBrowserViews(); // and the browser's lens goes under it
        detail.setVisibility(View.VISIBLE);
        render();
    }

    /** The browser's views (tabs, catalogue, keyboard) off screen while the detail is up. */
    private void hideBrowserViews() {
        tabs.setVisibility(View.GONE);
        catalogue.setVisibility(View.GONE);
        keyboard.setVisibility(View.GONE);
    }

    /** The browser's lens back on screen (Back from the detail). */
    private void showBrowserViews() {
        tabs.setVisibility(View.VISIBLE);
        if (tabs.active() == TabsView.TABLA) {
            catalogue.setVisibility(View.GONE);
            keyboard.setVisibility(View.GONE);
            if (!gridOpen) openGrid();
        } else {
            catalogue.setVisibility(View.VISIBLE);
            rebuildCatalogue();
            focusShelves();
        }
    }

    private void render() {
        Screen s = stack.peek();
        if (s == null) return;
        shown = s;

        // The Serie detail: 58 % of the width over the video, its key art behind it (F16).
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) detail.getLayoutParams();
        int w = Theme.panelWidth(this);
        if (lp.width != w) {
            lp.width = w;
            detail.setLayoutParams(lp);
        }
        detail.setBackgroundColor(0); // the hero's scrim instead
        hero.setVisibility(View.VISIBLE);
        hero.show(s.heroUrl, images.get(s.heroUrl), s.heroColor, w);
        int innerW = w - detail.getPaddingLeft() - detail.getPaddingRight();

        // A Serie's header is its title alone, the hero band's first line (B5); the eyebrow stays reserved.
        Ui.setHeader(detailEyebrow, detailTitle, s.eyebrow, s.title);
        detailEyebrow.setVisibility(View.GONE);
        detailTitle.setVisibility(View.VISIBLE);
        detailTitle.setPadding(detailTitle.getPaddingLeft(), 0, detailTitle.getPaddingRight(), Theme.dp(this, Theme.SP_XS));
        detailTitle.setCompoundDrawables(null, null, null, null);
        listTall = tall(s.items);
        listLane = lane(s.items);
        listLogo = sheetLogos(s, s.items);
        listIndent = indent(s, s.items, listLogo);
        adapter.setItems(s.items);
        int sel = s.selection;
        if (sel < 0 || sel >= s.items.size() || !adapter.isEnabled(sel)) sel = Math.max(0, adapter.firstSelectable());
        s.selection = sel;
        int top = s.selTop != Integer.MIN_VALUE ? s.selTop : wholeRowsAbove(s.items, sel, centreOffset());
        list.setSelectionFromTop(sel, top);
        list.requestFocus();
        updateHint();
    }

    private static boolean tall(List<Item> items) {
        for (Item it : items) if (!it.header && !it.note && !it.info && it.pills == null && it.sub != null) return true;
        return false;
    }

    private static boolean lane(List<Item> items) {
        for (Item it : items) if (!it.header && !it.note && it.progress >= 0) return true;
        return false;
    }

    /** A sheet with a logo on any row keeps the slot on every row: one left edge (M8). */
    private static boolean logoColumn(List<Item> items) {
        for (Item it : items) if (!it.header && !it.note && it.channel != null) return true;
        return false;
    }

    /**
     * A sheet's 56 x 32 dp logo column: on every row when any row has a logo, and on every sheet about a
     * channel (its header has the logo), so the titles of all its variants share one x. List rows keep
     * the 80 x 45 dp slot (X1: the fallback name must fit it).
     */
    private static boolean sheetLogos(Screen s, List<Item> items) {
        return s.kind == SHEET && logoColumn(items);
    }

    /**
     * The list's shared lead column: the widest logo or thumbnail slot of any row, so every row's text
     * starts at one x whatever the row carries (Y3). 0 when no row has a slot.
     */
    private int indent(Screen s, List<Item> items, boolean sheetLogos) {
        if (s.kind == SEARCH) return Theme.dp(this, Theme.RESULT_W); // Sök: the thumbnail column in every state, before any result lands (M-c)
        int w = 0;
        for (Item it : items) {
            if (it.header || it.note || it.info || it.pills != null || it.spacerPx > 0) continue;
            if (it.bigThumb) w = Math.max(w, Theme.THUMB_W);
            else if (it.image != null || it.thumbSlot) w = Math.max(w, Theme.RESULT_W);
            else if (it.channel != null || sheetLogos) w = Math.max(w, sheetLogos ? Theme.HEADER_LOGO_W : Theme.LOGO_W);
        }
        return Theme.dp(this, w);
    }

    /** Height of row i of a list whose rows are `tall`; notes take no room in the scroll arithmetic. */
    private int rowH(Item it, int i, boolean tall) {
        if (it.header) return Theme.dp(this, i == 0 ? Theme.ROW_H_HEADER_FIRST : Theme.ROW_H_HEADER);
        if (it.compact) return Theme.dp(this, Theme.ROW_H_ONE);
        if (it.info || it.pills != null || it.spacerPx > 0 || it.bigThumb || it.note) return rowHeight(it);
        return Theme.dp(this, tall ? Theme.ROW_H_TWO : Theme.ROW_H_ONE);
    }

    /** Toasts while the browser is open go to the tab row's line, never over its cards (M9). */
    private void notice(String text, int kind, int ms) {
        if (browserOpen) {
            showTransientHint(text);
            ui.postDelayed(() -> {
                if (text.equals(transientHint)) {
                    transientHint = null;
                    updateHint();
                }
            }, ms);
            return;
        }
        snack.show(text, kind, ms);
    }

    /**
     * The offset that puts whole rows above the selected one, as close to {@code target} as possible:
     * the list never starts with a row cut by its top edge (ListView closes any gap above the first row).
     */
    private int wholeRowsAbove(List<Item> items, int sel, int target) {
        int y = 0;
        for (int i = sel - 1; i >= 0; i--) {
            Item it = items.get(i);
            int h = rowH(it, i, listTall);
            if (y + h > target) break;
            y += h;
        }
        return y;
    }

    /** The height of one row by its kind (never by its text): header, info, pills, episode, plain. */
    private int rowHeight(Item it) {
        if (it.header) return Theme.dp(this, Theme.ROW_H_HEADER);
        if (it.info) {
            if (infoProbe == null) infoProbe = new RowView(this);
            return infoProbe.infoHeight();
        }
        if (it.pills != null) return Theme.dp(this, Theme.ROW_H_PILLS);
        if (it.spacerPx > 0) return it.spacerPx;
        if (it.bigThumb) return Theme.dp(this, Theme.ROW_H_THUMB);
        if (it.note) {
            if (infoProbe == null) infoProbe = new RowView(this);
            return infoProbe.noteHeight();
        }
        return Theme.dp(this, listTall ? Theme.ROW_H_TWO : Theme.ROW_H_ONE);
    }

    private RowView infoProbe;

    /** First open of Kanaler and Tablå: the default row in the middle of the list. */
    private int centreOffset() {
        int h = list.getHeight();
        if (h <= 0) h = getResources().getDisplayMetrics().heightPixels - Theme.dp(this, 150);
        return Math.max(0, h / 2 - Theme.dp(this, Theme.ROW_H_TWO) / 2);
    }

    /**
     * The footer line of what has the focus: the Serie detail's rows, the catalogue lens's shelves and
     * field, or the in-app keyboard's key under focus. The line is drawn by the detail, the catalogue's
     * own hint, or nothing on video (the strip carries its own hints).
     */
    private void updateHint() {
        if (detail.getVisibility() == View.VISIBLE) {
            Screen s = stack.peek();
            if (s == null) return;
            String[] pairs;
            if (transientHint != null) {
                pairs = new String[] {"", transientHint};
            } else if (adapter.firstSelectable() < 0) {
                // Nothing to select (loading, a note): only the explanation the first row carries, never "OK".
                Item first = s.items.isEmpty() ? null : s.items.get(0);
                pairs = first != null && first.hint != null && first.hint.length > 1 && first.hint[0].isEmpty() ? first.hint : new String[0];
            } else {
                int pos = list.getSelectedItemPosition();
                Item it = pos >= 0 && pos < s.items.size() ? s.items.get(pos) : null;
                pairs = it != null && it.hint != null ? it.hint : s.hint;
                if (it != null && it.onLong != null && !java.util.Arrays.asList(pairs).contains("Håll OK")) {
                    String[] more = java.util.Arrays.copyOf(pairs, pairs.length + 2); // C3: say what held OK does
                    more[pairs.length] = "Håll OK";
                    more[pairs.length + 1] = "Mer";
                    pairs = more;
                }
            }
            String[] all = new String[pairs.length + 2];
            System.arraycopy(pairs, 0, all, 0, pairs.length);
            all[pairs.length] = Theme.BACK;
            all[pairs.length + 1] = "Tillbaka";
            lastHintPairs = all;
            detailHint.setText(Ui.hints(all));
            return;
        }
        if (catalogueOpen) {
            String[] pairs;
            if (transientHint != null) {
                pairs = new String[] {"", transientHint};
            } else if (tabs.hasFocus()) {
                pairs = new String[] {"← →", "Byt vy", "↓", "Gå in", Theme.BACK, "Stäng"};
            } else if (keyboard.hasFocus()) {
                String[] ok = keyHint(keyboard.focusedKey(), query.isEmpty(), keyboard.voiceAvailable());
                pairs = new String[] {ok.length > 1 ? ok[0] : "", ok.length > 1 ? ok[1] : "",
                        "→", catalogue.shelfCount() > 0 ? "Resultat" : "Inga kort", Theme.BACK, "Dölj tangentbord"};
            } else if (searchFieldFocus && catalogue.searchLayout()) {
                pairs = new String[] {"↓", "Tangentbord", "→", "Resultat", Theme.BACK, "Dölj tangentbord"};
            } else if (searchFieldFocus) {
                pairs = new String[] {"OK", "Skriv", "↓", "Katalog", Theme.BACK, "Flikar"};
            } else if (catalogue.focusedCard() != null) {
                pairs = catalogue.searchLayout()
                        ? new String[] {"OK", "Spela", "←", "Tangentbord", Theme.BACK, "Dölj tangentbord"}
                        : new String[] {"OK", "Spela", "↑", "Sök", Theme.BACK, "Flikar"};
            } else {
                pairs = new String[] {"OK", "Välj", Theme.BACK, "Stäng"};
            }
            lastHintPairs = pairs;
            catalogue.setHint(Ui.hints(pairs));
            return;
        }
        lastHintPairs = new String[0];
    }

    /**
     * Hints while a key has focus (C3, Y4, Y5): what OK does on this key and "→" named after what the
     * list column holds. Only key–action pairs: a column with nothing to select explains itself in its
     * note row, never in the footer.
     */
    private String[] keyboardHints(Screen s) {
        List<String> out = new ArrayList<>();
        String[] ok = keyHint(keyboard.focusedKey(), query.isEmpty(), keyboard.voiceAvailable());
        Collections.addAll(out, ok);
        int first = adapter.firstSelectable();
        if (first >= 0) {
            Object key = s.items.get(first).key;
            String ks = key instanceof String ? (String) key : "";
            out.add("→");
            out.add(ks.startsWith("sug:") ? "Förslag" : ks.startsWith("rec:") ? "Senaste"
                    : ks.equals("r:error") ? "Försök igen" : "Resultat");
        }
        return out.toArray(new String[0]);
    }

    /**
     * The OK pair for the focused key (UX.md 6): letters "OK Skriv", "OK Mellanslag", "OK Radera" and
     * "OK Rensa" (nothing while there is nothing to erase), Röst "OK Tala", or the no-recognizer text
     * when the key is dim. {@code KeyboardHintTest} walks every key.
     */
    static String[] keyHint(String key, boolean queryEmpty, boolean voice) {
        switch (key) {
            case "Röst": return voice ? new String[] {"OK", "Tala"} : new String[] {"", "Röstsökning finns inte på den här enheten"};
            case "Mellanslag": return new String[] {"OK", "Mellanslag"};
            case "Radera": return queryEmpty ? new String[0] : new String[] {"OK", "Radera"};
            case "Rensa": return queryEmpty ? new String[0] : new String[] {"OK", "Rensa"};
            default: return new String[] {"OK", "Skriv"};
        }
    }

    /** The footer's hint model as set last (harness: C3, Y5). */
    String[] hintPairsForTest() {
        return lastHintPairs == null ? new String[0] : lastHintPairs.clone();
    }

    private String[] lastHintPairs;

    /** Test hook: the titles and secondary lines of the rows on the Serie detail (R5, X4). */
    List<String> rowTextsForTest() {
        List<String> out = new ArrayList<>();
        if (shown == null || detail.getVisibility() != View.VISIBLE) return out;
        for (Item it : shown.items) out.add(it.label + (it.sub == null ? "" : " / " + it.sub));
        return out;
    }

    /**
     * Test hook (B1): null unless a rail destination's root without a logo shows (Kanaler, Sök, Min lista,
     * Inställningar); then the header text on screen, "" when the header is gone. Clean room: the rail is
     * gone, so the detail's header is its title alone (never a section header).
     */
    String sectionHeaderForTest() {
        return null;
    }

    /** Test hook (B2): rows whose chevron promises something the row cannot do; a channel row of Kanaler has no chevron at all (nothing opens from it). */
    List<String> chevronViolationsForTest() {
        List<String> out = new ArrayList<>();
        if (shown == null || detail.getVisibility() != View.VISIBLE) return out;
        for (Item it : shown.items) {
            boolean chevron = it.trailing == RowView.TRAIL_CHEVRON;
            if (chevron && it.onRight == null && it.onSelect == null) out.add("chevron without an action: " + it.label);
        }
        return out;
    }

    /** Test hook (U11): a scrub has steps taken that no seek has committed yet. */
    boolean seekPendingForTest() {
        return seekTarget != C.TIME_UNSET || cursorMs != C.TIME_UNSET;
    }

    /** Test hook (programme-boundary jumps): commits the pending seek now, as OK on the bar does. */
    void commitSeekForTest() {
        ui.removeCallbacks(commitSeek);
        commitSeek();
    }

    /** Test hooks for the boundary jumps (BoundaryJumpTest): the player the app uses, and the play facts. */
    void playerForTest(androidx.media3.common.Player p) {
        try {
            java.lang.reflect.Field f = MainActivity.class.getDeclaredField("player");
            f.setAccessible(true);
            f.set(this, p);
        } catch (Exception e) {
            throw new AssertionError("no player field", e);
        }
    }

    void releasePlayerForTest() {
        if (player != null) player.release();
        player = null;
    }

    void scrubForTest(long deltaMs) {
        scrub(deltaMs);
    }

    boolean cancelSeekForTest() {
        return cancelSeek();
    }

    /** Test hook (BoundaryJumpTest): where the stream that was just started begins, in its own position. */
    long playingResumeForTest() {
        return playingResumeMs;
    }

    String playingTitleForTest() {
        return playingTitle;
    }

    String delayedChannelNameForTest() {
        return delayedChannel == null ? null : delayedChannel.name;
    }

    Api.Program delayedProgrammeForTest() {
        return delayedProgramme;
    }

    long playPositionForTest() {
        return player.getCurrentPosition();
    }

    String playingChannelIdForTest() {
        return current >= 0 ? channels.get(current).id : null;
    }

    List<Api.Program> cachedGuideForTest(String channelId) {
        return cachedGuide(channelId);
    }

    /** Plays a programme's catch-up the way Tablå's OK does. */
    void playCatchupForTest(Api.Program p) {
        playFromPanel(p.catchup, null);
    }

    /** Test hook (BoundaryJumpTest): what STATE_READY does (the stub fires no listener callbacks). */
    void playerReadyForTest() {
        if (zapStartMs > 0) {
            zapStartMs = 0;
            if (pendingSeekMs != 0) {
                long d = pendingSeekMs;
                pendingSeekMs = 0;
                seek(d);
            }
        }
    }

    /** Test hook (blink): how often the overlay appeared or disappeared, and its alpha now. */
    int osdVisibilityChangesForTest() {
        return osd.visibilityChanges();
    }

    float osdAlphaForTest() {
        return osd.getAlpha();
    }

    /**
     * Test hook (B5): how many whole episode rows the Serie list holds in its viewport, counted from the first
     * episode row on screen to the bottom of the list (a season with fewer episodes leaves the room empty).
     */
    int episodeRowsThatFitForTest() {
        Screen s = shown;
        if (s == null || detail.getVisibility() != View.VISIBLE) return -1;
        for (int i = 0; i < list.getChildCount(); i++) {
            int pos = list.getFirstVisiblePosition() + i;
            if (pos < s.items.size() && s.items.get(pos).bigThumb) {
                int top = Math.max(0, list.getChildAt(i).getTop());
                return (list.getHeight() - top) / Theme.dp(this, Theme.ROW_H_THUMB);
            }
        }
        return -1;
    }

    /** Test hook (B5): the hero band of the Serie screen in px, from the panel's content top to the bottom of the action pills; -1 when not shown. */
    int seriesBandForTest() {
        Screen s = shown;
        if (s == null || detail.getVisibility() != View.VISIBLE) return -1;
        int idx = indexOfKey(s.items, "actions");
        View c = idx < 0 ? null : list.getChildAt(idx - list.getFirstVisiblePosition());
        if (c == null) return -1;
        int[] loc = new int[2];
        list.getLocationInWindow(loc);
        return loc[1] + c.getBottom() - detail.getPaddingTop();
    }

    /** Test hook (Y7): the eyebrow of the picker on top, or null when the picker is closed. */
    String topSheetEyebrowForTest() {
        return pickerOpen ? picker.eyebrowForTest() : null;
    }

    /** The label of what Back lands on: the Serie detail's parent, else "Stäng". */
    private String backLabel() {
        if (detail.getVisibility() == View.VISIBLE && stack.size() > 1) {
            java.util.Iterator<Screen> it = stack.iterator();
            it.next();
            return it.next().name;
        }
        return "Stäng";
    }

    /** Back in the Serie detail: one level out, to the catalogue (which opened it). */
    private boolean back() {
        if (detail.getVisibility() != View.VISIBLE || stack.isEmpty()) {
            closeBrowser();
            return true;
        }
        if (stack.size() > 1) {
            Screen popped = stack.pop();
            render();
            if (popped.onBack != null) popped.onBack.run();
            return true;
        }
        Screen popped = stack.pop();
        closeDetail();
        if (browserOpen) showBrowserViews();
        if (popped.onBack != null) popped.onBack.run();
        return true;
    }

    private void toggleVideoMode() {
        int m = video.mode() == VideoFrame.CONTAIN ? VideoFrame.COVER : VideoFrame.CONTAIN;
        video.setMode(m);
        getPreferences(MODE_PRIVATE).edit().putInt("videoMode", m).apply();
    }

    private String videoModeLabel() {
        return video.mode() == VideoFrame.CONTAIN ? "Hela bilden" : "Fyll skärmen";
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static float progress(Api.Program p, long now) {
        if (p.endMs <= p.startMs) return -1;
        return Math.max(0f, Math.min(1f, (now - p.startMs) / (float) (p.endMs - p.startMs)));
    }

    // ---- Keys ----------------------------------------------------------------------------

    private static boolean isOk(int k) {
        return k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        lastKeyMs = android.os.SystemClock.uptimeMillis(); // Nästa avsnitt stops auto-playing after 90 min idle
        if (down && e.getRepeatCount() == 0 && transientHint != null) {
            transientHint = null;
            updateHint();
        }

        // Back: short on release, held = close everything and go live (clean room 3).
        if (k == KeyEvent.KEYCODE_BACK) {
            if (down) {
                if (e.getRepeatCount() == 0) {
                    backDown = true;
                    backLongDone = false;
                    ui.postDelayed(backLong, ViewConfiguration.getLongPressTimeout());
                }
                return true;
            }
            if (e.getAction() == KeyEvent.ACTION_UP) {
                ui.removeCallbacks(backLong);
                boolean was = backDown;
                backDown = false;
                if (was && !backLongDone && !e.isCanceled() && !shortBack()) onBackPressed();
                return true;
            }
            return true;
        }

        // Surface 3: the picker has the keys while it is up (↑ ↓ the rows, ← → a value, OK acts, Back closes).
        if (pickerOpen) {
            if (!down) return true;
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_UP: movePicker(-1); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: movePicker(1); return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (picker.row(picker.focus()) != null && picker.row(picker.focus()).kind == PickerView.Row.VALUE) stepPicker(picker.row(picker.focus()), -1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (picker.row(picker.focus()) != null && picker.row(picker.focus()).kind == PickerView.Row.VALUE) stepPicker(picker.row(picker.focus()), 1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (e.getRepeatCount() == 0) runPicker();
                    return true;
                case KeyEvent.KEYCODE_MENU:
                case KeyEvent.KEYCODE_GUIDE:
                    closePicker();
                    return true;
                default:
                    return true;
            }
        }

        // The browser's tab row, over either lens: ← → change the lens in place, ↓ enters the active one.
        if (browserOpen && tabs.hasFocus()) {
            if (!down) return true;
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_LEFT: tabs.setActive(TabsView.TABLA); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT: tabs.setActive(TabsView.KATALOG); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (tabs.active() == TabsView.KATALOG) focusField();
                    else if (gridOpen) grid.requestFocus();
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (tabs.active() == TabsView.KATALOG) focusField();
                    else if (gridOpen) { // OK on the Tablå tab: back to now, on the channel that plays
                        grid.goNow(playingChannel(), Clock.now());
                        grid.requestFocus();
                    }
                    return true;
                case KeyEvent.KEYCODE_MENU:
                case KeyEvent.KEYCODE_BACK:
                    closeBrowser(); // Menu and Back close the browser
                    return true;
                case KeyEvent.KEYCODE_GUIDE:
                    tabs.setActive(TabsView.TABLA);
                    if (!gridOpen) openGrid();
                    else grid.requestFocus();
                    return true;
                default:
                    return true;
            }
        }

        // Surface 2, Tablå lens: the guide grid has the keys while it is up.
        if (gridOpen) {
            if (isOk(k)) {
                if (down) {
                    if (e.getRepeatCount() == 0) {
                        gridOkDown = true;
                        gridHoldDone = false;
                        ui.postDelayed(gridHold, ViewConfiguration.getLongPressTimeout());
                    }
                } else if (e.getAction() == KeyEvent.ACTION_UP) {
                    ui.removeCallbacks(gridHold);
                    boolean was = gridOkDown && !gridHoldDone;
                    gridOkDown = false;
                    if (was) grid.ok();
                }
                return true;
            }
            if (!down) return super.dispatchKeyEvent(e);
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (!grid.moveRow(-1)) tabs.requestFocus(); // ↑ from the top row: the tab row, to the other lens
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: grid.moveRow(1); return true;
                case KeyEvent.KEYCODE_DPAD_LEFT: grid.moveProgramme(-1); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT: grid.moveProgramme(1); return true;
                case KeyEvent.KEYCODE_CHANNEL_UP: grid.moveRow(-5); return true; // CH ±: five rows
                case KeyEvent.KEYCODE_CHANNEL_DOWN: grid.moveRow(5); return true;
                case KeyEvent.KEYCODE_MENU:
                    closeBrowser(); // Menu toggles: it opened the browser, it closes it
                    return true;
                case KeyEvent.KEYCODE_GUIDE:
                    openBrowser(TabsView.TABLA);
                    return true;
                case KeyEvent.KEYCODE_SEARCH:
                    openBrowser(TabsView.KATALOG);
                    focusField();
                    startVoice();
                    return true;
                default:
                    return super.dispatchKeyEvent(e);
            }
        }

        // Surface 2, Katalog & Sök lens: the tab row, the shelves, the field and the in-app keyboard.
        if (catalogueOpen) {
            if (!down) return super.dispatchKeyEvent(e);
            if (k == KeyEvent.KEYCODE_GUIDE) { // Guide always shows Tablå
                openBrowser(TabsView.TABLA);
                return true;
            }
            if (k == KeyEvent.KEYCODE_MENU) { // Menu toggles the browser
                closeBrowser();
                return true;
            }
            if (k == KeyEvent.KEYCODE_SEARCH) { // the Search key puts the query field in reach
                focusField();
                startVoice();
                return true;
            }
            if (keyboard.hasFocus()) { // the keyboard handles its own keys (→ out of its last column: the results)
                if (k == KeyEvent.KEYCODE_DPAD_UP && keyboard.focusedKeyRow() == 0) { // ↑ from the action row: the field above it
                    focusField();
                    return true;
                }
                if (k == KeyEvent.KEYCODE_DPAD_LEFT && keyboard.focusedKeyColumn() == 0) return true; // nothing left of the keys
                return super.dispatchKeyEvent(e);
            }
            if (catalogue.hasFocus() && searchFieldFocus) {
                switch (k) {
                    case KeyEvent.KEYCODE_DPAD_UP: tabs.requestFocus(); return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        if (catalogue.searchLayout()) focusKeyboard(); // the keys are under the field
                        else focusShelves(); // the cards are under the field
                        return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if (catalogue.searchLayout()) focusList();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        if (!catalogue.searchLayout()) tabs.requestFocus();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                    case KeyEvent.KEYCODE_NUMPAD_ENTER:
                        focusKeyboard();
                        return true;
                    case KeyEvent.KEYCODE_MENU:
                    case KeyEvent.KEYCODE_GUIDE:
                        closeBrowser();
                        return true;
                    default: return true;
                }
            }
            // The shelves have the focus.
            if (!catalogue.hasFocus()) return super.dispatchKeyEvent(e);
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_LEFT: {
                    // The edge of the first column: with the keyboard up, its column is at the left.
                    if (catalogue.move(-1, 0) == CatalogueView.EDGE_LEFT && catalogue.searchLayout()) focusKeyboard();
                    updateHint();
                    return true;
                }
                case KeyEvent.KEYCODE_DPAD_RIGHT: {
                    catalogue.move(1, 0);
                    updateHint();
                    return true;
                }
                case KeyEvent.KEYCODE_DPAD_UP: {
                    if (catalogue.move(0, -1) == CatalogueView.EDGE_UP) { // above the first row
                        if (catalogue.searchLayout()) tabs.requestFocus(); // the field is at the left, over the keys
                        else focusField(); // the field is the row above the cards
                    }
                    updateHint();
                    return true;
                }
                case KeyEvent.KEYCODE_DPAD_DOWN: {
                    catalogue.move(0, 1);
                    updateHint();
                    return true;
                }
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (e.getRepeatCount() == 0) openCard(catalogue.focusedCard());
                    return true;
                default:
                    return super.dispatchKeyEvent(e);
            }
        }

        // The Serie detail: its list, its pill rows and held OK (one level below the catalogue).
        if (detail.getVisibility() == View.VISIBLE) {
            if (!down) return super.dispatchKeyEvent(e);
            Screen s = stack.peek();
            int pos = list.getSelectedItemPosition();
            Item it = s != null && pos >= 0 && pos < s.items.size() ? s.items.get(pos) : null;
            if (it != null && it.pills != null) {
                boolean leftEdge = k == KeyEvent.KEYCODE_DPAD_LEFT && it.pillFocus == 0;
                if ((k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) && !leftEdge) {
                    s.selection = pos;
                    movePill(it, k == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                    return true;
                }
                if (k == KeyEvent.KEYCODE_DPAD_DOWN && it.downKey != null && focusDown(it)) return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                Runnable r = it == null ? null : it.onRight != null ? it.onRight
                        : it.trailing == RowView.TRAIL_CHEVRON ? it.onSelect : null;
                if (r != null) {
                    s.selection = pos;
                    r.run();
                }
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) { // out of the detail, back to the catalogue
                back();
                return true;
            }
            return super.dispatchKeyEvent(e);
        }

        // OK on video acts on release, so the key-up never lands in a surface the key-down opened.
        if (isOk(k)) {
            if (down) {
                if (e.getRepeatCount() == 0) okDown = true;
                return true;
            }
            if (e.getAction() == KeyEvent.ACTION_UP) {
                boolean was = okDown;
                okDown = false;
                if (was) shortOk();
                return true;
            }
            return true;
        }

        if (!down) return super.dispatchKeyEvent(e);

        // The strip has the focus (U11): on the action row ← → move between the pills, while scrubbing they
        // scrub on (below); ↑ ↓ never zap.
        if (osd.focusRow() == OsdView.FOCUS_ACTIONS && (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            moveAction(k == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            return true;
        }
        if (osd.focusRow() != OsdView.FOCUS_NONE && (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN)) {
            moveRow(k == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1);
            return true;
        }

        if (k == KeyEvent.KEYCODE_SEARCH) {
            openBrowser(TabsView.KATALOG);
            focusField();
            startVoice();
            return true;
        }
        if (k == KeyEvent.KEYCODE_MENU) { // Menu is a toggle: it opens the browser on Tablå and closes it again
            openBrowser(TabsView.TABLA);
            return true;
        }
        if (k == KeyEvent.KEYCODE_GUIDE) { // Guide opens the browser on Tablå
            openBrowser(TabsView.TABLA);
            return true;
        }
        if (k == KeyEvent.KEYCODE_LAST_CHANNEL && previous >= 0) {
            tune(previous, true);
            return true;
        }
        if (!channels.isEmpty() && (k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || k == KeyEvent.KEYCODE_MEDIA_PAUSE
                || k == KeyEvent.KEYCODE_MEDIA_PLAY)) {
            togglePause(); // the media keys are the Paus pill (F18 on live too)
            return true;
        }

        if (channels.isEmpty()) return super.dispatchKeyEvent(e);
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                zap(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                zap(1);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                restartCurrentProgramme();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                scrub(-seekStep(e));
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                scrub(seekStep(e));
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    private void shortOk() {
        if (channels.isEmpty()) {
            if (startFailed) retryStart();
            return;
        }
        // OK confirms what is going on, and the hint says which (U0): while scrubbing it plays from the
        // playhead at once, on the pills it performs the focused one, else it opens the pills (first pill).
        switch (osd.focusRow()) {
            case OsdView.FOCUS_ACTIONS: runAction(); break;
            case OsdView.FOCUS_BAR:
                ui.removeCallbacks(commitSeek);
                commitSeek();
                break;
            default: showOverlay(OsdView.FOCUS_ACTIONS); break;
        }
    }

    /**
     * ← → on video: a scrub on the light overlay (the bar and its numbers, no pills, no peek). The playhead
     * moves with every step; the picture seeks when the keys stop ({@link #COMMIT_MS}) or at once on OK, and
     * Back cancels (U11). With nothing to seek (a stream that is loading, a live buffer without start-over)
     * the overlay just shows its frame.
     */
    /** One line of what the scrub and the stream are doing now (TeliaLite log; safe without a player). */
    private String scrubState() {
        String pos = "-", ws = "-";
        if (player != null) {
            long d = player.getDuration();
            pos = player.getCurrentPosition() + "/" + (d == C.TIME_UNSET ? "?" : d) + (player.isCurrentMediaItemDynamic() ? " dyn" : " static");
            if (!player.getCurrentTimeline().isEmpty()) ws = new java.util.Date(windowStartMs()).toString();
        }
        return "live=" + playingLive + " startOver=" + playingStartOver + " zapMs=" + (zapStartMs > 0 ? Clock.now() - zapStartMs : 0)
                + " pos=" + pos + " ws=" + ws + " cursor=" + (cursorMs == C.TIME_UNSET ? "-" : new java.util.Date(cursorMs).toString())
                + " target=" + (seekTarget == C.TIME_UNSET ? "-" : String.valueOf(seekTarget)) + " loadHead=" + (loadHeadMs == C.TIME_UNSET ? "-" : new java.util.Date(loadHeadMs).toString())
                + " soProg=" + (startOverProgramme == null ? "-" : startOverProgramme.title + "@" + clock(startOverProgramme.startMs))
                + " soAt=" + (startOverAtMs == C.TIME_UNSET ? "-" : new java.util.Date(startOverAtMs).toString());
    }

    private void scrub(long deltaMs) {
        Log.i(TAG, "scrub " + deltaMs + " ms: " + scrubState());
        seek(deltaMs);
        if (osd.getVisibility() != View.VISIBLE) renderFrame();
        boolean pending = seekTarget != C.TIME_UNSET || cursorMs != C.TIME_UNSET;
        if (pending && osd.focusRow() != OsdView.FOCUS_BAR) osd.focusRow(OsdView.FOCUS_BAR); // from the pills the bar already has it
        flashOverlay();
    }

    /** The scrub is over (committed): the light overlay stays its usual time, with the usual hint. */
    private void endScrub() {
        Log.i(TAG, "endScrub: row=" + osd.focusRow() + " pillsOnBar=" + osd.pillsOnBar());
        if (osd.focusRow() != OsdView.FOCUS_BAR) return;
        osd.focusRow(osd.pillsOnBar() ? OsdView.FOCUS_ACTIONS : OsdView.FOCUS_NONE); // from the pills: back to them
        flashOverlay();
    }

    /**
     * Short Back. In the panel one step out ({@link #back}); on video it only hides what is on screen
     * (the controls, a previewed seek, the banner) and never moves playback (F12c: Back never jumps to
     * live; → to the live edge is the way back). Returns false when nothing is on screen: the system
     * then leaves the app.
     */
    private boolean shortBack() {
        if (pickerOpen) {
            closePicker();
            return true;
        }
        if (detail.getVisibility() == View.VISIBLE) {
            back();
            return true;
        }
        if (browserOpen && catalogueOpen && keyboard.getVisibility() == View.VISIBLE) { // Back puts the keys away: browse again
            showKeyboard(false);
            if (catalogue.shelfCount() > 0) focusShelves();
            else focusField();
            updateHint();
            return true;
        }
        if (browserOpen && !tabs.hasFocus()) { // Back steps out to the tab row first: one press, from any row
            tabs.requestFocus();
            updateHint();
            return true;
        }
        if (browserOpen) { // Back closes the browser, to the video; it never leaves the app from here
            closeBrowser();
            backClosedAtMs = android.os.SystemClock.uptimeMillis();
            return true;
        }
        if (osd.focusRow() != OsdView.FOCUS_NONE) { // the strip is one step: Back hides it, playback stays where it is
            cancelSeek(); // a scrub in progress: the playhead goes back, nothing seeks
            hideControls();
            return true;
        }
        if (cancelSeek()) { // a previewed seek: drop it, stay where playback is
            if (playingLive) showChannelOsd(channels.get(current));
            else osd.hideNow();
            return true;
        }
        if (osd.getVisibility() == View.VISIBLE) { // the strip is up: hide it
            osd.hideNow();
            return true;
        }
        // Clean video: Back opens the browser in one press (clean room 3). A second Back soon after the
        // browser closed leaves the app (the double-Back), so the app is never a trap.
        if (android.os.SystemClock.uptimeMillis() - backClosedAtMs < DOUBLE_BACK_MS) return false;
        openBrowser(TabsView.TABLA);
        return true;
    }

    /** When Back last closed the browser; a second Back within this window leaves the app (clean room 3). */
    private static final long DOUBLE_BACK_MS = 700;
    private long backClosedAtMs = -DOUBLE_BACK_MS;

    /** Test hook (U11): the overlay's row as drawn, in order. */
    List<String> pillsForTest() {
        return java.util.Arrays.asList(osd.shownActions());
    }

    /** Test hook (A5): "contain" or "cover" when the picture and the saved setting agree on it, else "unsaved". */
    String videoModeForTest() {
        int saved = getPreferences(MODE_PRIVATE).getInt("videoMode", VideoFrame.CONTAIN);
        if (saved != video.mode()) return "unsaved";
        return saved == VideoFrame.CONTAIN ? "contain" : "cover";
    }

    /** Test hook (A4): the rail's peek is on screen. */
    boolean peekShownForTest() {
        return false; // clean room: the rail's peek is gone with the rail
    }

    /** Test hook (U11): "none", "bar" or "actions": where the overlay's focus is. */
    String osdFocusForTest() {
        switch (osd.focusRow()) {
            case OsdView.FOCUS_BAR: return "bar";
            case OsdView.FOCUS_ACTIONS: return "actions";
            default: return "none";
        }
    }

    /** Test hook (U11): the label of the focused pill, or null. */
    String focusedPillForTest() {
        int a = osd.focusedAction();
        String[] p = osd.shownActions();
        return a >= 0 && a < p.length ? p[a] : null;
    }

    /** Test hook (F18): the player is paused (a pause on live is a paused start-over session). */
    boolean pausedForTest() {
        return paused;
    }

    /** Test hook (F18): the broadcast moment live shows now, to compare with where a pause landed. */
    long liveMomentForTest() {
        return liveNowMs();
    }

    /** Test hook (F12c): "live", "delayed" (behind live on a channel: time-shift or the start-over stream) or "ondemand". */
    String playbackForTest() {
        if (playingStartOver || (playingLive && cursorMs != C.TIME_UNSET && cursorMs < liveNowMs() - OsdView.LIVE_SLACK_MS)) return "delayed";
        return playingLive ? "live" : "ondemand";
    }

    /** Held Back: close every surface (the browser, the detail, the picker, the controls, a previewed seek); on-demand playback returns to live. */
    private void longBack() {
        closePicker();
        closeDetail();
        closeBrowser();
        if (channels.isEmpty()) return;
        cancelSeek();
        hideControls();
        if (!playingLive && !playingStartOver) tune(current); // on-demand: live, no panel (U1); a rewound channel stays where it is
    }

    // ---- Seeking ---------------------------------------------------------------------------
    //
    // Three kinds of timeline (verified on the TV, 2026-10-15):
    // - Live channel: a dynamic window of 61 s (16 segments of 3.84 s) and no timeShiftBufferDepth. It is
    //   a live buffer, not history, so it is never seeked. Rewind goes into start-over instead.
    // - Start-over: the live manifest with &start=<broadcast start>&stop=<broadcast end>. Dynamic, window
    //   from the broadcast start (aligned down to a segment, up to 3.84 s early) to the live edge,
    //   timeShiftBufferDepth 8 h. Position 0 is the window start.
    // - Catch-up and VOD: static, 0 to the duration.
    // Steps accumulate on the OSD bar and commit as one seek 1 s after the last press (or at once on OK).

    /** The live offset ExoPlayer holds behind the live edge (see setTargetOffsetMs in play()). */
    private static final long LIVE_OFFSET_MS = 10_000;
    /** A dynamic window shorter than this is a live buffer, not a time-shift window. */
    private static final long TIMESHIFT_MIN_MS = 5 * 60_000;
    private static final int COMMIT_MS = 1000;
    /** After a viewer's boundary jump, the auto-continue holds off this long (they decide). */
    private static final long USER_JUMP_HOLD_MS = 20_000;

    private final androidx.media3.common.Timeline.Window window = new androidx.media3.common.Timeline.Window();
    /** A film or episode scrub: the pending target as a window position; C.TIME_UNSET when none is pending. */
    private long seekTarget = C.TIME_UNSET;
    /**
     * A channel scrub (live, start-over, catch-up): the cursor on the channel's one timeline, a broadcast
     * moment (epoch ms); C.TIME_UNSET when none is pending. ← → move only this. The stream that holds the
     * moment is chosen when the keys stop ({@link #commitCursor}), so the steps are never slowed by a load.
     */
    private long cursorMs = C.TIME_UNSET;
    /** The scrub has said "Går inte att spola vidare här" once; it does not repeat on every step. */
    private boolean edgeSaid;
    /**
     * The moment a stream the scrub just loaded was asked to start at. Until the player reports a position
     * near it, the bar keeps the head there: the stream starts at its own position 0 for a moment, and the
     * bar must not fall back with it (owner, 2026-10-18).
     */
    private long loadHeadMs = C.TIME_UNSET;
    private long loadHeadSinceMs;

    /** The head the bar draws: the player's moment, or the moment the load was asked for while the player is not there yet. */
    private long heldHead(long headMoment) {
        if (loadHeadMs == C.TIME_UNSET) return headMoment;
        if (Math.abs(headMoment - loadHeadMs) <= 3000 || Clock.now() - loadHeadSinceMs > 15_000) {
            loadHeadMs = C.TIME_UNSET; // the player has arrived (or never will: show what it says)
            return headMoment;
        }
        return loadHeadMs;
    }
    /** Steps taken while a stream loads; applied when it is ready. */
    private long pendingSeekMs;
    /** Start-over just requested: the broadcast moment to start at (epoch ms), fixed once the window is known. */
    private long startOverAtMs = C.TIME_UNSET;
    /** The programme of the current start-over session; null when unknown. */
    private Api.Program startOverProgramme;
    private final Runnable commitSeek = this::commitSeek;
    private final Runnable tick = this::tick;

    /**
     * ← → on video. A channel (live, start-over, catch-up) has one timeline in broadcast time: the steps
     * move a cursor along it, across programme edges, and the stream that holds the moment loads when the
     * keys stop ({@link #seekChannel}). A film or an episode seeks inside its own stream.
     */
    private void seek(long deltaMs) {
        if (boundaryChannel() != null && channelTimeline() && (player == null || zapStartMs == 0)) {
            seekChannel(deltaMs); // needs no player: the harness rewinds into start-over this way
            return;
        }
        if (player == null) return;
        if (zapStartMs > 0) { // the stream is still loading
            pendingSeekMs += deltaMs;
            return;
        }
        if (!player.isCurrentMediaItemSeekable() || player.getDuration() == C.TIME_UNSET) return;
        long dur = player.getDuration();
        long base = seekTarget != C.TIME_UNSET ? seekTarget : player.getCurrentPosition();
        long target = Math.max(0, Math.min(base + deltaMs, Math.max(0, dur - 1000)));
        seekTarget = target;
        showPosition(target, true);
        ui.removeCallbacks(commitSeek);
        ui.postDelayed(commitSeek, COMMIT_MS);
    }

    /** True when what plays is a channel's timeline: live, its start-over, or a programme of it from the guide. */
    private boolean channelTimeline() {
        return playingLive || playingStartOver || (delayedChannel != null && delayedProgramme != null);
    }

    /** Where playback is now, as a broadcast moment on the channel's timeline. */
    private long playheadMs() {
        if (player == null || zapStartMs > 0) return liveNowMs();
        if (playingLive && !timeShiftWindow()) return liveNowMs();
        if (player.isCurrentMediaItemDynamic()) {
            long ws = windowStartMs();
            return ws == C.TIME_UNSET ? liveNowMs() : ws + Math.min(player.getCurrentPosition(), livePosition());
        }
        return delayedProgramme != null ? delayedProgramme.startMs + player.getCurrentPosition() : liveNowMs();
    }

    /** The programme that holds this broadcast moment on {@code ch}, from the one guide cache (X3), else null. */
    private Api.Program programmeAt(Api.Channel ch, long t) {
        Api.Program on = onAirNow(ch);
        if (on != null && on.startMs <= t && t < on.endMs) return on;
        List<Api.Program> g = cachedGuide(ch.id);
        if (g != null) for (Api.Program p : g) if (p.startMs <= t && t < p.endMs) return p;
        return null;
    }

    /** Whether a stream exists for {@code t} inside {@code p}: the one playing, its catch-up, or its start-over. */
    private boolean playableAt(Api.Program p, long t) {
        long now = Clock.now();
        if (p.endMs <= now && p.catchup != null) return true;
        if (p.startMs <= now && hasStartOver(p)) return true;
        return streamHolds(p, t);
    }

    /** The stream that plays now holds this moment of {@code p} (a time-shift window, or the catch-up itself). */
    private boolean streamHolds(Api.Program p, long t) {
        if (player == null || zapStartMs > 0) return false;
        if (player.isCurrentMediaItemDynamic()) {
            long ws = windowStartMs();
            if (playingStartOver) {
                // The programme the session started from (a refetched guide holds another object for it), or any
                // moment inside the window: it opens a few seconds to a minute before the broadcast start, and a
                // scrub from there must seek in this stream, not load the catch-up of the programme before.
                if (startOverProgramme != null && p.startMs == startOverProgramme.startMs) return true;
                long dur = player.getDuration();
                return ws != C.TIME_UNSET && dur != C.TIME_UNSET && t >= ws && t <= ws + dur;
            }
            return ws != C.TIME_UNSET && t >= ws && t <= liveNowMs();
        }
        return delayedProgramme == p;
    }

    /**
     * One step along the channel's timeline. The cursor moves at once, past programme edges, and the strip
     * shows the programme it is in. A neighbour with nothing to play stops the cursor at the edge.
     */
    private void seekChannel(long deltaMs) {
        Api.Channel ch = boundaryChannel();
        if (ch == null) return;
        long live = liveNowMs();
        boolean atLive = cursorMs == C.TIME_UNSET && playingLive && !startOverOrBehind();
        if (deltaMs > 0 && atLive) { // already live: nothing to fast-forward to
            showChannelOsd(ch);
            return;
        }
        long from = cursorMs != C.TIME_UNSET ? cursorMs : playheadMs();
        long t = from + deltaMs;
        if (t >= live - OsdView.LIVE_SLACK_MS) {
            t = live; // within 15 s counts as live (the bar says Direkt)
        } else {
            Api.Program here = programmeAt(ch, from), there = programmeAt(ch, t);
            if (there == null && cachedGuide(ch.id) == null) { // the neighbour is not known yet: fetch it, press again
                ensureGuide(ch);
                prefetchOnAir(ch);
                if (cursorMs == C.TIME_UNSET) showChannelOsd(ch);
                osd.status("Hämtar tablån …", false);
                return;
            }
            if (here == null && there == null) {
                if (cursorMs == C.TIME_UNSET) showChannelOsd(ch);
                osd.status("Går inte att spola tillbaka", false);
                return;
            }
            if (there == null || !playableAt(there, t)) { // the neighbour has nothing to play: stop at the edge
                if (here == null || there == here) {
                    if (cursorMs == C.TIME_UNSET) showChannelOsd(ch);
                    osd.status("Går inte att spola tillbaka", false);
                    return;
                }
                long edge = deltaMs < 0 ? here.startMs : here.endMs - 1;
                if (!edgeSaid) osd.status("Går inte att spola vidare här", false);
                edgeSaid = true;
                if (edge == from) { // already at the edge
                    ui.removeCallbacks(commitSeek);
                    ui.postDelayed(commitSeek, COMMIT_MS);
                    return;
                }
                t = edge;
            }
        }
        cursorMs = t;
        Log.i(TAG, "seekChannel from=" + new java.util.Date(from) + " to=" + new java.util.Date(t) + " in " + (programmeAt(ch, t) == null ? "no programme" : programmeAt(ch, t).title));
        showCursorFrame(ch);
        ui.removeCallbacks(commitSeek);
        ui.postDelayed(commitSeek, COMMIT_MS);
    }

    /** Playing a start-over stream or a live stream held behind live: the picture is not at the live edge. */
    private boolean startOverOrBehind() {
        return playingStartOver || (playingLive && behindLiveMs() > OsdView.LIVE_SLACK_MS);
    }

    /** The strip for the cursor: the programme it is in, whole, with the live tick and the cursor on the one bar. */
    private void showCursorFrame(Api.Channel ch) {
        long live = liveNowMs();
        Api.Program p = programmeAt(ch, Math.min(cursorMs, live));
        if (p == null) {
            showChannelFrame(ch);
            return;
        }
        showTimelineFrame(ch, p.title, p.startMs, p.endMs, live, Math.min(cursorMs, live));
    }

    /** The title of the programme the cursor is in, or null: the harness reads it between the steps and the commit. */
    String cursorProgrammeForTest() {
        Api.Channel ch = boundaryChannel();
        if (cursorMs == C.TIME_UNSET || ch == null) return null;
        Api.Program p = programmeAt(ch, Math.min(cursorMs, liveNowMs()));
        return p == null ? null : p.title;
    }

    /** The player's duration of the programme playing, else the programme's EPG span. */
    private long durOf(Api.Program p) {
        return player != null && zapStartMs == 0 && player.getDuration() != C.TIME_UNSET ? player.getDuration() : p.endMs - p.startMs;
    }

    private void commitSeek() {
        Log.i(TAG, "commitSeek: " + scrubState());
        doCommitSeek();
        endScrub();
    }

    private void doCommitSeek() {
        edgeSaid = false;
        if (cursorMs != C.TIME_UNSET) {
            long t = cursorMs;
            cursorMs = C.TIME_UNSET;
            commitCursor(t);
            return;
        }
        if (player == null || seekTarget == C.TIME_UNSET) return;
        long t = seekTarget;
        seekTarget = C.TIME_UNSET;
        Log.i(TAG, "seekTo " + t);
        player.seekTo(t);
    }

    /**
     * The keys stopped: load what holds the cursor's moment. Inside the stream that plays, that is a seek.
     * Anywhere else it is the stream of the programme the moment is in: its catch-up, or its start-over.
     * Live, the cursor reached the edge: the live channel.
     */
    private void commitCursor(long t) {
        Api.Channel ch = boundaryChannel();
        if (ch == null) return;
        if (t >= liveNowMs() - OsdView.LIVE_SLACK_MS) { // the live edge
            if (playingStartOver && current >= 0) tune(current); // caught up with the broadcast
            else if (playingLive) {
                if (player != null && timeShiftWindow() && behindLiveMs() > OsdView.LIVE_SLACK_MS) player.seekToDefaultPosition();
            } else goLive();
            return;
        }
        Api.Program p = programmeAt(ch, t);
        if (p == null) return;
        Log.i(TAG, ("commitCursor t=" + new java.util.Date(t) + " in " + p.title + " (" + new java.util.Date(p.startMs)
                + ") startOver=" + playingStartOver + " ws=" + (player != null && !player.getCurrentTimeline().isEmpty() ? new java.util.Date(windowStartMs()) : "-")
                + " holds=" + streamHolds(p, t)));
        if (player != null && zapStartMs == 0) { // inside the stream that plays: a seek
            if (player.isCurrentMediaItemDynamic() && streamHolds(p, t)) {
                long ws = windowStartMs();
                if (ws != C.TIME_UNSET) {
                    player.seekTo(Math.max(0, Math.min(t - ws, livePosition())));
                    return;
                }
            } else if (!player.isCurrentMediaItemDynamic() && delayedProgramme == p) {
                player.seekTo(Math.max(0, Math.min(t - p.startMs, Math.max(0, player.getDuration() - 1000))));
                return;
            }
        }
        long now = Clock.now();
        userJumpAtMs = now; // the auto-continue holds off while the viewer decides (20 s)
        if (p.endMs <= now && p.catchup != null) {
            Log.i(TAG, "timeline: " + p.title + " from its catch-up at " + new java.util.Date(t));
            // A copy, so the position is this jump's and a later OK on the programme in Tablå starts clean.
            Api.Playable c = new Api.Playable();
            Api.Playable src = p.catchup;
            c.title = src.title;
            c.videoId = src.videoId;
            c.videoIdType = src.videoIdType;
            c.watchMode = src.watchMode;
            c.accessControl = src.accessControl;
            c.channel = src.channel;
            c.programme = src.programme;
            c.durationMs = src.durationMs;
            c.startPositionMs = Math.max(0, t - p.startMs);
            playFromPanel(c, null);
            loadHeadMs = t;
            loadHeadSinceMs = now;
        } else if (hasStartOver(p)) {
            Log.i(TAG, "timeline: " + p.title + " from its start-over at " + new java.util.Date(t));
            if (p.startMs <= now && now < p.endMs && delayedChannel == null) onAir.put(ch.id, p);
            playStartOver(p, t);
            loadHeadMs = t;
            loadHeadSinceMs = now;
        }
    }

    /** When the viewer last jumped across a programme boundary by hand; 0 = never. */
    private long userJumpAtMs;

    /** Drops a pending seek; true when there was one. */
    private boolean cancelSeek() {
        ui.removeCallbacks(commitSeek);
        boolean had = seekTarget != C.TIME_UNSET || cursorMs != C.TIME_UNSET;
        seekTarget = C.TIME_UNSET;
        cursorMs = C.TIME_UNSET; // Back dropped the scrub: the cursor goes back to the playhead
        edgeSaid = false;
        return had;
    }

    /**
     * Draws the OSD bar for window position `pos`. Live and start-over: the programme as the track with
     * the live position and the playhead. Catch-up and VOD: 0 to the duration.
     */
    private void showPosition(long pos, boolean flash) {
        long dur = player.getDuration();
        if (dur == C.TIME_UNSET) return;
        if (player.isCurrentMediaItemDynamic()) {
            long ws = windowStartMs();
            if (ws == C.TIME_UNSET) return;
            long live = ws + livePosition(), head = heldHead(ws + Math.min(pos, livePosition()));
            Api.Program p = playingStartOver ? startOverProgramme : current < 0 ? null : onAirNow(channels.get(current));
            long start = p != null ? Math.min(p.startMs, ws) : ws;
            long end = p != null ? Math.max(p.endMs, live) : live;
            String title = p != null ? p.title : playingTitle;
            Api.Channel ch = delayedChannel != null ? delayedChannel : current < 0 ? null : channels.get(current);
            showTimelineFrame(ch, title, start, end, live, head); // one frame, live window or start-over stream (U0)
        } else if (delayedChannel != null && delayedProgramme != null) { // catch-up: the programme on its channel (U0)
            Api.Program p = delayedProgramme;
            showTimelineFrame(delayedChannel, p.title, p.startMs, p.endMs, liveNowMs(), heldHead(p.startMs + pos));
        } else {
            osd.showSeek(playingEyebrow, playingTitle, pos, dur);
            buildActions();
            applyActions();
        }
        if (flash) flashOverlay();
    }

    /** Once a second: the bar follows the player. The time behind live is only the bar's own labels (A1). */
    private void tick() {
        ui.removeCallbacks(tick);
        ui.postDelayed(tick, 1000);
        if (player == null || zapStartMs > 0) return;
        watchDelayedEnd();
        reportMediaProgress();
        if (osd.getVisibility() == View.VISIBLE && osd.isTimeline() && seekTarget == C.TIME_UNSET && cursorMs == C.TIME_UNSET) {
            showPosition(player.getCurrentPosition(), false);
            skipPillTick(); // the Hoppa över pill follows the playhead in and out of its span
        }
    }

    /** The skip span the playhead is inside has changed: the row follows (the pill is only there while inside). */
    private void skipPillTick() {
        String want = skipItem() == null ? null
                : skipItem().type.equals(Api.TimelineItem.RECAP) ? "Hoppa över recap" : "Hoppa över intro";
        boolean has = false;
        for (String l : actionLabels) if (l.startsWith("Hoppa över")) has = true;
        if (want == null ? has : !has || !want.equals(actionLabels.get(action >= 0 && action < actionLabels.size() ? action : 0))) {
            buildActions();
            applyActions();
        }
    }

    /** The live position as a window position: where ExoPlayer plays when at live. */
    private long livePosition() {
        return Math.max(0, player.getDuration() - LIVE_OFFSET_MS);
    }

    /** What "live" shows on a live channel, as a broadcast moment (epoch ms). */
    private long liveNowMs() {
        // The player's own offset is the distance from the playhead to the edge only while it plays at the edge. On a
        // start-over stream, or live held behind, it is the distance of the playhead itself: "now" would then be the
        // playhead, every step forward would count as the live edge, and the scrub would jump to live (TV, 2026-10-19).
        boolean atEdge = player != null && playingLive && behindLiveMs() <= OsdView.LIVE_SLACK_MS;
        long off = atEdge ? player.getCurrentLiveOffset() : C.TIME_UNSET; // render-only: no player
        return Clock.now() - (off == C.TIME_UNSET ? LIVE_OFFSET_MS : off);
    }

    /** Epoch ms of window position 0, or C.TIME_UNSET. */
    private long windowStartMs() {
        androidx.media3.common.Timeline t = player.getCurrentTimeline();
        if (t.isEmpty()) return C.TIME_UNSET;
        return t.getWindow(player.getCurrentMediaItemIndex(), window).windowStartTimeMs;
    }

    /** How far a dynamic stream plays behind its live position; 0 for static streams. */
    private long behindLiveMs() {
        if (player == null || !player.isCurrentMediaItemDynamic() || player.getDuration() == C.TIME_UNSET) return 0;
        return Math.max(0, livePosition() - player.getCurrentPosition());
    }

    /** True for a dynamic window that holds real history: a time-shift buffer of at least 5 minutes. */
    private boolean timeShiftWindow() {
        if (player == null || !player.isCurrentMediaItemDynamic() || !player.isCurrentMediaItemSeekable()) return false;
        long dur = player.getDuration();
        if (dur == C.TIME_UNSET || dur < TIMESHIFT_MIN_MS) return false;
        Object m = player instanceof ExoPlayer ? ((ExoPlayer) player).getCurrentManifest() : null;
        return !(m instanceof DashManifest) || ((DashManifest) m).timeShiftBufferDepthMs != C.TIME_UNSET;
    }

    /**
     * Plays the programme on air from its start ("Från början"). For the menu and MEDIA_PREVIOUS.
     * In a start-over session it seeks to the start; otherwise it needs the programme's start-over option.
     */
    void restartCurrentProgramme() {
        if (player == null || current < 0) return;
        Log.i(TAG, "restartCurrentProgramme: " + scrubState());
        if (playingStartOver && zapStartMs == 0) {
            cancelSeek();
            loadHeadMs = C.TIME_UNSET; // a jump's held head (up to 15 s) would draw the middle while the start plays
            player.seekTo(0);
            showPosition(0, true);
            return;
        }
        Api.Channel ch = channels.get(current);
        Api.Program p = onAirNow(ch);
        if (p == null || p.startOver == null) {
            prefetchOnAir(ch);
            showChannelOsd(ch);
            osd.status("Går inte att börja om", false);
            return;
        }
        playStartOver(p, C.TIME_UNSET);
    }

    /** Starts the start-over stream of `p` at broadcast moment `atMs` (epoch ms), or at its start when unset. */
    private void playStartOver(Api.Program p, long atMs) {
        Log.i(TAG, "start-over of " + p.title + " at " + (atMs == C.TIME_UNSET ? "its start" : new java.util.Date(atMs).toString()));
        clearStartFrame();
        Api.Playable so = new Api.Playable();
        so.title = p.title;
        so.videoId = p.startOver.videoId;
        so.videoIdType = p.startOver.videoIdType;
        so.watchMode = p.startOver.watchMode;
        so.accessControl = p.startOver.accessControl;
        so.channel = p.startOver.channel != null ? p.startOver.channel : current >= 0 ? channels.get(current) : null;
        so.programme = p;
        // Position 0 is the window start, a few seconds before the broadcast start; the exact position
        // is set when the window is known (onTimelineChanged), before the first frame.
        so.startPositionMs = atMs == C.TIME_UNSET ? 0 : Math.max(0, atMs - p.broadcastStartMs);
        long live = liveNowMs();
        playingEyebrow = startOverEyebrow(so.channel);
        showTimelineFrame(so.channel, p.title, p.startMs, p.endMs, live, atMs == C.TIME_UNSET ? p.broadcastStartMs : atMs);
        flashOverlay();
        play(so, false);
        startOverAtMs = atMs;
        startOverProgramme = p;
    }

    // ---- Delayed channel playback (F14) ----------------------------------------------------
    //
    // Catch-up from the guide and start-over are a channel watched with a delay. When the programme
    // ends, the next programme of the same channel follows, still delayed: catch-up when it has ended,
    // start-over when it is on air. When the delay has caught up with live, or nothing playable follows
    // within 3 programmes, the channel continues live.

    /** The channel and programme of the delayed session; null for live and on-demand playback. */
    private Api.Channel delayedChannel;
    private Api.Program delayedProgramme;
    /** The programmes after the current one, fetched about 90 s before its end; null until then. */
    private List<Api.Program> upcoming;
    private boolean fetchingUpcoming, continuing;

    /** True once a second: prefetches what follows, and ends a start-over at the broadcast end. */
    private void watchDelayedEnd() {
        if (delayedProgramme == null || continuing) return;
        long left = delayedRemainingMs();
        if (left == C.TIME_UNSET) {
            if (dbgEvery(dbgNextTick)) Log.i(TAG, dbgState("delayedRemainingMs unknown: duration="
                    + (player.getDuration() == C.TIME_UNSET ? "unset" : player.getDuration()) + " dynamic=" + player.isCurrentMediaItemDynamic()
                    + " ws=" + windowStartMs()));
            return;
        }
        if (left < 90_000) fetchUpcoming(null);
        // A start-over stream may run on past its programme; the programme ends at the broadcast end.
        if (playingStartOver && left <= 0) {
            Log.i(TAG, dbgState("start-over past broadcast end: continueDelayed"));
            continueDelayed();
        }
        // A static catch-up that reached the end of its stream: the same continue, but not right
        // after a viewer's boundary jump (the jump landed near the end on purpose; the auto-continue
        // would undo the rewind at once — the bounce-back seen on the TV, 2026-10-17). STATE_ENDED
        // reaches continueDelayed through the player callback, so the hold-off lives there.
    }

    /** Playback time left in the delayed programme, or C.TIME_UNSET when unknown. */
    private long delayedRemainingMs() {
        if (player.getDuration() == C.TIME_UNSET) return C.TIME_UNSET;
        if (!player.isCurrentMediaItemDynamic()) return player.getDuration() - player.getCurrentPosition();
        long ws = windowStartMs();
        if (ws == C.TIME_UNSET) return C.TIME_UNSET;
        Api.Program p = delayedProgramme;
        long end = p.broadcastEndMs > 0 ? p.broadcastEndMs : p.endMs;
        return end - (ws + player.getCurrentPosition());
    }

    // ---- Post-hoc debugging (F14) ----------------------------------------------------------
    //
    // The delayed-continue path (F14) is not verified on the TV yet (REQUIREMENTS). Its facts sit in
    // one-shot logs (the "F14 " prefix greps clean) plus this state dump; a TV session afterwards is
    // debugged from `adb logcat -s TeliaLite`.

    /** The F14 state as one greppable line: every fact the continue path reads. */
    private String dbgState(String why) {
        Api.Program p = delayedProgramme;
        return "F14 [" + why + "] " + (p == null ? "no delayed programme"
                : clock(p.startMs) + "-" + clock(p.endMs) + " \"" + p.title + "\" bcEnd=" + (p.broadcastEndMs > 0 ? clock(p.broadcastEndMs) : "-")
                + " catchup=" + (p.catchup != null) + " startOver=" + (p.startOver != null)
                + " | " + (playingLive ? "live" : playingStartOver ? "start-over" : "catch-up/vod")
                + " left=" + (delayedRemainingMs() == C.TIME_UNSET ? "unknown" : delayedRemainingMs() / 1000 + "s")
                + " upcoming=" + (upcoming == null ? "none" : upcoming.size())
                + " continuing=" + continuing + " fetching=" + fetchingUpcoming
                + " ws=" + clock(windowStartMs()) + " pos=" + clock(player == null ? 0 : player.getCurrentPosition()));
    }
    /** True once per {@code n} ticks: the log shows a changing value without one line every second. */
    private boolean dbgEvery(int n) {
        if (n <= 0) return false;
        dbgTick++;
        return dbgTick % n == 1;
    }

    private int dbgTick, dbgNextTick = 30;

    private void fetchUpcoming(Runnable then) {
        if (upcoming != null) {
            if (then != null) then.run();
            return;
        }
        if (fetchingUpcoming && then == null) return;
        fetchingUpcoming = true;
        final Api.Channel ch = delayedChannel;
        final Api.Program prev = delayedProgramme;
        Log.i(TAG, "F14 fetchUpcoming: " + ch.name + " after " + prev.title + " (" + clock(prev.endMs) + ")"
                + (then == null ? " (prefetch)" : " (continue)"));
        io.execute(() -> {
            List<Api.Program> next;
            try {
                next = api.programmes(ch, prev.endMs, prev.endMs + 4 * 3600_000L);
            } catch (Exception e) {
                Log.w(TAG, "next programmes", e);
                next = new ArrayList<>();
            }
            final List<Api.Program> got = next;
            ui.post(() -> {
                fetchingUpcoming = false;
                if (delayedProgramme != prev) {
                    Log.i(TAG, "F14 fetchUpcoming landed after playback moved on: " + prev.title + " -> " + (delayedProgramme == null ? "null" : delayedProgramme.title));
                    return; // playback moved on
                }
                upcoming = got;
                StringBuilder sb = new StringBuilder("F14 upcoming of " + ch.name + " after " + prev.title + ": " + got.size());
                for (Api.Program n : got) {
                    sb.append("\n  ").append(clock(n.startMs)).append('-').append(clock(n.endMs)).append(' ').append(n.title)
                            .append(" catchup=").append(n.catchup != null).append(" startOver=").append(n.startOver != null)
                            .append(" bcStart=").append(n.broadcastStartMs > 0 ? clock(n.broadcastStartMs) : "-");
                }
                Log.i(TAG, sb.toString());
                if (then != null) then.run();
            });
        });
    }

    /** The delayed programme has ended: play what follows on the same channel, or go live. */
    private void continueDelayed() {
        if (continuing || delayedProgramme == null) return;
        continuing = true;
        Log.i(TAG, dbgState("continueDelayed: fetch what follows " + delayedProgramme.title));
        fetchUpcoming(this::playNextDelayed);
    }

    private void playNextDelayed() {
        userJumpAtMs = 0; // the auto-continue's own hops run on the normal rules again
        Api.Channel ch = delayedChannel;
        Api.Program prev = delayedProgramme;
        long now = Clock.now();
        Log.i(TAG, "F14 playNextDelayed on " + ch.name + " after " + prev.title + " (ended " + clock(prev.endMs)
                + "), now " + clock(now) + ", " + (upcoming == null ? "no upcoming list" : upcoming.size() + " candidates"));
        int hops = 0;
        for (Api.Program n : upcoming) {
            if (n.id.equals(prev.id) || n.startMs < prev.endMs - 60_000) continue; // the one that just ended
            if (++hops > 3 || n.startMs > now) {
                Log.i(TAG, "F14 stop after " + (hops > 3 ? "3 hops" : "a programme not broadcast yet")
                        + ": " + n.title + " starts " + clock(n.startMs));
                break; // nothing playable soon, or not broadcast yet
            }
            if (n.endMs <= now && n.catchup != null) {
                Log.i(TAG, "continue with catch-up: " + n.title);
                // F14: the next programme's passive overlay, like a zap; the chip keeps counting.
                playingEyebrow = "";
                playingResumeMs = 0;
                playingDurMs = n.endMs - n.startMs;
                playingTitle = n.title;
                play(n.catchup, false);
                showStartFrame();
                return;
            }
            if (n.endMs > now) { // on air
                if (n.startOver == null || now - n.broadcastStartMs < OsdView.LIVE_SLACK_MS) {
                    Log.i(TAG, "F14 " + (n.startOver == null ? "no start-over option for the on-air next: " : "next on air for under "
                            + (OsdView.LIVE_SLACK_MS / 1000) + " s: ") + n.title + " (bcStart " + clock(n.broadcastStartMs) + ")");
                    break;
                }
                Log.i(TAG, "continue with start-over: " + n.title);
                playStartOver(n, C.TIME_UNSET);
                return;
            }
            // ended without catch-up: try the next one
        }
        Log.i(TAG, dbgState("continue live on " + ch.name + (upcoming == null ? " (no upcoming list)" : "")));
        int idx = channels.indexOf(ch);
        tune(idx >= 0 ? idx : Math.max(0, current)); // the live banner says it: no toast (UX.md F14)
    }

    /** After the first manifest of a start-over stream: moves to the exact broadcast moment asked for. */
    private void alignStartOver() {
        if (startOverAtMs == C.TIME_UNSET || !playingStartOver) return;
        long ws = windowStartMs();
        if (ws == C.TIME_UNSET || ws <= 0) return;
        long want = Math.max(0, Math.min(startOverAtMs - ws, livePosition()));
        Log.i(TAG, "alignStartOver want=" + want + " at=" + player.getCurrentPosition() + " ws=" + new java.util.Date(ws));
        startOverAtMs = C.TIME_UNSET;
        if (Math.abs(want - player.getCurrentPosition()) > 1000) player.seekTo(want);
    }

    private static String clock(long ms) {
        long sec = ms / 1000;
        return sec >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60)
                : String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60);
    }

    /** 30 s per press; held: 2 min per repeat, then 10 min. */
    private static long seekStep(KeyEvent e) {
        int r = e.getRepeatCount();
        return r > 12 ? 10 * 60_000 : r > 2 ? SEEK_HELD_MS : SEEK_MS;
    }

    // ---- Audio and text (G5) -------------------------------------------------------------------
    //
    // The player reports its tracks; TrackPolicy chooses (the settings, then the viewer's pick of this
    // session) and this applies the choice as a track override. The pill Ljud och text and its sheet are
    // the viewer's way to override; a pick of a language is remembered as the preference (TrackPrefs).

    /** The language of the device's own settings; the harness runs as a Swedish TV. */
    private static String deviceLanguage() {
        return languageOverride != null ? languageOverride : Locale.getDefault().getLanguage();
    }

    /** The settings as the player's own preferences, so the first selection of every start is already right. */
    private void applyTrackParameters() {
        if (player == null) return;
        List<String> langs = TrackPolicy.audioLanguages(trackPrefs.audio(), deviceLanguage());
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setPreferredAudioLanguages(langs.toArray(new String[0]))
                .setPreferredTextLanguage(trackPrefs.textLanguage())
                .setPreferredTextRoleFlags(0)
                // Automatisk waits for the audio to be known (selectTracks), Av never shows text.
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, trackPrefs.subtitleMode() != TrackPolicy.SUB_ALWAYS)
                .build());
    }

    /** The stream's tracks are known (or changed): choose among them and show or hide the pill. */
    private void tracksChanged(List<TrackInfo> list) {
        boolean pill = TrackPolicy.pillWanted(trackList);
        trackList = list;
        selectTracks();
        if (pill != TrackPolicy.pillWanted(trackList)) {
            buildActions();
            if (osd.getVisibility() == View.VISIBLE) applyActions();
        }
    }

    /** Applies the policy's audio and text choice where the player does not already play it. */
    private void selectTracks() {
        if (trackList.isEmpty()) return;
        List<String> langs = TrackPolicy.audioLanguages(trackPrefs.audio(), deviceLanguage());
        int a = TrackPolicy.pickAudio(trackList, langs, audioPick);
        if (a >= 0 && !trackList.get(a).selected) applyTrack(trackList.get(a));
        String audioLanguage = null;
        int cur = -1;
        for (int i = 0; i < trackList.size(); i++) {
            TrackInfo t = trackList.get(i);
            if (t.selected && t.type == TrackInfo.AUDIO) audioLanguage = t.language;
            if (t.selected && t.type == TrackInfo.TEXT) cur = i;
        }
        int t = TrackPolicy.pickText(trackList, trackPrefs.subtitleMode(), trackPrefs.textLanguage(), audioLanguage, textPick);
        if (t == cur) return;
        if (t < 0) textOff();
        else applyTrack(trackList.get(t));
    }

    /** Plays this track: the list says so at once, the player gets an override of its group. */
    private void applyTrack(TrackInfo t) {
        for (TrackInfo o : trackList) if (o.type == t.type) o.selected = o == t;
        if (player == null || lastTracks == null || t.group >= lastTracks.getGroups().size()) return;
        androidx.media3.common.TrackGroup g = lastTracks.getGroups().get(t.group).getMediaTrackGroup();
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < g.length; i++) all.add(i);
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(t.type, false)
                .setOverrideForType(new TrackSelectionOverride(g, all))
                .build());
    }

    private void textOff() {
        for (TrackInfo o : trackList) if (o.type == TrackInfo.TEXT) o.selected = false;
        captions.setCue(null);
        if (player == null) return;
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build());
    }

    /**
     * The pick of a row in Ljud och text. It holds for this session; the language of a pick is also what the
     * settings remember (an audio description or Av/På is not a language and is not).
     */
    private void chooseTrack(TrackPolicy.Row r) {
        if (r.track < 0) {
            textPick = TrackPolicy.TEXT_OFF;
            textOff();
            return;
        }
        TrackInfo t = trackList.get(r.track);
        if (t.type == TrackInfo.AUDIO) {
            audioPick = t.key();
            if (!t.describes()) trackPrefs.setAudio(TrackPolicy.languageOf(t));
        } else {
            textPick = t.key();
            trackPrefs.setTextLanguage(TrackPolicy.languageOf(t));
        }
        applyTrack(t);
    }

    /**
     * The pill Ljud och text: the one picker, its rows the Ljud and Undertext choices (Surface 3). A pick
     * applies at once and the sheet stays, so the viewer can try the other group; Back returns to the pill.
     */
    private void openTracksSheet() {
        String title = playingTitle == null || playingTitle.isEmpty() ? "Ljud och text" : playingTitle;
        picker.setOnClose(this::reopenOnTracksPill);
        openPicker("Ljud och text", title, trackRows(), 0);
    }

    /** The picker's rows for the stream's tracks: the Ljud group, then Undertext with Av first. */
    private List<PickerView.Row> trackRows() {
        List<PickerView.Row> rows = new ArrayList<>();
        for (TrackPolicy.Row r : TrackPolicy.rows(trackList, sv)) {
            if (r.header) {
                rows.add(PickerView.Row.header(r.label));
                continue;
            }
            String value = r.sub;
            if (r.selected && value == null) value = "Spelas";
            rows.add(new PickerView.Row(r.label, value, PickerView.Row.VALUE, (java.util.function.IntConsumer) d -> {
                chooseTrack(r);
                picker.setContent("Ljud och text", playingTitle == null || playingTitle.isEmpty() ? "Ljud och text" : playingTitle,
                        trackRows(), picker.focus());
            }, false));
        }
        return rows;
    }

    private void reopenOnTracksPill() {
        renderFrame();
        action = Math.max(0, actionLabels.indexOf("Ljud och text"));
        applyActions();
        osd.focusRow(OsdView.FOCUS_ACTIONS);
        flashOverlay();
    }

    /** Test hook: the stream reported these tracks (render-only playback has no player). */
    void tracksForTest(List<TrackInfo> list) {
        tracksChanged(list);
    }

    /** Test hook: what plays, as "audio=<key> text=<key|off>". */
    String selectedTracksForTest() {
        String a = "none", t = "off";
        for (TrackInfo i : trackList) {
            if (i.selected && i.type == TrackInfo.AUDIO) a = i.key();
            if (i.selected && i.type == TrackInfo.TEXT) t = i.key();
        }
        return "audio=" + a + " text=" + t;
    }

    /** Test hook: a caption cue arrived. */
    void cueForTest(String text) {
        captions.setCue(text);
    }

    CaptionView captionsForTest() {
        return captions;
    }

    OsdView osdForTest() {
        return osd;
    }

    // ---- The system around the player (G6) -----------------------------------------------------

    private boolean awake, visible;

    /** The screen stays on only while a picture is meant to move (Playback.keepScreenOn). */
    private void keepAwake(boolean playWhenReady, int state) {
        boolean on = Playback.keepScreenOn(playWhenReady, state);
        if (on == awake) return;
        awake = on;
        if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /**
     * The player changed playWhenReady. A viewer's own press set {@code paused} first; a change the player made itself
     * (audio focus to another app, the headset pulled, a suppression) is mirrored into it, so Paus/Spela and the
     * media session say what the picture does.
     */
    void playWhenReadyChanged(boolean playWhenReady, int reason) {
        keepAwake(playWhenReady, player != null ? player.getPlaybackState() : Player.STATE_READY);
        if (Playback.followsPlayer(reason) && paused == playWhenReady) {
            paused = !playWhenReady;
            buildActions();
            if (osd.getVisibility() == View.VISIBLE) applyActions();
        }
        updateSession();
    }

    /** The media session follows what plays: active while a stream is asked for and the app is on screen. */
    private void updateSession() {
        if (session == null) return;
        boolean something = current >= 0 && current < channels.size() && !tuneFailed && (player == null || player.getMediaItemCount() > 0 || zapStartMs > 0);
        if (!something || !visible) {
            session.hide();
            return;
        }
        SessionBridge.State st = sessionState();
        Api.Channel ch = delayedChannel != null ? delayedChannel : playingMedia == null ? channels.get(current) : null;
        session.show(st, ch == null ? null : logos.get(ch, false));
    }

    SessionBridge.State sessionState() {
        Api.Channel ch = current >= 0 && current < channels.size() ? channels.get(current) : null;
        boolean rewindable = !playingLive || (ch != null && hasStartOver(onAirNow(ch)));
        Api.Asset next = playingMedia == null ? null : nextEpisode(playingMedia);
        boolean restart = false;
        for (String l : actionLabels) if (actionRank(l) == 1) restart = true;
        boolean buffering = zapStartMs > 0 || (player != null && player.getPlaybackState() == Player.STATE_BUFFERING);
        long pos = player != null && zapStartMs == 0 ? player.getCurrentPosition() : playingResumeMs;
        long dur = player != null && zapStartMs == 0 && player.getDuration() != C.TIME_UNSET ? player.getDuration() : playingDurMs;
        String title, subtitle;
        if (playingMedia != null) {
            title = playingTitle;
            subtitle = playingMedia.seriesTitle == null ? "" : playingMedia.seriesTitle;
        } else {
            Api.Channel shown = delayedChannel != null ? delayedChannel : ch;
            title = playingLive && ch != null && ch.now != null ? ch.now.title : playingTitle;
            subtitle = shown == null ? "" : shown.name;
        }
        return SessionBridge.of(paused, buffering, playingLive, rewindable, next != null && next.playable != null, restart, pos, dur, title, subtitle);
    }

    /**
     * Fortsätt titta on the TV's home screen (G6). Called when the list can have changed: a position saved (stop, pause,
     * Home, once a minute while playing), Telia's list arrived, a title was removed. The entries are built here (cheap),
     * the provider is written on the I/O thread, and nothing is written when nothing changed.
     */
    private void syncWatchNext() {
        if (watchNext == null) return;
        for (Api.Saved s : library.continueWatching) if (s.imageUrl == null) s.imageUrl = posterSources.get(s.id);
        List<WatchNext.Entry> want = WatchNext.entries(library.continueWatching, library, getPackageName(), watchNextAnchor);
        if (watchNextPushed != null && watchNextPushed.size() == want.size()) {
            boolean same = true;
            for (int i = 0; same && i < want.size(); i++) same = want.get(i).sameAs(watchNextPushed.get(i));
            if (same) return;
        }
        watchNextPushed = want;
        io.execute(() -> watchNext.sync(want));
    }

    /** Test hook: the Watch Next rows last handed to the provider. */
    List<WatchNext.Entry> watchNextForTest() {
        return watchNextPushed;
    }

    /** Test hook: what the player's own change of playWhenReady does (render-only playback has no player). */
    void playWhenReadyForTest(boolean playWhenReady, int reason) {
        playWhenReadyChanged(playWhenReady, reason);
    }

    void keepAwakeForTest(boolean playWhenReady, int state) {
        keepAwake(playWhenReady, state);
    }

    boolean screenKeptOnForTest() {
        return (getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0;
    }

    /** Test hook: the id of the channel that is tuned, or null. */
    String channelForTest() {
        return current >= 0 && current < channels.size() ? channels.get(current).id : null;
    }

    List<String> channelIdsForTest() {
        List<String> ids = new ArrayList<>();
        for (Api.Channel c : channels) ids.add(c.id);
        return ids;
    }

    List<String> channelNamesForTest() {
        List<String> names = new ArrayList<>();
        for (Api.Channel c : channels) names.add(c.name);
        return names;
    }

    /** Test hook: the text in Sök's query field. */
    String queryForTest() {
        return query;
    }

    /** Test hook: the results of a spoken search arrived. */
    void playCertainForTest(String q, List<Api.Asset> hits) {
        playCertain(q, hits);
    }

    SessionBridge sessionForTest() {
        return session;
    }

    // ---- Zapping ---------------------------------------------------------------------------

    private long lastZapKeyMs;
    private int zapTarget = -1;
    private final Runnable zapCommit = () -> {
        if (zapTarget >= 0) {
            int t = zapTarget;
            zapTarget = -1;
            tune(t, true);
        }
    };

    /**
     * Up/down on video. The first press tunes at once; presses within 400 ms of the previous one only
     * move the banner, and the tune fires 250 ms after the last of them (UX.md 3.3). While a title started
     * by hand plays (an episode, a film, catch-up), ↑ ↓ never zap: they show its banner (F17).
     */
    private void zap(int dir) {
        if (!playingLive && !playingStartOver) {
            showTitleBanner();
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        boolean burst = now - lastZapKeyMs < 400;
        lastZapKeyMs = now;
        int n = channels.size();
        int next = ((zapTarget >= 0 ? zapTarget : current) + dir + n) % n;
        ui.removeCallbacks(zapCommit);
        if (!burst && zapTarget < 0) {
            tune(next, true);
            return;
        }
        zapTarget = next;
        showChannelOsd(channels.get(next));
        ui.postDelayed(zapCommit, 250);
    }

    @Override
    protected void onStop() {
        super.onStop();
        visible = false;
        ui.removeCallbacks(tick);
        if (player != null) player.pause();
        saveMediaProgress(false); // leaving the app (Home, another app): Telia and the cache get the position
        session.hide(); // Android TV expects playback to stop when the app leaves the screen
        keepAwake(false, Player.STATE_IDLE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        visible = true;
        captions.setLook(CaptionLook.system(this)); // the viewer may have changed the accessibility style meanwhile
        if (player != null) tick();
        if (player != null && player.getMediaItemCount() > 0) {
            if (playingLive && current >= 0) tune(current); // a stale live stream; re-tune
            else if (!paused) player.play();
        }
        updateSession();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null); // no timer of this activity may outlive it
        session.release(); // after the callbacks: nothing the system sends reaches a dead activity
        if (player != null) player.release();
        io.shutdownNow();
        super.onDestroy();
    }
}
