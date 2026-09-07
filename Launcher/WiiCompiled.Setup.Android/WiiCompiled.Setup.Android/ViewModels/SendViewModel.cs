using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;

namespace WiiCompiled.Setup.Android.ViewModels;

public partial class SendViewModel : ObservableObject
{
    [ObservableProperty]
    private string _statusText = "Device authorized and ready";

    [ObservableProperty]
    private string _statusColor = "#34d399";

    [ObservableProperty]
    private string _selectedDevice = "Scanning...";

    [ObservableProperty]
    private string _selectedProduct = "Mario Kart Wii";

    [ObservableProperty]
    private string _payloadFolderPath = "";

    public ObservableCollection<string> ConnectedDevices { get; } = new();
    public ObservableCollection<string> Products { get; } = new() { "Mario Kart Wii" };

    public SendViewModel()
    {
        ConnectedDevices.Add("Honor 90 (REA-NX9 - Android 15)");
        SelectedDevice = ConnectedDevices[0];
    }

    [RelayCommand]
    private void RefreshDevices() { }

    [RelayCommand]
    private void BrowsePayload() { }

    [RelayCommand]
    private void Send() { }
}
