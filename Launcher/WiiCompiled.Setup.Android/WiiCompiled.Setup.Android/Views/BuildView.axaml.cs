using Avalonia.Controls;
using Avalonia.Markup.Xaml;

namespace WiiCompiled.Setup.Android.Views;

public partial class BuildView : UserControl
{
    public BuildView()
    {
        InitializeComponent();
    }

    private void InitializeComponent()
    {
        AvaloniaXamlLoader.Load(this);
    }
}
