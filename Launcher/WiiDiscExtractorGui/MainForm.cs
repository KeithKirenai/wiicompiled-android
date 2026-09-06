using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;
using WiiDiscExtractor;

namespace WiiCompiled.Android.Builder;

internal static class Program
{
    [STAThread]
    static void Main()
    {
        try
        {
            ApplicationConfiguration.Initialize();
            Application.Run(new MainForm());
        }
        catch (Exception ex)
        {
            File.WriteAllText("gui_crash.txt", ex.ToString());
            MessageBox.Show(ex.ToString(), "Startup Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }
}

internal static class ControlExtensions
{
    public static void InvokeIfRequired(this Control control, Action action)
    {
        if (control.IsDisposed) return;
        if (control.InvokeRequired)
            control.Invoke(action);
        else
            action();
    }
}

public sealed class MainForm : Form
{
    private readonly string _workspaceRoot;
    private ToolchainStatus _toolchain;
    private CancellationTokenSource? _cts;
    private bool _isBuilding;
    private string? _lastBuiltApk;

    // Controls
    private readonly Label _sdkStatus = new() { AutoSize = true };
    private readonly Label _ndkStatus = new() { AutoSize = true };
    private readonly Label _toolsStatus = new() { AutoSize = true };
    private readonly Label _deviceStatus = new() { AutoSize = true };
    private readonly Button _refreshToolsBtn = new() { Text = "↻ Re-scan", Width = 80, Height = 26 };

    private readonly TextBox _imagePathTxt = new() { ReadOnly = true, Width = 520 };
    private readonly Button _browseBtn = new() { Text = "Browse Image…", Width = 130, Height = 28 };
    private readonly Label _discInspectionLabel = new() { AutoSize = true, Text = "Select your MKW PAL (.iso / .wbfs) disc dump, or drag & drop it here." };

    private readonly RadioButton _releaseRadio = new() { Text = "Release (Recommended for gaming)", Checked = true, AutoSize = true };
    private readonly RadioButton _debugRadio = new() { Text = "Debug (Developer build)", AutoSize = true };
    private readonly CheckBox _fastBuildCheck = new() { Text = "Fast Build (Reuse compiled native shards)", Checked = true, AutoSize = true };
    private readonly CheckBox _forceTranslateCheck = new() { Text = "Force re-translate PowerPC code", Checked = false, AutoSize = true };
    private readonly CheckBox _autoInstallCheck = new() { Text = "Install APK to phone over ADB upon completion", Checked = false, AutoSize = true };

    private readonly Button _buildBtn = new() { Text = "Build APK", Width = 150, Height = 40, Font = new Font(Control.DefaultFont.FontFamily, 11f, FontStyle.Bold), BackColor = Color.FromArgb(0, 120, 215), ForeColor = Color.White, FlatStyle = FlatStyle.Flat };
    private readonly Button _cancelBtn = new() { Text = "Cancel", Width = 100, Height = 40, Enabled = false };
    private readonly Button _openApkBtn = new() { Text = "Open APK Folder", Width = 140, Height = 40, Visible = false };

    private readonly ProgressBar _progressBar = new() { Height = 24, Dock = DockStyle.Bottom };
    private readonly Label _statusLabel = new() { Text = "Ready.", Dock = DockStyle.Bottom, Height = 24, TextAlign = ContentAlignment.MiddleLeft, Font = new Font(Control.DefaultFont.FontFamily, 9.5f, FontStyle.Bold) };
    private readonly TextBox _logBox = new() { Multiline = true, ReadOnly = true, ScrollBars = ScrollBars.Vertical, Dock = DockStyle.Fill, BackColor = Color.FromArgb(28, 28, 28), ForeColor = Color.FromArgb(220, 220, 220), Font = new Font("Consolas", 9f) };

    public MainForm()
    {
        Text = "WiiCompiled Android Builder — Mario Kart Wii (RMCP01)";
        Size = new Size(860, 720);
        MinimumSize = new Size(760, 600);
        StartPosition = FormStartPosition.CenterScreen;
        AllowDrop = true;

        _workspaceRoot = ResolveWorkspaceRoot();
        _toolchain = ToolchainDetector.Detect(_workspaceRoot);

        InitializeLayout();
        RefreshToolchainDisplay();
        CheckExistingAssets();

        DragEnter += OnDragEnter;
        DragDrop += OnDragDrop;
    }

    private static string ResolveWorkspaceRoot()
    {
        string current = AppContext.BaseDirectory;
        while (!string.IsNullOrEmpty(current))
        {
            if (Directory.Exists(Path.Combine(current, "android")) &&
                Directory.Exists(Path.Combine(current, "projects")))
            {
                return current;
            }
            current = Directory.GetParent(current)?.FullName ?? "";
        }
        return Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "..", "..", "..", ".."));
    }

    private void InitializeLayout()
    {
        var mainContainer = new Panel { Dock = DockStyle.Fill, Padding = new Padding(14) };

        // 1. Header
        var headerPanel = new Panel { Dock = DockStyle.Top, Height = 56 };
        var title = new Label
        {
            Text = "WiiCompiled Android Builder",
            Font = new Font(Font.FontFamily, 15f, FontStyle.Bold),
            ForeColor = Color.FromArgb(20, 20, 20),
            AutoSize = true,
            Location = new Point(0, 0)
        };
        var subtitle = new Label
        {
            Text = "Ahead-of-time static recompilation pipeline for Android (arm64-v8a).",
            Font = new Font(Font.FontFamily, 9f),
            ForeColor = Color.Gray,
            AutoSize = true,
            Location = new Point(0, 30)
        };
        headerPanel.Controls.AddRange(new Control[] { title, subtitle });

        // ToolTips
        var toolTip = new ToolTip
        {
            AutoPopDelay = 10000,
            InitialDelay = 300,
            ReshowDelay = 200,
            ShowAlways = true
        };

        toolTip.SetToolTip(_releaseRadio, "Optimized Release build. Recommended for smooth gameplay and optimal FPS.");
        toolTip.SetToolTip(_debugRadio, "Debug build with symbols and assertions enabled. Useful for development and troubleshooting.");
        toolTip.SetToolTip(_fastBuildCheck, "Reuses the previously compiled native C++ library (libmkw_base_shared.a) if it already exists.\nUncheck this if you modified native runtime sources or need a clean rebuild.");
        toolTip.SetToolTip(_forceTranslateCheck, "What it does: Forces the .NET translator to completely re-parse main.dol + StaticR.rel and re-generate all ~29,000 C++ functions in generated/build_shards/.\nBy default, translation only runs once because generated code is static unless the recompilation rules or translator itself change.");
        toolTip.SetToolTip(_autoInstallCheck, "Automatically pushes and installs the finished APK onto your USB-connected Android phone via ADB.");
        toolTip.SetToolTip(_browseBtn, "Select your dumped Mario Kart Wii PAL disc (.iso or .wbfs).");
        toolTip.SetToolTip(_refreshToolsBtn, "Re-scan the system for Android SDK, NDK, CMake, Ninja, and connected devices.");
        toolTip.SetToolTip(_buildBtn, "Start the automated build pipeline (extract -> translate -> compile -> package APK).");

        // 2. Preflight Group
        var preflightGroup = new GroupBox
        {
            Text = "Environment & Toolchain Preflight",
            Dock = DockStyle.Top,
            Height = 100,
            Font = new Font(Font.FontFamily, 9f, FontStyle.Bold)
        };
        var pfLayout = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.TopDown,
            Font = new Font(Font.FontFamily, 8.5f, FontStyle.Regular),
            Padding = new Padding(8, 4, 8, 4)
        };
        var row1 = new FlowLayoutPanel { AutoSize = true, FlowDirection = FlowDirection.LeftToRight };
        row1.Controls.AddRange(new Control[] { _sdkStatus, new Label { Width = 30 }, _ndkStatus, new Label { Width = 30 }, _toolsStatus });
        var row2 = new FlowLayoutPanel { AutoSize = true, FlowDirection = FlowDirection.LeftToRight };
        row2.Controls.AddRange(new Control[] { _deviceStatus, new Label { Width = 20 }, _refreshToolsBtn });
        pfLayout.Controls.AddRange(new Control[] { row1, row2 });
        preflightGroup.Controls.Add(pfLayout);
        _refreshToolsBtn.Click += (_, _) =>
        {
            _toolchain = ToolchainDetector.Detect(_workspaceRoot);
            RefreshToolchainDisplay();
        };

        // 3. Disc Source Group
        var discGroup = new GroupBox
        {
            Text = "Game Disc Image (.iso / .wbfs)",
            Dock = DockStyle.Top,
            Height = 90,
            Font = new Font(Font.FontFamily, 9f, FontStyle.Bold)
        };
        var discFlow = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.TopDown,
            Font = new Font(Font.FontFamily, 8.5f, FontStyle.Regular),
            Padding = new Padding(8, 6, 8, 4)
        };
        var browseRow = new FlowLayoutPanel { AutoSize = true, FlowDirection = FlowDirection.LeftToRight };
        browseRow.Controls.AddRange(new Control[] { _imagePathTxt, _browseBtn });
        discFlow.Controls.AddRange(new Control[] { browseRow, _discInspectionLabel });
        discGroup.Controls.Add(discFlow);

