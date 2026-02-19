package com.orcterm.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.orcterm.R;
import com.orcterm.core.sftp.SftpFile;
import com.orcterm.core.sftp.SftpRepository;
import com.orcterm.core.ssh.SshNative;
import com.orcterm.core.session.SessionConnector;
import com.orcterm.core.session.SessionInfo;
import com.orcterm.core.session.SessionManager;
import com.orcterm.core.terminal.TerminalSession;
import com.orcterm.ui.SftpTransferService;
import com.orcterm.ui.common.UiStateController;
import com.orcterm.util.CommandConstants;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SFTP 文件管理 Activity
 * 提供文件浏览、上传、下载、重命名、删除等功能
 */
public class SftpActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_UPLOAD = 1001;
    private static final int REQUEST_CODE_EDIT = 1002;
    private static final int REQUEST_CODE_UPLOAD_FOLDER = 1003;
    private static final int LIST_PAGE_SIZE = 200;
    private static final int LIST_PAGE_PREFETCH = 40;

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private UiStateController uiStateController;
    private FileAdapter adapter;
    private View pathBar;
    private View pathScroll;
    private LinearLayout pathSegments;
    private EditText pathInput;
    private View btnHome;
    private View btnPathApply;
    private View btnPathCancel;
    private com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton fabMain;
    private View transferPanel;
    private TextView transferTitle;
    private ProgressBar transferProgress;
    private TextView transferSubtitle;
    private TextView transferQueue;
    private MaterialButton btnCancelTransfer;
    
    private SshNative sshNative;
    private SftpRepository sftpRepository;
    private long sshHandle = 0;
    private boolean isSharedSession = false;
    private boolean preferDedicatedConnection = true;
    
    private String hostname, username, password;
    private int port;
    private int authType;
    private String keyPath;
    private long hostId = -1L;
    private long sftpSessionId = -1L;
    private boolean sftpSessionConnected = false;
    private String currentPath = "/root";
    
    private SharedPreferences prefs;
    private Set<String> pinnedFiles = new HashSet<>();
    private int sortMode = 0; // 0: Name ASC, 1: Name DESC, 2: Size ASC, 3: Size DESC
    private boolean showHidden = false;
    private int lastIconSize = -1;
    private boolean lastShowHidden = false;
    private int lastSortMode = -1;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger loadSequence = new AtomicInteger(0);
    private volatile int activeLoadToken = 0;
    private List<SftpFile> pendingFiles = null;
    private int pendingIndex = 0;
    private int pendingToken = 0;
    private boolean isAppendingPage = false;
    private String lastRequestedPath = "/root";

    private android.view.ActionMode actionMode;
    private BroadcastReceiver transferReceiver;
    
    // Clipboard for copy/move operations
    private List<String> clipboardFiles = new ArrayList<>();
    private boolean isCutOperation = false;
    private String clipboardSourcePath = null;

    private void startSelectionMode() {
        if (actionMode != null) return;
        actionMode = startActionMode(new android.view.ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                mode.getMenuInflater().inflate(R.menu.sftp_selection_menu, menu);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                Set<SftpFile> selected = adapter.getSelectedFiles();
                if (selected.isEmpty()) return false;
                
                boolean hasArchives = false;
                for (SftpFile f : selected) {
                    if (!f.isDir && isArchiveFile(f)) {
                        hasArchives = true;
                        break;
                    }
                }
                
                MenuItem extractItem = menu.findItem(R.id.action_extract);
                if (extractItem != null) extractItem.setVisible(hasArchives);
                
                return true;
            }

            @Override
            public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) {
                int id = item.getItemId();
                Set<SftpFile> selected = adapter.getSelectedFiles();
                if (selected.isEmpty()) return true;

                if (id == R.id.action_copy || id == R.id.action_cut) {
                    clipboardFiles.clear();
                    for (SftpFile f : selected) {
                        clipboardFiles.add((currentPath.endsWith("/") ? currentPath : currentPath + "/") + f.name);
                    }
                    clipboardSourcePath = currentPath;
                    isCutOperation = (id == R.id.action_cut);
                    Toast.makeText(SftpActivity.this, 
                        String.format(getString(isCutOperation ? R.string.msg_cut_n_files : R.string.msg_copied_n_files), selected.size()), 
                        Toast.LENGTH_SHORT).show();
                    mode.finish();
                } else if (id == R.id.action_delete) {
                    showMultiDeleteDialog(selected);
                } else if (id == R.id.action_download) {
                    enqueueMultiDownload(selected);
                    mode.finish();
                } else if (id == R.id.action_compress) {
                    showMultiCompressDialog(selected);
                } else if (id == R.id.action_extract) {
                    showMultiExtractDialog(selected);
                } else if (id == R.id.action_select_all) {
                    adapter.selectAll();
                }
                return true;
            }

            @Override
            public void onDestroyActionMode(android.view.ActionMode mode) {
                adapter.setSelectionMode(false);
                actionMode = null;
            }
        });
    }

    private boolean isArchiveFile(SftpFile file) {
        String ext = getExtension(file.name);
        return ext.equals("zip") || ext.equals("tar") || ext.equals("gz") || ext.equals("tgz") || ext.equals("rar") || ext.equals("7z") || ext.equals("bz2");
    }

    private void showMultiExtractDialog(Set<SftpFile> selected) {
         new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.action_extract)
            .setMessage(String.format(getString(R.string.msg_extract_n_archives), selected.size()))
            .setPositiveButton(R.string.action_ok, (d, w) -> {
                 executor.execute(() -> {
                     try {
                         for (SftpFile f : selected) {
                             if (!isArchiveFile(f)) continue;
                             String cmd = "";
                             String name = f.name;
                             String fullPath = getFullPath(name);
                             // Simple extraction logic based on extension
                            if (name.endsWith(".zip")) cmd = String.format(CommandConstants.CMD_UNZIP, fullPath);
                            else if (name.endsWith(".tar")) cmd = String.format(CommandConstants.CMD_TAR_EXTRACT, fullPath);
                            else if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) cmd = String.format(CommandConstants.CMD_TAR_GZ_EXTRACT, fullPath);
                            else if (name.endsWith(".gz")) cmd = String.format(CommandConstants.CMD_GZIP_DECOMPRESS, fullPath);
                            else if (name.endsWith(".rar")) cmd = String.format(CommandConstants.CMD_UNRAR_EXTRACT, fullPath);
                            else if (name.endsWith(".7z")) cmd = String.format(CommandConstants.CMD_7Z_EXTRACT, fullPath);
                             
                             if (!cmd.isEmpty()) {
                                sshNative.exec(sshHandle, String.format(CommandConstants.CMD_CD_AND, escapePath(currentPath), cmd));
                             }
                         }
                         runOnUiThread(() -> {
                             Toast.makeText(this, getString(R.string.msg_extract_success), Toast.LENGTH_SHORT).show();
                             if (actionMode != null) actionMode.finish();
                             loadFiles(currentPath);
                         });
                     } catch (Exception e) {
                         runOnUiThread(() -> Toast.makeText(this, String.format(getString(R.string.msg_extract_fail), e.getMessage()), Toast.LENGTH_SHORT).show());
                     }
                 });
            })
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    
    @Override
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        // Show Paste option if clipboard has files
        android.view.MenuItem pasteItem = menu.findItem(R.id.action_paste);
        if (pasteItem != null) {
            pasteItem.setVisible(!clipboardFiles.isEmpty());
        }
        return super.onPrepareOptionsMenu(menu);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sftp);

        setSupportActionBar(findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        prefs = getSharedPreferences("sftp_prefs", MODE_PRIVATE);
        sortMode = prefs.getInt("file_sort_order", 0);
        showHidden = prefs.getBoolean("file_show_hidden", false);
        int iconSize = prefs.getInt("file_icon_size", 0);
        
        lastSortMode = sortMode;
        lastShowHidden = showHidden;
        lastIconSize = iconSize;

        pinnedFiles = new HashSet<>(prefs.getStringSet("pinned_files", new HashSet<>()));

        recyclerView = findViewById(R.id.recycler_files);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        progressBar = findViewById(R.id.progress_bar);
        uiStateController = new UiStateController(this);
        uiStateController.setRetryAction(() -> loadFiles(lastRequestedPath));
        pathBar = findViewById(R.id.path_bar);
        pathScroll = findViewById(R.id.path_scroll);
        pathSegments = findViewById(R.id.path_segments);
        pathInput = findViewById(R.id.path_input);
        btnHome = findViewById(R.id.btn_home);
        btnPathApply = findViewById(R.id.btn_path_apply);
        btnPathCancel = findViewById(R.id.btn_path_cancel);
        fabMain = findViewById(R.id.fab_main);
        transferPanel = findViewById(R.id.transfer_panel);
        transferTitle = findViewById(R.id.transfer_title);
        transferProgress = findViewById(R.id.transfer_progress);
        transferSubtitle = findViewById(R.id.transfer_subtitle);
        transferQueue = findViewById(R.id.transfer_queue);
        btnCancelTransfer = findViewById(R.id.btn_cancel_transfer);

        if (btnCancelTransfer != null) {
            btnCancelTransfer.setOnClickListener(v -> requestCancelActiveTransfer());
        }
        
        swipeRefresh.setOnRefreshListener(() -> loadFiles(currentPath));
        setupPathBar();
        setupMainAction();

        adapter = new FileAdapter(new FileAdapter.OnFileClickListener() {
            @Override
            public void onFileClick(SftpFile file) {
                if (adapter.isSelectionMode()) {
                    adapter.toggleSelection(file);
                } else {
                    handleFileClick(file);
                }
            }

            @Override
            public void onFileLongClick(SftpFile file, View anchor) {
                if (!adapter.isSelectionMode()) {
                    adapter.setSelectionMode(true);
                    adapter.toggleSelection(file);
                } else {
                    // Already in selection mode, maybe toggle or show menu?
                    // Standard behavior: just toggle
                    adapter.toggleSelection(file);
                }
            }
            
            @Override
            public void onSelectionChanged(int count) {
                if (count == 0 && adapter.isSelectionMode()) {
                     if (actionMode != null) {
                         actionMode.setTitle(String.format(getString(R.string.msg_selected_count), count));
                         actionMode.invalidate();
                     } else {
                         startSelectionMode();
                     }
                } else if (count > 0) {
                     if (actionMode == null) startSelectionMode();
                     actionMode.setTitle(String.format(getString(R.string.msg_selected_count), count));
                     actionMode.invalidate();
                }
            }

            @Override
            public void onFileMenuClick(SftpFile file, View anchor) {
                if (adapter.isSelectionMode()) {
                    adapter.toggleSelection(file);
                } else {
                    showFileActionMenu(file, anchor);
                }
            }
        });
        
        updateLayoutManager(iconSize);
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                maybeAppendNextPage();
            }
        });

        hostId = getIntent().getLongExtra("host_id", -1L);
        hostname = getIntent().getStringExtra("hostname");
        port = getIntent().getIntExtra("port", 22);
        username = getIntent().getStringExtra("username");
        password = getIntent().getStringExtra("password");
        authType = getIntent().getIntExtra("auth_type", 0);
        keyPath = getIntent().getStringExtra("key_path");
        long incomingSessionId = getIntent().getLongExtra("session_id", -1L);
        sftpSessionId = resolveSftpSessionId(incomingSessionId);

        sshNative = new SshNative();
        sftpRepository = new SftpRepository(sshNative);
        
        if (incomingSessionId != -1) {
            SessionManager manager = SessionManager.getInstance();
            TerminalSession session = manager.getTerminalSession(incomingSessionId);
            if (session == null) {
                long sharedHandle = manager.getAndRemoveSharedHandle(incomingSessionId);
                if (sharedHandle != 0) {
                    this.sshHandle = sharedHandle;
                    this.isSharedSession = true;
                    this.preferDedicatedConnection = false;
                }
            }
        }

        loadFiles(currentPath);
        
        transferReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (SftpTransferService.ACTION_UI_UPDATE.equals(action)) {
                    boolean visible = intent.getBooleanExtra(SftpTransferService.EXTRA_VISIBLE, false);
                    String title = intent.getStringExtra(SftpTransferService.EXTRA_TITLE);
                    int progress = intent.getIntExtra(SftpTransferService.EXTRA_PROGRESS, 0);
                    int max = intent.getIntExtra(SftpTransferService.EXTRA_MAX, 1000);
                    String subtitle = intent.getStringExtra(SftpTransferService.EXTRA_SUBTITLE);
                    String queue = intent.getStringExtra(SftpTransferService.EXTRA_QUEUE);
                    transferPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
                    if (visible) {
                        transferTitle.setText(title != null ? title : "");
                        transferProgress.setMax(max);
                        transferProgress.setProgress(progress);
                        transferSubtitle.setText(subtitle != null ? subtitle : "");
                        transferQueue.setText(queue != null ? queue : "");
                    }
                } else if (SftpTransferService.ACTION_UI_EVENT.equals(action)) {
                    String event = intent.getStringExtra(SftpTransferService.EXTRA_EVENT);
                    String message = intent.getStringExtra(SftpTransferService.EXTRA_MESSAGE);
                    String remotePath = intent.getStringExtra(SftpTransferService.EXTRA_REMOTE_PATH);
                    if (message != null && !message.isEmpty()) {
                        Toast.makeText(SftpActivity.this, message, Toast.LENGTH_SHORT).show();
                        if (transferPanel.getVisibility() == View.VISIBLE) {
                            transferSubtitle.setText(message);
                        }
                    }
                    if (remotePath != null && !remotePath.isEmpty()) {
                        showReloadRestartPrompt(remotePath);
                    }
                    if ("download_done".equals(event) || "upload_done".equals(event) || "upload_file_done".equals(event)) {
                        loadFiles(currentPath);
                    }
                }
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (transferReceiver != null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(SftpTransferService.ACTION_UI_UPDATE);
            filter.addAction(SftpTransferService.ACTION_UI_EVENT);
            registerReceiver(transferReceiver, filter);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (transferReceiver != null) {
            try {
                unregisterReceiver(transferReceiver);
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        int iconSize = prefs.getInt("file_icon_size", 0);
        boolean hidden = prefs.getBoolean("file_show_hidden", false);
        int sort = prefs.getInt("file_sort_order", 0);

        if (iconSize != lastIconSize) {
            updateLayoutManager(iconSize);
            lastIconSize = iconSize;
        }

        if (hidden != lastShowHidden || sort != lastSortMode) {
            lastShowHidden = hidden;
            lastSortMode = sort;
            showHidden = hidden;
            sortMode = sort;
            loadFiles(currentPath);
        }
    }

    private void updateLayoutManager(int iconSize) {
        if (iconSize == 0) { // List
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            adapter.setViewMode(0);
        } else { // Small (1) or Large (2) Grid
            int spanCount = iconSize == 1 ? 4 : 2; 
            recyclerView.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, spanCount));
            adapter.setViewMode(1);
        }
    }

    private void setupPathBar() {
        if (btnHome != null) {
            btnHome.setOnClickListener(v -> loadFiles("/"));
        }
        if (pathBar != null) {
            pathBar.setOnClickListener(v -> showPathInput());
        }
        if (btnPathApply != null) {
            btnPathApply.setOnClickListener(v -> applyPathInput());
        }
        if (btnPathCancel != null) {
            btnPathCancel.setOnClickListener(v -> setPathEditMode(false));
        }
        if (pathInput != null) {
            pathInput.setOnEditorActionListener((v, actionId, event) -> {
                applyPathInput();
                return true;
            });
        }
        updatePathSegments();
    }

    private void showPathInput() {
        if (pathInput != null) {
            pathInput.setText(currentPath);
            pathInput.setSelection(pathInput.getText().length());
            pathInput.requestFocus();
        }
        setPathEditMode(true);
    }

    private void setupMainAction() {
        if (fabMain == null) return;
        fabMain.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, fabMain);
            menu.getMenu().add(0, 1, 0, getString(R.string.action_upload));
            menu.getMenu().add(0, 2, 0, getString(R.string.action_upload_folder));
            menu.getMenu().add(0, 3, 0, getString(R.string.action_new_folder));
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    openFilePicker();
                } else if (item.getItemId() == 2) {
                    openFolderPicker();
                } else if (item.getItemId() == 3) {
                    showCreateDialog(getString(R.string.dialog_create_folder), true);
                }
                return true;
            });
            menu.show();
        });
    }

    private void applyPathInput() {
        if (pathInput == null) return;
        String path = pathInput.getText().toString().trim();
        if (!path.isEmpty()) {
            loadFiles(path);
        }
        setPathEditMode(false);
    }

    private void setPathEditMode(boolean editing) {
        if (pathScroll != null) {
            pathScroll.setVisibility(editing ? View.GONE : View.VISIBLE);
        }
        if (pathSegments != null) {
            pathSegments.setVisibility(editing ? View.GONE : View.VISIBLE);
        }
        if (pathInput != null) {
            pathInput.setVisibility(editing ? View.VISIBLE : View.GONE);
        }
        if (btnPathApply != null) {
            btnPathApply.setVisibility(editing ? View.VISIBLE : View.GONE);
        }
        if (btnPathCancel != null) {
            btnPathCancel.setVisibility(editing ? View.VISIBLE : View.GONE);
        }
    }

    private void updatePathSegments() {
        if (pathSegments == null) return;
        pathSegments.removeAllViews();
        String path = currentPath == null || currentPath.isEmpty() ? "/" : currentPath;
        if (!path.startsWith("/")) path = "/" + path;

        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (!part.isEmpty()) parts.add(part);
        }

        addSegment("/", "/", parts.isEmpty());
        StringBuilder running = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            running.append("/").append(part);
            if (i > 0) {
                addSeparator();
            }
            addSegment(part, running.toString(), i == parts.size() - 1);
        }
    }

    private void addSeparator() {
        TextView tv = new TextView(this);
        tv.setText(" / ");
        tv.setTextColor(resolveColorAttr(com.google.android.material.R.attr.colorOnSurfaceVariant));
        pathSegments.addView(tv);
    }

    private void addSegment(String label, String path, boolean isCurrent) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(resolveColorAttr(isCurrent ? com.google.android.material.R.attr.colorPrimary : com.google.android.material.R.attr.colorOnSurfaceVariant));
        tv.setTextSize(14);
        if (!isCurrent) {
            tv.setOnClickListener(v -> loadFiles(path));
        }
        pathSegments.addView(tv);
    }

    private int resolveColorAttr(int attr) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(attr, typedValue, true);
        if (typedValue.resourceId != 0) {
            return androidx.core.content.ContextCompat.getColor(this, typedValue.resourceId);
        }
        return typedValue.data;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.sftp_menu, menu); // Add Paste
        menu.add(0, 1, 0, R.string.action_refresh);
        menu.add(0, 3, 0, R.string.action_new_file);
        menu.add(0, 5, 0, R.string.action_goto);
        menu.add(0, 6, 0, R.string.action_sort);
        MenuItem hiddenItem = menu.add(0, 8, 0, R.string.action_show_hidden);
        hiddenItem.setCheckable(true);
        hiddenItem.setChecked(showHidden);
        menu.add(0, 7, 0, R.string.action_home);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_paste) {
            handlePaste();
            return true;
        }
        switch (item.getItemId()) {
            case 1:
                loadFiles(currentPath);
                return true;
            case 3:
                showCreateDialog(getString(R.string.dialog_create_file), false);
                return true;
            case 5:
                showGoToDialog();
                return true;
            case 6:
                showSortDialog();
                return true;
            case 8:
                showHidden = !showHidden;
                item.setChecked(showHidden);
                prefs.edit().putBoolean("show_hidden", showHidden).apply();
                loadFiles(currentPath);
                return true;
            case 7:
                loadFiles("/");
                return true;
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void handleFileClick(SftpFile file) {
        if (file.isDir) {
            String nextPath;
            if ("..".equals(file.name)) {
                // Go up
                if ("/".equals(currentPath)) return; // Already at root
                int lastSlash = currentPath.lastIndexOf('/');
                if (lastSlash > 0) {
                    nextPath = currentPath.substring(0, lastSlash);
                } else {
                    nextPath = "/";
                }
            } else if (".".equals(file.name)) {
                return; // refresh
            } else {
                // Go down
                if (currentPath.endsWith("/")) {
                    nextPath = currentPath + file.name;
                } else {
                    nextPath = currentPath + "/" + file.name;
                }
            }
            loadFiles(nextPath);
        } else {
            String ext = getExtension(file.name);
            if (isTextFile(ext)) {
                openEditor(file);
            } else if (isMediaFile(ext)) {
                openMediaPreview(file);
            } else {
                Toast.makeText(this, String.format(getString(R.string.msg_file_info_bytes), file.name, file.size), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean isTextFile(String ext) {
        return ext.matches("^(txt|md|json|xml|html|css|js|ts|py|java|c|cpp|h|kt|gradle|properties|conf|sh|log)$");
    }

    private boolean isMediaFile(String ext) {
        return ext.matches("^(jpg|jpeg|png|gif|bmp|webp|mp4|mkv|webm|avi)$");
    }

    private void openMediaPreview(SftpFile file) {
        if (!swipeRefresh.isRefreshing()) progressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                ensureConnected();
                String fullPath = getFullPath(file.name);
                File localFile = new File(getCacheDir(), file.name);

                if (file.size > 20 * 1024 * 1024) { // 20MB limit for preview
                    mainHandler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, getString(R.string.msg_file_too_large_for_preview), Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                String cmd = String.format(CommandConstants.CMD_CAT_BASE64, fullPath);
                String base64Content = sshNative.exec(sshHandle, cmd);

                byte[] content = Base64.decode(base64Content.replace("\n", ""), Base64.NO_WRAP);
                try (FileOutputStream fos = new FileOutputStream(localFile)) {
                    fos.write(content);
                }

                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    String ext = getExtension(file.name);
                    String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                    if (mimeType != null) {
                        Intent intent = new Intent(this, MediaPreviewActivity.class);
                        Uri fileUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", localFile);
                        intent.setDataAndType(fileUri, mimeType);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(intent);
                    }
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, String.format(getString(R.string.msg_download_fail), e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private String getExtension(String name) {
        int i = name.lastIndexOf('.');
        if (i > 0) return name.substring(i + 1).toLowerCase();
        return "";
    }

    private static class UploadItem {
        final Uri uri;
        final String remotePath;
        UploadItem(Uri uri, String remotePath) {
            this.uri = uri;
            this.remotePath = remotePath;
        }
    }

    private void openEditor(SftpFile file) {
        if (!swipeRefresh.isRefreshing()) progressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                ensureConnected();
                String fullPath = getFullPath(file.name);
                File localFile = new File(getCacheDir(), file.name);
                
                String cmd = String.format(CommandConstants.CMD_CAT_BASE64, fullPath);
                String base64Content = sshNative.exec(sshHandle, cmd);
                
                if (file.size > 1024 * 1024) {
                    mainHandler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, getString(R.string.msg_file_too_large), Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                
                byte[] content = Base64.decode(base64Content.replace("\n", ""), Base64.NO_WRAP);
                try (FileOutputStream fos = new FileOutputStream(localFile)) {
                    fos.write(content);
                }
                
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Intent intent = new Intent(this, EditorActivity.class);
                    intent.putExtra("path", localFile.getAbsolutePath());
                    intent.putExtra("remotePath", fullPath);
                    startActivityForResult(intent, REQUEST_CODE_EDIT);
                });
                
            } catch (Exception e) {
                 mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, String.format(getString(R.string.msg_download_fail), e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void enqueueMultiDownload(Set<SftpFile> selected) {
        for (SftpFile f : selected) {
            if (f.isDir) continue;
            enqueueDownload(f);
        }
    }

    private void enqueueDownload(SftpFile file) {
        String downloadPath = prefs.getString("file_download_path", "/storage/emulated/0/Download");
        File destDir = new File(downloadPath);
        if (!destDir.exists()) destDir.mkdirs();
        File destFile = new File(destDir, file.name);
        String fullPath = getFullPath(file.name);
        SftpTransferService.enqueueTransfer(this, SftpTransferService.TYPE_DOWNLOAD, file.name, fullPath, Math.max(file.size, 0), null, destFile, true, false, hostname, port, username, password, authType, keyPath);
    }

    private void enqueueUpload(Uri uri) {
        String fileName = getFileName(uri);
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = "upload_" + System.currentTimeMillis();
        }
        long size = getUriSize(uri);
        String remotePath = getFullPath(fileName);
        SftpTransferService.enqueueTransfer(this, SftpTransferService.TYPE_UPLOAD_URI, fileName, remotePath, size, uri, null, true, false, hostname, port, username, password, authType, keyPath);
    }

    private void enqueueUpload(Uri uri, String remotePath, boolean showToast) {
        String name = getFileName(uri);
        if (name == null || name.trim().isEmpty()) {
            name = "upload_" + System.currentTimeMillis();
        }
        long size = getUriSize(uri);
        SftpTransferService.enqueueTransfer(this, SftpTransferService.TYPE_UPLOAD_URI, name, remotePath, size, uri, null, showToast, false, hostname, port, username, password, authType, keyPath);
    }

    private void enqueueUpload(File local, String remotePath, boolean showToast) {
        String name = local.getName();
        SftpTransferService.enqueueTransfer(this, SftpTransferService.TYPE_UPLOAD_FILE, name, remotePath, local.length(), null, local, showToast, true, hostname, port, username, password, authType, keyPath);
    }

    private void requestCancelActiveTransfer() {
        SftpTransferService.cancelActive(this);
    }

    private long getUriSize(Uri uri) {
        long size = 0;
        if (uri == null) return 0;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (index >= 0 && !cursor.isNull(index)) {
                        size = cursor.getLong(index);
                    }
                }
            } catch (Exception ignored) {
            }
        } else if ("file".equals(uri.getScheme())) {
            File f = new File(uri.getPath());
            if (f.exists()) size = f.length();
        }
        return size;
    }

    private void showReloadRestartPrompt(String remotePath) {
        String label = remotePath == null ? "" : remotePath;
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.action_edit))
            .setMessage(getString(R.string.msg_reload_restart_prompt, label))
            .setPositiveButton(getString(R.string.action_reload), (d, w) -> executeSimpleCommand(CommandConstants.CMD_RELOAD))
            .setNegativeButton(getString(R.string.action_restart), (d, w) -> executeSimpleCommand(CommandConstants.CMD_RESTART))
            .setNeutralButton(getString(R.string.action_cancel), null)
            .show();
    }

    private void executeSimpleCommand(String command) {
        executor.execute(() -> {
            try {
                ensureConnected();
                sshNative.exec(sshHandle, command);
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.msg_command_sent), Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, String.format(getString(R.string.msg_error), e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void openInTerminal(SftpFile file) {
        String targetPath = file.isDir ? getFullPath(file.name) : currentPath;
        Intent intent = new Intent(this, SshTerminalActivity.class);
        intent.putExtra("hostname", hostname);
        intent.putExtra("username", username);
        intent.putExtra("port", port);
        intent.putExtra("password", password);
        intent.putExtra("auth_type", authType);
        intent.putExtra("key_path", keyPath);
        intent.putExtra("initial_command", String.format(CommandConstants.CMD_CD, escapePath(targetPath)));
        startActivity(intent);
    }

    private void showFileActionMenu(SftpFile file, View anchor) {
        if (".".equals(file.name) || "..".equals(file.name)) return;

        String ext = getExtension(file.name);

        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, getString(R.string.action_open_in_terminal));
        if (!file.isDir && isTextFile(ext)) {
            popup.getMenu().add(0, 3, 0, getString(R.string.action_edit));
        }
        if (!file.isDir) {
            popup.getMenu().add(0, 4, 0, getString(R.string.action_download));
        }
        popup.getMenu().add(0, 2, 0, getString(R.string.action_copy_path));
        popup.getMenu().add(0, 5, 0, getString(R.string.action_rename));
        popup.getMenu().add(0, 6, 0, getString(R.string.action_delete));
        
        popup.getMenu().add(0, 7, 0, getString(R.string.action_compress));
        
        if (!file.isDir && (ext.equals("zip") || ext.equals("tar") || ext.equals("gz") || ext.equals("tgz") || ext.equals("rar") || ext.equals("7z"))) {
            popup.getMenu().add(0, 8, 0, getString(R.string.action_extract));
        }
        
        String fullPath = getFullPath(file.name);
        if (pinnedFiles.contains(fullPath)) {
            popup.getMenu().add(0, 9, 0, getString(R.string.action_unpin));
        } else {
            popup.getMenu().add(0, 9, 0, getString(R.string.action_pin));
        }
        
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                openInTerminal(file);
            } else if (id == 2) {
                copyPath(file);
            } else if (id == 3) {
                openEditor(file);
            } else if (id == 4) {
                downloadFile(file);
            } else if (id == 5) {
                showRenameDialog(file);
            } else if (id == 6) {
                showDeleteConfirmDialog(file);
            } else if (id == 7) {
                Set<SftpFile> set = new HashSet<>();
                set.add(file);
                showMultiCompressDialog(set);
            } else if (id == 8) {
                unzipFile(file);
            } else if (id == 9) {
                togglePin(file);
            }
            return true;
        });
        popup.show();
    }

    private String getFullPath(String name) {
        return (currentPath.endsWith("/") ? currentPath : currentPath + "/") + name;
    }

    private void copyPath(SftpFile file) {
        String path = getFullPath(file.name);
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Path", path);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, String.format(getString(R.string.msg_path_copied), path), Toast.LENGTH_SHORT).show();
    }

    private void togglePin(SftpFile file) {
        String path = getFullPath(file.name);
        if (pinnedFiles.contains(path)) {
            pinnedFiles.remove(path);
        } else {
            pinnedFiles.add(path);
        }
        prefs.edit().putStringSet("pinned_files", pinnedFiles).apply();
        loadFiles(currentPath);
    }

    private void showSortDialog() {
        String[] items = getResources().getStringArray(R.array.file_sort_options);
        new AlertDialog.Builder(this)
            .setTitle(R.string.action_sort)
            .setSingleChoiceItems(items, sortMode, (dialog, which) -> {
                sortMode = which;
                prefs.edit().putInt("file_sort_order", sortMode).apply();
                loadFiles(currentPath);
                dialog.dismiss();
            })
            .show();
    }

    private void showGoToDialog() {
        final EditText input = new EditText(this);
        input.setText(currentPath);
        new AlertDialog.Builder(this)
            .setTitle(R.string.action_goto)
            .setView(input)
            .setPositiveButton(R.string.action_ok, (dialog, which) -> {
                String path = input.getText().toString().trim();
                if (!path.isEmpty()) {
                    loadFiles(path);
                }
            })
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void showCreateDialog(String title, boolean isFolder) {
        final EditText input = new EditText(this);
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(R.string.action_ok, (dialog, which) -> {
                String name = input.getText().toString().trim();
                if (!name.isEmpty()) {
                    if (isFolder) {
                        createFolder(name);
                    } else {
                        createFile(name);
                    }
                }
            })
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void createFolder(String name) {
        String fullPath = getFullPath(name);
        performCommand(String.format(CommandConstants.CMD_MKDIR_P_QUOTED, fullPath), getString(R.string.msg_folder_created));
    }

    private void createFile(String name) {
        String fullPath = getFullPath(name);
        performCommand(String.format(CommandConstants.CMD_TOUCH_QUOTED, fullPath), getString(R.string.msg_file_created));
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQUEST_CODE_UPLOAD);
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CODE_UPLOAD_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_UPLOAD && resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    uploadFile(uri);
                }
            } else if (data.getData() != null) {
                uploadFile(data.getData());
            }
        } else if (requestCode == REQUEST_CODE_UPLOAD_FOLDER && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                try {
                    getContentResolver().takePersistableUriPermission(uri, flags);
                } catch (SecurityException ignored) {
                }
                uploadFolder(uri);
            }
        } else if (requestCode == REQUEST_CODE_EDIT && resultCode == RESULT_OK && data != null) {
            String localPath = data.getStringExtra("path");
            String remotePath = data.getStringExtra("remotePath");
            if (localPath != null && remotePath != null) {
                uploadLocalFile(new File(localPath), remotePath);
            }
        }
    }

    private void uploadLocalFile(File local, String remotePath) {
        enqueueUpload(local, remotePath, true);
    }

    private void unzipFile(SftpFile file) {
        String ext = getExtension(file.name);
        String fullPath = getFullPath(file.name);
        String defaultTarget = prefs.getString("file_unzip_path", currentPath);
        if (defaultTarget == null || defaultTarget.isEmpty()) defaultTarget = currentPath;
        
        final EditText input = new EditText(this);
        input.setText(defaultTarget);
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_extract_title)
            .setView(input)
            .setPositiveButton(R.string.action_ok, (dialog, which) -> {
                String target = input.getText().toString();
                String command = "";
                if (ext.equals("zip")) {
                    command = String.format(CommandConstants.CMD_UNZIP_TO, fullPath, target);
                } else if (ext.equals("tar") || ext.equals("gz") || ext.equals("tgz")) {
                    command = String.format(CommandConstants.CMD_TAR_GZ_TO, fullPath, target);
                } else if (ext.equals("rar")) {
                    command = String.format(CommandConstants.CMD_UNRAR_TO, fullPath, target);
                } else if (ext.equals("7z")) {
                    command = String.format(CommandConstants.CMD_7Z_TO, fullPath, target);
                }
                
                if (!command.isEmpty()) {
                    performCommand(command, getString(R.string.msg_extract_task_submitted));
                }
            })
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void downloadFile(SftpFile file) {
        enqueueDownload(file);
    }

    private void uploadFile(Uri uri) {
        enqueueUpload(uri);
    }

    private void uploadFolder(Uri treeUri) {
        executor.execute(() -> {
            try {
                ensureConnected();
                androidx.documentfile.provider.DocumentFile root = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, treeUri);
                if (root == null) {
                    mainHandler.post(() -> Toast.makeText(this, getString(R.string.msg_upload_folder_invalid), Toast.LENGTH_SHORT).show());
                    return;
                }
                String folderName = root.getName();
                if (folderName == null || folderName.trim().isEmpty()) {
                    folderName = "folder_" + System.currentTimeMillis();
                }
                String remoteBase = getFullPath(folderName);
                sshNative.exec(sshHandle, String.format(CommandConstants.CMD_MKDIR_P_QUOTED, remoteBase));
                List<UploadItem> items = new ArrayList<>();
                collectUploadItems(root, remoteBase, items);
                int finalCount = items.size();
                mainHandler.post(() -> {
                    Toast.makeText(this, String.format(getString(R.string.msg_upload_folder_success), finalCount), Toast.LENGTH_SHORT).show();
                    loadFiles(currentPath);
                });
                for (UploadItem item : items) {
                    enqueueUpload(item.uri, item.remotePath, false);
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this, String.format(getString(R.string.msg_upload_fail), e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void collectUploadItems(androidx.documentfile.provider.DocumentFile dir, String remoteDir, List<UploadItem> items) throws Exception {
        if (!dir.isDirectory()) {
            return;
        }
        for (androidx.documentfile.provider.DocumentFile child : dir.listFiles()) {
            String name = child.getName();
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            if (child.isDirectory()) {
                String childRemote = remoteDir + "/" + name;
                sshNative.exec(sshHandle, String.format(CommandConstants.CMD_MKDIR_P_QUOTED, childRemote));
                collectUploadItems(child, childRemote, items);
            } else {
                String childRemote = remoteDir + "/" + name;
                items.add(new UploadItem(child.getUri(), childRemote));
            }
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) result = cursor.getString(index);
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) result = result.substring(cut + 1);
        }
        return result;
    }

    private void showRenameDialog(SftpFile file) {
        final EditText input = new EditText(this);
        input.setText(file.name);
        new AlertDialog.Builder(this)
            .setTitle(R.string.action_rename)
            .setView(input)
            .setPositiveButton(R.string.action_ok, (dialog, which) -> {
                String newName = input.getText().toString().trim();
                if (!newName.isEmpty() && !newName.equals(file.name)) {
                    renameFile(file, newName);
                }
            })
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void renameFile(SftpFile file, String newName) {
        String oldPath = getFullPath(file.name);
        String newPath = getFullPath(newName);
        performCommand(String.format(CommandConstants.CMD_MV_QUOTED, oldPath, newPath), getString(R.string.msg_rename_success));
    }

    private void showDeleteConfirmDialog(SftpFile file) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(String.format(getString(R.string.msg_confirm_delete), file.name))
            .setPositiveButton(R.string.action_delete, (dialog, which) -> deleteFile(file))
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void deleteFile(SftpFile file) {
        String fullPath = getFullPath(file.name);
        String cmd = String.format(CommandConstants.CMD_RM_RF_QUOTED, fullPath);
        performCommand(cmd, getString(R.string.msg_delete_success));
    }

    private void performCommand(String cmd, String successMsg) {
        if (!swipeRefresh.isRefreshing()) progressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            try {
                ensureConnected();
                sshNative.exec(sshHandle, cmd);
                mainHandler.post(() -> {
                    Toast.makeText(SftpActivity.this, successMsg, Toast.LENGTH_SHORT).show();
                    loadFiles(currentPath); // Refresh
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(SftpActivity.this, String.format(getString(R.string.msg_operation_fail), e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void handlePaste() {
        if (clipboardFiles.isEmpty()) return;
        
        String targetDir = currentPath;
        List<String> sources = new ArrayList<>(clipboardFiles);
        boolean cut = isCutOperation;
        
        if (!swipeRefresh.isRefreshing()) progressBar.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            try {
                ensureConnected();
                for (String src : sources) {
                    String cmd;
                    if (cut) {
                        cmd = String.format(CommandConstants.CMD_MV, escapePath(src), escapePath(targetDir));
                    } else {
                        cmd = String.format(CommandConstants.CMD_CP_R, escapePath(src), escapePath(targetDir));
                    }
                    sshNative.exec(sshHandle, cmd);
                }
                
                runOnUiThread(() -> {
                    if (cut) {
                        clipboardFiles.clear();
                        isCutOperation = false;
                        invalidateOptionsMenu();
                    }
                    Toast.makeText(this, getString(R.string.msg_paste_success), Toast.LENGTH_SHORT).show();
                    loadFiles(targetDir);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, String.format(getString(R.string.msg_error), e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showMultiDeleteDialog(Set<SftpFile> selected) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(String.format(getString(R.string.msg_delete_n_files), selected.size()))
            .setMessage(getString(R.string.msg_delete_warning))
            .setPositiveButton(R.string.action_delete, (d, w) -> {
                executor.execute(() -> {
                    try {
                        StringBuilder cmd = new StringBuilder(CommandConstants.CMD_RM_RF_PREFIX);
                        for (SftpFile f : selected) {
                            String path = (currentPath.endsWith("/") ? currentPath : currentPath + "/") + f.name;
                            cmd.append(" ").append(escapePath(path));
                        }
                        sshNative.exec(sshHandle, cmd.toString());
                        runOnUiThread(() -> {
                            Toast.makeText(this, getString(R.string.msg_delete_success), Toast.LENGTH_SHORT).show();
                            if (actionMode != null) actionMode.finish();
                            loadFiles(currentPath);
                        });
                    } catch (Exception e) {
                         runOnUiThread(() -> Toast.makeText(this, String.format(getString(R.string.msg_error), e.getMessage()), Toast.LENGTH_SHORT).show());
                    }
                });
            })
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }

    private void showMultiCompressDialog(Set<SftpFile> selected) {
        final EditText input = new EditText(this);
        input.setText(getString(R.string.file_action_compress_default_name));
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_compress_title)
            .setView(input)
            .setPositiveButton(R.string.action_compress, (d, w) -> {
                String name = input.getText().toString();
                if (name.isEmpty()) return;
                
                executor.execute(() -> {
                    try {
                        StringBuilder cmd = new StringBuilder(String.format(CommandConstants.CMD_TAR_COMPRESS, escapePath(currentPath), escapePath(name)));
                        for (SftpFile f : selected) {
                            cmd.append(" ").append(escapePath(f.name));
                        }
                        sshNative.exec(sshHandle, cmd.toString());
                        runOnUiThread(() -> {
                            Toast.makeText(this, getString(R.string.msg_compress_success), Toast.LENGTH_SHORT).show();
                            if (actionMode != null) actionMode.finish();
                            loadFiles(currentPath);
                        });
                    } catch (Exception e) {
                         runOnUiThread(() -> Toast.makeText(this, String.format(getString(R.string.msg_error), e.getMessage()), Toast.LENGTH_SHORT).show());
                    }
                });
            })
            .setNegativeButton(R.string.action_cancel, null)
            .show();
    }
    
    private String escapePath(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
    }

    private void ensureConnected() throws Exception {
        if (sshHandle != 0) {
            markSftpSessionConnected();
            return;
        }
        Exception freshError = null;
        if (preferDedicatedConnection) {
            try {
                SessionConnector.Connection fresh = SessionConnector.connectFresh(
                    sshNative,
                    hostname,
                    port,
                    username,
                    password,
                    authType,
                    keyPath,
                    getString(R.string.err_connect_fail),
                    getString(R.string.err_auth_fail)
                );
                sshHandle = fresh.getHandle();
                isSharedSession = false;
                markSftpSessionConnected();
                return;
            } catch (Exception e) {
                freshError = e;
            }
        }

        try {
            SessionConnector.Connection reused = SessionConnector.acquire(
                sshNative,
                hostname,
                port,
                username,
                password,
                authType,
                keyPath,
                getString(R.string.err_connect_fail),
                getString(R.string.err_auth_fail)
            );
            sshHandle = reused.getHandle();
            isSharedSession = reused.isShared();
            markSftpSessionConnected();
        } catch (Exception e) {
            if (freshError != null) {
                throw freshError;
            }
            throw e;
        }
    }
    
    private void loadFiles(String path) {
        final String targetPath = (path == null || path.trim().isEmpty()) ? "/" : path;
        boolean isPullRefresh = swipeRefresh != null && swipeRefresh.isRefreshing();
        lastRequestedPath = targetPath;
        if (!isPullRefresh) {
            progressBar.setVisibility(View.VISIBLE);
            uiStateController.showLoading(getString(R.string.sftp_state_loading_message));
        }
        int token = loadSequence.incrementAndGet();
        activeLoadToken = token;
        
        executor.execute(() -> {
            try {
                ensureConnected();
                
                String response = sftpRepository.fetchListResponse(sshHandle, targetPath);
                if ((!sftpRepository.isValidListResponse(response)
                        || ("[]".equals(response == null ? null : response.trim()) && isSharedSession))
                        && isSharedSession) {
                    isSharedSession = false;
                    sshHandle = 0;
                    preferDedicatedConnection = true;
                    ensureConnected();
                    response = sftpRepository.fetchListResponse(sshHandle, targetPath);
                }
                if (!sftpRepository.isValidListResponse(response)) {
                    if (sshHandle != 0 && !isSharedSession) {
                        try {
                            sshNative.disconnect(sshHandle);
                        } catch (Exception ignored) {
                        }
                    }
                    sshHandle = 0;
                    isSharedSession = false;
                    preferDedicatedConnection = true;
                    markSftpSessionDisconnected();
                    throw new Exception("SFTP list failed");
                }
                
                List<SftpFile> list = new ArrayList<>();
                for (SftpFile file : sftpRepository.parseList(response)) {
                    // Filter hidden files
                    if (!showHidden && file.name.startsWith(".") && !file.name.equals("..")) {
                        continue;
                    }
                    list.add(file);
                }
                
                // Sort
                Collections.sort(list, (o1, o2) -> {
                    // 1. Pinned
                    String p1Path = (targetPath.endsWith("/") ? targetPath : targetPath + "/") + o1.name;
                    String p2Path = (targetPath.endsWith("/") ? targetPath : targetPath + "/") + o2.name;
                    boolean p1 = pinnedFiles.contains(p1Path);
                    boolean p2 = pinnedFiles.contains(p2Path);
                    if (p1 && !p2) return -1;
                    if (!p1 && p2) return 1;

                    // 2. Folders first
                    if (o1.isDir && !o2.isDir) return -1;
                    if (!o1.isDir && o2.isDir) return 1;

                    // 3. Sort mode
                    int result = 0;
                    switch (sortMode) {
                        case 0: result = o1.name.compareToIgnoreCase(o2.name); break;
                        case 1: result = o2.name.compareToIgnoreCase(o1.name); break;
                        case 2: result = Long.compare(o1.size, o2.size); break;
                        case 3: result = Long.compare(o2.size, o1.size); break;
                    }
                    return result;
                });
                
                mainHandler.post(() -> {
                    if (token != activeLoadToken) return;
                    progressBar.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    currentPath = targetPath;
                    getSupportActionBar().setSubtitle(currentPath);
                    updatePathSegments();
                    if (list.size() <= LIST_PAGE_SIZE) {
                        adapter.setFiles(list);
                        clearPendingFiles();
                    } else {
                        List<SftpFile> first = new ArrayList<>(list.subList(0, LIST_PAGE_SIZE));
                        adapter.setFiles(first);
                        pendingFiles = list;
                        pendingIndex = LIST_PAGE_SIZE;
                        pendingToken = token;
                        isAppendingPage = false;
                    }
                    if (list.isEmpty()) {
                        uiStateController.showEmpty(getString(R.string.sftp_state_empty_message, targetPath));
                    } else {
                        uiStateController.showContent();
                    }
                });
                
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (token != activeLoadToken) return;
                    progressBar.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    uiStateController.showError(e.getMessage());
                    Toast.makeText(SftpActivity.this, String.format(getString(R.string.msg_error), e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void scheduleRemainingFiles(List<SftpFile> list, int start, int token) {
        if (token != activeLoadToken) return;
        if (list == null || list.isEmpty()) return;
        int total = list.size();
        if (start >= total) return;
        int end = Math.min(total, start + LIST_PAGE_SIZE);
        List<SftpFile> chunk = new ArrayList<>(list.subList(start, end));
        adapter.appendFiles(chunk);
        if (end < total) {
            mainHandler.post(() -> scheduleRemainingFiles(list, end, token));
        }
    }

    private void maybeAppendNextPage() {
        if (pendingFiles == null || pendingFiles.isEmpty()) return;
        if (pendingToken != activeLoadToken) {
            clearPendingFiles();
            return;
        }
        if (isAppendingPage) return;
        RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
        if (!(lm instanceof androidx.recyclerview.widget.LinearLayoutManager)) return;
        int lastVisible = ((androidx.recyclerview.widget.LinearLayoutManager) lm).findLastVisibleItemPosition();
        if (lastVisible < 0) return;
        if (lastVisible < adapter.getItemCount() - LIST_PAGE_PREFETCH) return;
        appendNextPage();
    }

    private void appendNextPage() {
        if (pendingFiles == null) return;
        if (pendingIndex >= pendingFiles.size()) {
            clearPendingFiles();
            return;
        }
        isAppendingPage = true;
        int end = Math.min(pendingFiles.size(), pendingIndex + LIST_PAGE_SIZE);
        List<SftpFile> chunk = new ArrayList<>(pendingFiles.subList(pendingIndex, end));
        pendingIndex = end;
        adapter.appendFiles(chunk);
        isAppendingPage = false;
        if (pendingIndex >= pendingFiles.size()) {
            clearPendingFiles();
        }
    }

    private void clearPendingFiles() {
        pendingFiles = null;
        pendingIndex = 0;
        pendingToken = 0;
        isAppendingPage = false;
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sshHandle != 0 && sftpSessionId > 0) {
            SessionManager.getInstance().putSharedHandle(sftpSessionId, sshHandle);
            sshHandle = 0;
            isSharedSession = true;
        }
        executor.shutdownNow();
    }

    private long resolveSftpSessionId(long hintedSessionId) {
        SessionManager manager = SessionManager.getInstance();
        if (hintedSessionId > 0 && manager.getTerminalSession(hintedSessionId) == null) {
            return hintedSessionId;
        }
        long endpointId = buildEndpointSessionId(hostname, port, username);
        if (endpointId > 0) {
            return endpointId;
        }
        if (hostId > 0) {
            return (hostId << 1) | 1L;
        }
        return System.currentTimeMillis();
    }

    private long buildEndpointSessionId(String host, int endpointPort, String user) {
        if (TextUtils.isEmpty(host) || endpointPort <= 0) {
            return -1L;
        }
        String normalizedHost = host.trim().toLowerCase();
        String normalizedUser = user == null ? "" : user.trim().toLowerCase();
        String key = normalizedHost + "|" + endpointPort + "|" + normalizedUser;
        long hash = 1469598103934665603L;
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= 1099511628211L;
        }
        hash &= Long.MAX_VALUE;
        if (hash == 0L) {
            hash = 1L;
        }
        return hash;
    }

    private void markSftpSessionConnected() {
        if (sftpSessionConnected) {
            return;
        }
        SessionInfo info = buildSftpSessionInfo(true);
        if (info == null) {
            return;
        }
        SessionManager.getInstance().upsertSftpSession(info);
        sftpSessionConnected = true;
    }

    private void markSftpSessionDisconnected() {
        if (!sftpSessionConnected) {
            return;
        }
        SessionInfo info = buildSftpSessionInfo(false);
        if (info != null) {
            SessionManager.getInstance().upsertSftpSession(info);
        }
        sftpSessionConnected = false;
    }

    @Nullable
    private SessionInfo buildSftpSessionInfo(boolean connected) {
        if (TextUtils.isEmpty(hostname) || port <= 0) {
            return null;
        }
        if (sftpSessionId <= 0) {
            sftpSessionId = resolveSftpSessionId(-1L);
        }
        String name;
        if (TextUtils.isEmpty(username)) {
            name = hostname;
        } else {
            name = username + "@" + hostname;
        }
        return new SessionInfo(
            sftpSessionId,
            name,
            hostname,
            port,
            username,
            password,
            authType,
            keyPath,
            connected
        );
    }

}
