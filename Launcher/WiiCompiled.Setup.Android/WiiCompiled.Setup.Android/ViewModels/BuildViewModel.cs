using System;
using System.Collections.ObjectModel;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Platform.Storage;
using Avalonia.Threading;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using WiiCompiled.Android.Builder;
namespace WiiCompiled.Setup.Android.ViewModels;
public partial class BuildViewModel : ObservableObject {
    private readonly string _workspaceRoot;
    private ToolchainStatus? _toolchain;
    private CancellationTokenSource? _buildCts;
    [ObservableProperty] private string _toolStatusText = "Checking tools...";
    [ObservableProperty] private string _toolStatusColor = "#00b074";
    [ObservableProperty] private string _toolchainDetails = "";
    [ObservableProperty] private string _dataStatusText = "Checking extracted assets...";
    [ObservableProperty] private string _dataStatusColor = "#8f99ab";
    [ObservableProperty] private string _discImagePath = "";
    [ObservableProperty] private string _outputFolderPath = "";
    [ObservableProperty] private string _selectedProduct = "Mario Kart Wii";
    [ObservableProperty] private string _buildType = "Debug";
    [ObservableProperty] private bool _fastBuild = true;
    [ObservableProperty] private bool _forceReTranslate = false;
    [ObservableProperty] private bool _autoInstall = false;

