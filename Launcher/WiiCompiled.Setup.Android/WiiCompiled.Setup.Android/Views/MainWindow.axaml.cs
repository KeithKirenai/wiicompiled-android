using Avalonia.Controls;
using Avalonia.Markup.Xaml;

namespace WiiCompiled.Setup.Android.Views;

public partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();
        Closing += (s, e) => {
            try {
                if (DataContext is ViewModels.MainViewModel mvm) {
                    mvm.BuildVm?.CancelBuild();
                } else if (DataContext is ViewModels.BuildViewModel directBvm) {
                    directBvm.CancelBuild();
                }
            } catch { }
        };
    }

    private void InitializeComponent()
    {
        AvaloniaXamlLoader.Load(this);
    }
}