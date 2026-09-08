using System;
using System.Collections.ObjectModel;
using System.IO;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Platform.Storage;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using WiiCompiled.Android.Builder;
namespace WiiCompiled.Setup.Android.ViewModels;
public partial class SendViewModel : ObservableObject {
    private readonly string _workspaceRoot;
    private ToolchainStatus? _toolchain;
    [ObservableProperty] private string _statusText = "Looking for devices...";
    [ObservableProperty] private string _statusColor = "#34d399";
    [ObservableProperty] private string _selectedDevice = "";
    [ObservableProperty] private string _selectedProduct = "Mario Kart Wii";
    [ObservableProperty] private string _payloadFolderPath = "";
    [ObservableProperty] private bool _isSending = false;
    public ObservableCollection<string> ConnectedDevices { get; } = new();
    public ObservableCollection<string> Products { get; } = new() { "Mario Kart Wii" };
    public SendViewModel() {
        string baseDir = AppDomain.CurrentDomain.BaseDirectory;
        var dir = new DirectoryInfo(baseDir);
        while (dir != null && !File.Exists(Path.Combine(dir.FullName, "android-bootstrap.bat"))) dir = dir.Parent;
        _workspaceRoot = dir?.FullName ?? Path.GetFullPath(Path.Combine(baseDir, "..", "..", "..", "..", ".."));
        PayloadFolderPath = Path.Combine(_workspaceRoot, "Assets");
        RefreshDevices();
    }
    [RelayCommand]
    public void RefreshDevices() {
        ConnectedDevices.Clear();
        _toolchain = ToolchainDetector.Detect(_workspaceRoot);
        if (_toolchain.ConnectedDevices.Count > 0) {
            foreach (var d in _toolchain.ConnectedDevices) ConnectedDevices.Add(d);
            SelectedDevice = ConnectedDevices[0];
            StatusText = $"{ConnectedDevices.Count} device(s) ready over ADB"; StatusColor = "#34d399";
        } else { SelectedDevice = "No devices found"; StatusText = "No authorized device detected"; StatusColor = "#f87171"; }
    }
    [RelayCommand]
    private async Task BrowsePayloadAsync() {
        var w = (Application.Current?.ApplicationLifetime as IClassicDesktopStyleApplicationLifetime)?.MainWindow;
        if (w?.StorageProvider != null) {
            var f = await w.StorageProvider.OpenFolderPickerAsync(new FolderPickerOpenOptions { Title = "Select Payload Folder" });
            if (f.Count > 0) PayloadFolderPath = f[0].Path.LocalPath;
        }
    }
    [RelayCommand]
    private async Task SendAsync() {
        if (IsSending || string.IsNullOrEmpty(_toolchain?.AdbPath) || ConnectedDevices.Count == 0) return;
        IsSending = true; StatusText = "Transferring payload..."; StatusColor = "#38bdf8";
        await Task.Run(() => {
            try {
                string s = SelectedDevice.Split(' ', '\t')[0];
                var psi = new System.Diagnostics.ProcessStartInfo(_toolchain.AdbPath, $"-s {s} push \"{PayloadFolderPath}\" \"/sdcard/Android/data/com.wiicompiled.mkw/files\"") { UseShellExecute = false, CreateNoWindow = true };
                using var p = System.Diagnostics.Process.Start(psi); p?.WaitForExit();
                StatusText = p?.ExitCode == 0 ? "Payload transferred successfully!" : "Transfer failed.";
                StatusColor = p?.ExitCode == 0 ? "#34d399" : "#f87171";
            } catch (Exception ex) { StatusText = "Error: " + ex.Message; StatusColor = "#f87171"; } finally { IsSending = false; }
        });
    }
}