        _browseBtn.Click += (_, _) => PickImage();

        // 4. Build Options Group
        var optionsGroup = new GroupBox
        {
            Text = "Build Options",
            Dock = DockStyle.Top,
            Height = 90,
            Font = new Font(Font.FontFamily, 9f, FontStyle.Bold)
        };
        var optFlow = new FlowLayoutPanel
        {
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.TopDown,
            Font = new Font(Font.FontFamily, 8.5f, FontStyle.Regular),
            Padding = new Padding(8, 4, 8, 4)
        };
        var radioRow = new FlowLayoutPanel { AutoSize = true, FlowDirection = FlowDirection.LeftToRight };
        radioRow.Controls.AddRange(new Control[] { _releaseRadio, new Label { Width = 20 }, _debugRadio });
        var checkRow = new FlowLayoutPanel { AutoSize = true, FlowDirection = FlowDirection.LeftToRight };
        checkRow.Controls.AddRange(new Control[] { _fastBuildCheck, new Label { Width = 15 }, _forceTranslateCheck, new Label { Width = 15 }, _autoInstallCheck });
        optFlow.Controls.AddRange(new Control[] { radioRow, checkRow });
        optionsGroup.Controls.Add(optFlow);

        // 5. Action Panel
        var actionPanel = new Panel { Dock = DockStyle.Top, Height = 54, Padding = new Padding(0, 6, 0, 6) };
        _buildBtn.Location = new Point(0, 6);
        _cancelBtn.Location = new Point(160, 6);
        _openApkBtn.Location = new Point(270, 6);
        actionPanel.Controls.AddRange(new Control[] { _buildBtn, _cancelBtn, _openApkBtn });

