using System;
using Avalonia.Controls;
using Avalonia.Controls.Templates;
using CommunityToolkit.Mvvm.ComponentModel;
using WiiCompiled.Setup.Android.ViewModels;
using WiiCompiled.Setup.Android.Views;

namespace WiiCompiled.Setup.Android;

public class ViewLocator : IDataTemplate
{
    public Control? Build(object? data)
    {
        if (data is null) return null;

        return data switch
        {
            BuildViewModel vm => new BuildView { DataContext = vm },
            SendViewModel vm => new SendView { DataContext = vm },
            _ => new TextBlock { Text = "Not Found: " + data.GetType().FullName }
        };
    }

    public bool Match(object? data)
    {
        return data is ObservableObject;
    }
}
