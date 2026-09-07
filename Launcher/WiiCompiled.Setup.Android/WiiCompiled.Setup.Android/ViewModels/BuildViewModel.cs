using System;
using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;

namespace WiiCompiled.Setup.Android.ViewModels;

public partial class BuildViewModel : ObservableObject
{
    [ObservableProperty]
    private string _toolStatusText = "All build tools found";

    [ObservableProperty]
    private string _discImagePath = "";

    [ObservableProperty]
    private string _outputFolderPath = "";

    [ObservableProperty]
    private string _selectedProduct = "Mario Kart Wii";

    [ObservableProperty]
    private bool _cleanBuild = false;

    [ObservableProperty]
    private bool _sustainedPerf = true;

    public ObservableCollection<string> Products { get; } = new() { "Mario Kart Wii" };

    public BuildViewModel()
    {
        OutputFolderPath = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "wiicompiled-workspace", "android-out");
    }

    [RelayCommand]
    private void CheckTools() { }

    [RelayCommand]
    private void BrowseDisc() { }

    [RelayCommand]
    private void BrowseOutput() { }

    [RelayCommand]
    private void StartBuild() { }
}