        _buildBtn.Click += (_, _) => StartBuildAsync();
        _cancelBtn.Click += (_, _) => CancelBuild();
        _openApkBtn.Click += (_, _) => OpenApkDirectory();

        // 6. Center Log Console & Progress Bar
        var logContainer = new Panel { Dock = DockStyle.Fill, Padding = new Padding(0, 6, 0, 0) };
        var bottomProgressPanel = new Panel { Dock = DockStyle.Bottom, Height = 56 };
        bottomProgressPanel.Controls.AddRange(new Control[] { _statusLabel, _progressBar });

        logContainer.Controls.AddRange(new Control[] { _logBox, bottomProgressPanel });

        // Add top panels in reverse order for DockStyle.Top
        mainContainer.Controls.Add(logContainer);
        mainContainer.Controls.Add(actionPanel);
        mainContainer.Controls.Add(optionsGroup);
        mainContainer.Controls.Add(discGroup);
        mainContainer.Controls.Add(preflightGroup);
        mainContainer.Controls.Add(headerPanel);

        Controls.Add(mainContainer);
    }

    private void RefreshToolchainDisplay()
    {
        _sdkStatus.Text = _toolchain.SdkPath != null ? "✔ Android SDK: OK" : "❌ Android SDK: Missing";
        _sdkStatus.ForeColor = _toolchain.SdkPath != null ? Color.DarkGreen : Color.Red;

        _ndkStatus.Text = _toolchain.NdkPath != null ? "✔ NDK (28.2+): OK" : "❌ NDK: Missing";
        _ndkStatus.ForeColor = _toolchain.NdkPath != null ? Color.DarkGreen : Color.Red;

        bool toolsOk = _toolchain.CMakePath != null && _toolchain.NinjaPath != null && _toolchain.DotNet8Available;
        _toolsStatus.Text = toolsOk ? "✔ CMake, Ninja, .NET 8: OK" : "❌ CMake/Ninja/.NET 8: Missing";
        _toolsStatus.ForeColor = toolsOk ? Color.DarkGreen : Color.Red;

        if (_toolchain.ConnectedDevices.Count > 0)
        {
            _deviceStatus.Text = $"✔ Device Connected: {_toolchain.ConnectedDevices[0]}";
            _deviceStatus.ForeColor = Color.DarkGreen;
            _autoInstallCheck.Enabled = true;
            _autoInstallCheck.Checked = true;
        }
        else
        {
            _deviceStatus.Text = "ℹ No ADB Device Connected (USB debugging)";
            _deviceStatus.ForeColor = Color.DimGray;
            _autoInstallCheck.Enabled = false;
            _autoInstallCheck.Checked = false;
        }
    }

    private void CheckExistingAssets()
    {
        string dol = Path.Combine(_workspaceRoot, "Assets", "main.dol");
        string rel = Path.Combine(_workspaceRoot, "Assets", "StaticR.rel");

        if (File.Exists(dol) && File.Exists(rel))
        {
            _discInspectionLabel.Text = "✔ Existing Mario Kart Wii PAL assets found staged in Assets/. You can build immediately or pick a new image.";
            _discInspectionLabel.ForeColor = Color.DarkGreen;
        }
    }

    private void PickImage()
    {
        using var dlg = new OpenFileDialog
        {
            Title = "Select Mario Kart Wii PAL Disc Image",
            Filter = "Wii Disc Images (*.iso;*.wbfs;*.gcm;*.gcz;*.ciso;*.chd;*.wia;*.rvz)|*.iso;*.wbfs;*.gcm;*.gcz;*.ciso;*.chd;*.wia;*.rvz|All files (*.*)|*.*",
            CheckFileExists = true
        };

        if (dlg.ShowDialog() == DialogResult.OK)
        {
            SetSelectedImage(dlg.FileName);
        }
    }

    private void SetSelectedImage(string path)
    {
        _imagePathTxt.Text = path;
        try
        {
            using var disc = new WiiDiscImage(path);
            var (gameId, magic) = disc.Inspect();
            if (gameId == WiiDiscImage.ExpectedGameId)
            {
                _discInspectionLabel.Text = $"✔ Verified: {gameId} (Mario Kart Wii PAL). Ready to extract and build!";
                _discInspectionLabel.ForeColor = Color.DarkGreen;
            }
            else
            {
                _discInspectionLabel.Text = $"⚠ Detected Game ID '{gameId}' (Expected {WiiDiscImage.ExpectedGameId}). Only clean PAL disc dumps are supported.";
                _discInspectionLabel.ForeColor = Color.OrangeRed;
            }
        }
        catch (Exception ex)
        {
            _discInspectionLabel.Text = $"⚠ Could not inspect header: {ex.Message}";
            _discInspectionLabel.ForeColor = Color.OrangeRed;
        }
    }

    private void OnDragEnter(object? sender, DragEventArgs e)
    {
        if (e.Data?.GetDataPresent(DataFormats.FileDrop) == true)
            e.Effect = DragDropEffects.Copy;
    }

    private void OnDragDrop(object? sender, DragEventArgs e)
    {
        if (e.Data?.GetData(DataFormats.FileDrop) is string[] files && files.Length > 0)
        {
            SetSelectedImage(files[0]);
        }
    }

    private async void StartBuildAsync()
    {
        if (_isBuilding) return;

        if (!_toolchain.HasRequiredTools)
        {
            MessageBox.Show(
                "Required build toolchain is missing. Please ensure Android SDK, NDK 28.2+, CMake, Ninja, and .NET 8 SDK are installed.",
                "Toolchain Missing", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            return;
        }

        string dol = Path.Combine(_workspaceRoot, "Assets", "main.dol");
        string rel = Path.Combine(_workspaceRoot, "Assets", "StaticR.rel");
        if (string.IsNullOrWhiteSpace(_imagePathTxt.Text) && (!File.Exists(dol) || !File.Exists(rel)))
        {
            MessageBox.Show(
                "Please select your Mario Kart Wii PAL (.iso or .wbfs) disc dump to proceed.",
                "Disc Image Required", MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }

        _isBuilding = true;
        _cts = new CancellationTokenSource();
        _buildBtn.Enabled = false;
        _cancelBtn.Enabled = true;
        _browseBtn.Enabled = false;
        _openApkBtn.Visible = false;
        _progressBar.Value = 0;
        _logBox.Clear();

        var options = new BuildOptions(
            _workspaceRoot,
            string.IsNullOrWhiteSpace(_imagePathTxt.Text) ? null : _imagePathTxt.Text,
            _releaseRadio.Checked,
            _fastBuildCheck.Checked,
            _forceTranslateCheck.Checked,
            _autoInstallCheck.Checked && _toolchain.ConnectedDevices.Count > 0,
            _toolchain.ConnectedDevices.Count > 0 ? _toolchain.ConnectedDevices[0] : null
        );

        var pipeline = new BuildPipelineService(
            logLine => this.InvokeIfRequired(() =>
            {
                _logBox.AppendText(logLine + Environment.NewLine);
            }),
            progress => this.InvokeIfRequired(() =>
            {
                _progressBar.Value = Math.Clamp(progress.Percentage, 0, 100);
                _statusLabel.Text = $"[{progress.Step}/{progress.TotalSteps}] {progress.Message}";
            })
        );

        try
        {
            _lastBuiltApk = await Task.Run(() => pipeline.RunAsync(options, _toolchain, _cts.Token));
            _statusLabel.Text = "Build Complete! APK ready.";
            _openApkBtn.Visible = true;
            MessageBox.Show($"APK build succeeded!\n\nLocation:\n{_lastBuiltApk}", "Build Complete", MessageBoxButtons.OK, MessageBoxIcon.Information);
        }
        catch (OperationCanceledException)
        {
            _statusLabel.Text = "Build cancelled by user.";
            _logBox.AppendText("\n[CANCELLED] Build stopped.\n");
        }
        catch (Exception ex)
        {
            _statusLabel.Text = "Build failed. Check log for details.";
            _logBox.AppendText($"\n[ERROR] {ex.Message}\n");
            MessageBox.Show($"Build failed: {ex.Message}", "Build Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
        finally
        {
            _isBuilding = false;
            _buildBtn.Enabled = true;
            _cancelBtn.Enabled = false;
            _browseBtn.Enabled = true;
            _cts?.Dispose();
            _cts = null;
        }
    }

    private void CancelBuild()
    {
        _cts?.Cancel();
    }

    private void OpenApkDirectory()
    {
        if (!string.IsNullOrEmpty(_lastBuiltApk) && File.Exists(_lastBuiltApk))
        {
            Process.Start("explorer.exe", $"/select,\"{_lastBuiltApk}\"");
        }
        else
        {
            string apkDir = Path.Combine(_workspaceRoot, "android", "app", "build", "outputs", "apk");
            if (Directory.Exists(apkDir))
                Process.Start("explorer.exe", $"\"{apkDir}\"");
        }
    }
}