    [ObservableProperty] private bool _isBuilding = false;
    [ObservableProperty] private string _buildButtonText = "Build";
    [ObservableProperty] private int _progressPercentage = 0;
    [ObservableProperty] private string _progressStatus = "Ready";
    [ObservableProperty] private string _logOutput = "";
    public ObservableCollection<string> Products { get; } = new() { "Mario Kart Wii" };
    public ObservableCollection<string> BuildTypes { get; } = new() { "Debug", "Release" };
    public BuildViewModel() {
        string baseDir = AppDomain.CurrentDomain.BaseDirectory;
        var dir = new DirectoryInfo(baseDir);
        while (dir != null && !File.Exists(Path.Combine(dir.FullName, "android-bootstrap.bat"))) dir = dir.Parent;
        _workspaceRoot = dir?.FullName ?? Path.GetFullPath(Path.Combine(baseDir, "..", "..", "..", "..", ".."));
        UpdateOutputPath();
        Log("Workspace root: " + _workspaceRoot);
        CheckTools();
        CheckExistingData();
    }
    partial void OnBuildTypeChanged(string value) => UpdateOutputPath();
    private void UpdateOutputPath() {
        string flavor = BuildType.ToLowerInvariant();
        OutputFolderPath = Path.Combine(_workspaceRoot, "android", "app", "build", "outputs", "apk", flavor);
    }
    private void Log(string msg) {
        string ts = DateTime.Now.ToString("HH:mm:ss");
        LogOutput += $"[{ts}] {msg}\n";
    }
    [RelayCommand] public void ClearLogs() => LogOutput = "";
    [RelayCommand]
    public void CheckTools() {
        try {
            _toolchain = ToolchainDetector.Detect(_workspaceRoot);
            if (_toolchain.HasRequiredTools) {
                ToolStatusText = "All build tools found"; ToolStatusColor = "#00b074";
            } else if (!string.IsNullOrEmpty(_toolchain.SdkPath)) {
                ToolStatusText = "SDK found, partial toolchain"; ToolStatusColor = "#f59e0b";
            } else {
                ToolStatusText = "Android SDK not configured"; ToolStatusColor = "#ef4444";
            }
            string ndkName = !string.IsNullOrEmpty(_toolchain.NdkPath) ? Path.GetFileName(_toolchain.NdkPath.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar)) : "Not found";
            string cmakeName = !string.IsNullOrEmpty(_toolchain.CMakePath) ? "CMake ready" : "CMake missing";
            string ninjaName = !string.IsNullOrEmpty(_toolchain.NinjaPath) ? "Ninja ready" : "Ninja missing";
            string dotNetName = _toolchain.DotNet8Available ? ".NET 8 OK" : ".NET 8 missing";
            ToolchainDetails = $"NDK: {ndkName}  •  {cmakeName}  •  {ninjaName}  •  {dotNetName}";
            Log($"Toolchain check: {ToolchainDetails}");
        } catch (Exception ex) {
            ToolStatusText = "Detection error"; ToolStatusColor = "#ef4444";
            ToolchainDetails = ex.Message; Log("Error detecting tools: " + ex.Message);
        }
        CheckExistingData();
    }
    public void CheckExistingData() {
        string assets = Path.Combine(_workspaceRoot, "Assets");
        bool hasDol = File.Exists(Path.Combine(assets, "main.dol"));
        bool hasRel = File.Exists(Path.Combine(assets, "StaticR.rel"));
        bool hasFiles = Directory.Exists(Path.Combine(assets, "files"));
        if (hasDol && hasRel && hasFiles) {
            DataStatusText = "Disc assets already extracted (main.dol, StaticR.rel, files/ ready)";
            DataStatusColor = "#00d68f";
        } else if (hasDol || hasRel) {
            DataStatusText = "Partial assets found in Assets/ folder. Extraction recommended.";
            DataStatusColor = "#f59e0b";
        } else {
            DataStatusText = "No previous disc assets extracted. Select a clean RMCP01 disc below.";
            DataStatusColor = "#8f99ab";
        }
    }
    [RelayCommand]
    private async Task BrowseDiscAsync() {
        var w = GetWindow(); if (w?.StorageProvider == null) return;
        var f = await w.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions { Title = "Select Disc Image", AllowMultiple = false });
        if (f.Count > 0) { DiscImagePath = f[0].Path.LocalPath; Log("Selected disc: " + DiscImagePath); }
    }
    [RelayCommand]
    private async Task BrowseOutputAsync() {
        var w = GetWindow(); if (w?.StorageProvider == null) return;
        var f = await w.StorageProvider.OpenFolderPickerAsync(new FolderPickerOpenOptions { Title = "Select Output Folder", AllowMultiple = false });
        if (f.Count > 0) { OutputFolderPath = f[0].Path.LocalPath; Log("Output folder: " + OutputFolderPath); }
    }
        [RelayCommand]
    public void CancelBuild() {
        if (IsBuilding && _buildCts != null && !_buildCts.IsCancellationRequested) {
            Log("Stopping build and terminating compilation processes...");
            ProgressStatus = "Cancelling build...";
            _buildCts.Cancel();
        }
    }

    [RelayCommand]
    private async Task StartBuildAsync() {
        if (IsBuilding) return;
        if (_toolchain == null || !_toolchain.HasRequiredTools) {
            CheckTools(); if (!_toolchain!.HasRequiredTools) { ProgressStatus = "Missing tools."; Log("Cannot build without required tools."); return; }
        }
        IsBuilding = true; BuildButtonText = "Building..."; ProgressPercentage = 0; _buildCts = new CancellationTokenSource();
        bool isRel = BuildType.Equals("Release", StringComparison.OrdinalIgnoreCase);
        string? targetDev = (AutoInstall && _toolchain.ConnectedDevices.Count > 0) ? _toolchain.ConnectedDevices[0] : null;
        Log($"=== Starting Android Build ({BuildType}) ===");
        try {
            var opt = new BuildOptions(_workspaceRoot, string.IsNullOrWhiteSpace(DiscImagePath) ? null : DiscImagePath, isRel, FastBuild, ForceReTranslate, AutoInstall, targetDev);
            var svc = new BuildPipelineService(
                m => Dispatcher.UIThread.Post(() => Log(m)),
                p => Dispatcher.UIThread.Post(() => { ProgressPercentage = p.Percentage; ProgressStatus = $"[{p.Step}/{p.TotalSteps}] {p.Message}"; })
            );
            string apk = await svc.RunAsync(opt, _toolchain, _buildCts.Token);
            ProgressPercentage = 100; ProgressStatus = "Complete!"; Log("Build succeeded: " + apk);
            CheckExistingData();
        } catch (OperationCanceledException) { ProgressStatus = "Cancelled"; Log("Build cancelled."); }
        catch (Exception ex) { ProgressStatus = "Failed"; Log("Error: " + ex.Message); }
        finally { IsBuilding = false; BuildButtonText = "Build"; _buildCts?.Dispose(); _buildCts = null; }
    }
    private static Avalonia.Controls.Window? GetWindow() => (Application.Current?.ApplicationLifetime as IClassicDesktopStyleApplicationLifetime)?.MainWindow;
}
