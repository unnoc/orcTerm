package com.orcterm.util;

import android.content.Context;
import com.orcterm.R;
import java.util.Locale;

public class OsIconUtils {

    /**
     * Get the resource ID for the OS icon based on the OS name.
     * Uses dynamic resource lookup to avoid compilation errors if icons are missing.
     *
     * @param context Context to access resources
     * @param osName The name of the operating system (e.g., "Ubuntu 22.04")
     * @return The resource ID of the matching icon, or the default Linux icon if not found.
     */
    public static int getOsIcon(Context context, String osName) {
        if (osName == null) {
            return R.drawable.ic_os_linux;
        }
        
        String lowerOs = osName.toLowerCase(Locale.ROOT);
        String iconName = "ic_os_linux";

        if (lowerOs.contains("ubuntu")) iconName = "ic_os_ubuntu";
        else if (lowerOs.contains("debian")) iconName = "ic_os_debian";
        else if (lowerOs.contains("centos")) iconName = "ic_os_centos";
        else if (lowerOs.contains("fedora")) iconName = "ic_os_fedora";
        else if (lowerOs.contains("arch")) iconName = "ic_os_arch";
        else if (lowerOs.contains("mint")) iconName = "ic_os_mint";
        else if (lowerOs.contains("kali")) iconName = "ic_os_kali";
        else if (lowerOs.contains("alpine")) iconName = "ic_os_alpine";
        else if (lowerOs.contains("suse") || lowerOs.contains("opensuse")) iconName = "ic_os_opensuse";
        else if (lowerOs.contains("manjaro")) iconName = "ic_os_manjaro";
        else if (lowerOs.contains("raspberry") || lowerOs.contains("raspbian")) iconName = "ic_os_raspberry";
        else if (lowerOs.contains("redhat") || lowerOs.contains("rhel")) iconName = "ic_os_redhat";
        else if (lowerOs.contains("rocky")) iconName = "ic_os_rocky";
        else if (lowerOs.contains("alma")) iconName = "ic_os_alma";
        else if (lowerOs.contains("elementary")) iconName = "ic_os_elementary";
        else if (lowerOs.contains("gentoo")) iconName = "ic_os_gentoo";
        else if (lowerOs.contains("slackware")) iconName = "ic_os_slackware";
        else if (lowerOs.contains("deepin")) iconName = "ic_os_deepin";
        else if (lowerOs.contains("android")) iconName = "ic_os_android";
        else if (lowerOs.contains("freebsd")) iconName = "ic_os_freebsd";
        else if (lowerOs.contains("coreos")) iconName = "ic_os_coreos";
        else if (lowerOs.contains("oracle")) iconName = "ic_os_oracle";
        else if (lowerOs.contains("pop")) iconName = "ic_os_pop_os";
        else if (lowerOs.contains("zorin")) iconName = "ic_os_zorin";
        else if (lowerOs.contains("nix")) iconName = "ic_os_nixos";
        else if (lowerOs.contains("void")) iconName = "ic_os_void";
        else if (lowerOs.contains("docker")) iconName = "ic_os_docker";

        int resId = context.getResources().getIdentifier(iconName, "drawable", context.getPackageName());
        return resId != 0 ? resId : R.drawable.ic_os_linux;
    }
}
