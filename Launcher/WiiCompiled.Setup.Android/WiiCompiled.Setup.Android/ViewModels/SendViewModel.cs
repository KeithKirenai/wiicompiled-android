using System;
using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using WiiCompiled.Android.Builder;

namespace WiiCompiled.Setup.Android.ViewModels;

public sealed record ApkOption(string Name, string Path, string Details);

public partial class SendViewModel : ObservableObject
{
    private readonly string _workspaceRoot;
    private ToolchainStatus? _toolchain;

    [ObservableProperty] private string _statusText = "Checking environment...";
    [ObservableProperty] private string _statusColor = "#8f99ab";
    [ObservableProperty] private string _selectedDevice = "";
    [ObservableProperty] private bool _isInstalling = false;
    [ObservableProperty] private ApkOption? _selectedApk;

    public ObservableCollection<string> ConnectedDevices { get; } = new();
    public ObservableCollection<ApkOption> AvailableApks { get; } = new();

    public SendViewModel()
    {
        string baseDir = AppDomain.CurrentDomain.BaseDirectory;
        var dir = new DirectoryInfo(baseDir);
        while (dir != null && !File.Exists(Path.Combine(dir.FullName, "android-bootstrap.bat"))) dir = dir.Parent;
        _workspaceRoot = dir?.FullName ?? Path.GetFullPath(Path.Combine(baseDir, "..", "..", "..", "..", ".."));

        RefreshEnvironment();
    }

    [RelayCommand]
    public void RefreshEnvironment()
    {
        ConnectedDevices.Clear();
        _toolchain = ToolchainDetector.Detect(_workspaceRoot);

        if (_toolchain.ConnectedDevices.Count > 0)
        {
            foreach (var d in _toolchain.ConnectedDevices) ConnectedDevices.Add(d);
            SelectedDevice = ConnectedDevices[0];
            StatusText = $"{ConnectedDevices.Count} ADB device(s) ready.";
            StatusColor = "#34d399";
        }
        else
        {
            SelectedDevice = "No devices detected";
            StatusText = "No device found. Connect via USB with USB Debugging enabled.";
            StatusColor = "#f87171";
        }

        AvailableApks.Clear();
        string releaseApk = Path.Combine(_workspaceRoot, "android", "app", "build", "outputs", "apk", "release", "app-release.apk");
        string debugApk = Path.Combine(_workspaceRoot, "android", "app", "build", "outputs", "apk", "debug", "app-debug.apk");

        if (File.Exists(releaseApk))
        {
            var fi = new FileInfo(releaseApk);
            AvailableApks.Add(new ApkOption("Release APK (Recommended)", releaseApk, $"{(fi.Length / (1024.0 * 1024.0)):F1} MB • Optimized"));
        }
        if (File.Exists(debugApk))
        {
            var fi = new FileInfo(debugApk);
            AvailableApks.Add(new ApkOption("Debug APK", debugApk, $"{(fi.Length / (1024.0 * 1024.0)):F1} MB • Unstripped"));
        }

        if (AvailableApks.Count > 0)
        {
            SelectedApk = AvailableApks[0];
        }
    }

    [RelayCommand]
    private async Task InstallToDeviceAsync()
    {
        if (IsInstalling || SelectedApk == null || string.IsNullOrEmpty(_toolchain?.AdbPath) || ConnectedDevices.Count == 0 || SelectedDevice == "No devices detected")
            return;

        IsInstalling = true;
        StatusText = $"Installing {SelectedApk.Name}...";
        StatusColor = "#38bdf8";

        await Task.Run(() =>
        {
            try
            {
                string deviceSerial = SelectedDevice.Split(' ', '\t')[0];

                var installPsi = new ProcessStartInfo(_toolchain.AdbPath, $"-s {deviceSerial} install -r \"{SelectedApk.Path}\"")
                {
                    UseShellExecute = false,
                    CreateNoWindow = true,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true
                };

                using var installProc = Process.Start(installPsi);
                string output = installProc?.StandardOutput.ReadToEnd() ?? "";
                string err = installProc?.StandardError.ReadToEnd() ?? "";
                installProc?.WaitForExit();

                if (installProc?.ExitCode != 0 || (!output.Contains("Success") && !err.Contains("Success")))
                {
                    StatusText = $"Install failed: {(string.IsNullOrWhiteSpace(err) ? output.Trim() : err.Trim())}";
                    StatusColor = "#f87171";
                    return;
                }

                StatusText = "Success! APK installed cleanly on device.";
                StatusColor = "#34d399";
            }
            catch (Exception ex)
            {
                StatusText = "Error: " + ex.Message;
                StatusColor = "#f87171";
            }
            finally
            {
                IsInstalling = false;
            }
        });
    }
}