using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;

namespace WiiCompiled.Setup.Android.ViewModels;

public partial class NavItem : ObservableObject
{
    public string Title { get; set; } = "";
    public string PathData { get; set; } = "";
    
    [ObservableProperty]
    private string _accentColor = "#8f99ab";
    
    [ObservableProperty]
    private string _textColor = "#cfd7e5";
    
    public object ViewInstance { get; set; } = null!;
}

public partial class MainViewModel : ObservableObject
{
    [ObservableProperty]
    private NavItem _selectedNavItem = null!;

    [ObservableProperty]
    private object _currentView = null!;

        public ObservableCollection<NavItem> NavItems { get; } = new();
    public BuildViewModel BuildVm { get; }

    public MainViewModel()
    {
        BuildVm = new BuildViewModel(); var buildVm = BuildVm;
        var sendVm = new SendViewModel();

        // Wrench / Build Icon
        var buildItem = new NavItem 
        { 
            Title = "Build", 
            PathData = "M22.7 19l-9.1-9.1c.9-2.3.4-5-1.5-6.9-2-2-5-2.4-7.4-1.3L9 6 6 9 1.6 4.7C.4 7.1.9 10.1 2.9 12.1c1.9 1.9 4.6 2.4 6.9 1.5l9.1 9.1c.4.4 1 .4 1.4 0l2.3-2.3c.5-.4.5-1.1.1-1.4z", 
            AccentColor = "#00d68f", 
            TextColor = "#00d68f", 
            ViewInstance = buildVm 
        };

        // Mobile / Send to Device Icon
        var sendItem = new NavItem 
        { 
            Title = "Send to Device", 
            PathData = "M17 1.01L7 1c-1.1 0-2 .9-2 2v18c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V3c0-1.1-.9-1.99-2-1.99zM17 19H7V5h10v14z", 
            AccentColor = "#8f99ab", 
            TextColor = "#cfd7e5", 
            ViewInstance = sendVm 
        };

        NavItems.Add(buildItem);
        NavItems.Add(sendItem);

        SelectedNavItem = buildItem;
        CurrentView = buildVm;
    }

    partial void OnSelectedNavItemChanged(NavItem value)
    {
        if (value != null)
        {
            CurrentView = value.ViewInstance;
            foreach (var item in NavItems)
            {
                bool isSelected = item == value;
                item.TextColor = isSelected ? "#00d68f" : "#cfd7e5";
                item.AccentColor = isSelected ? "#00d68f" : "#8f99ab";
            }
        }
    }
}
