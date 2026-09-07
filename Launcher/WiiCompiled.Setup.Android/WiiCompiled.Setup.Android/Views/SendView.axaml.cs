using Avalonia.Controls;
using Avalonia.Markup.Xaml;

namespace WiiCompiled.Setup.Android.Views;

public partial class SendView : UserControl
{
    public SendView()
    {
        InitializeComponent();
    }

    private void InitializeComponent()
    {
        AvaloniaXamlLoader.Load(this);
    }
}
