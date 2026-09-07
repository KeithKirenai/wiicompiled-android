using Avalonia.Controls;
using Avalonia.Markup.Xaml;

namespace WiiCompiled.Setup.Android.Views;

public partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();
    }

    private void InitializeComponent()
    {
        AvaloniaXamlLoader.Load(this);
    }
}
